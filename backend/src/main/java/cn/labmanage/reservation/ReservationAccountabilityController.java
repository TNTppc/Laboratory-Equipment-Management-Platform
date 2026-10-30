package cn.labmanage.reservation;

import cn.labmanage.common.ApiResult;
import cn.labmanage.common.BusinessException;
import cn.labmanage.security.AccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationAccountabilityController {
    private final JdbcTemplate db;
    private final AccessService access;
    public ReservationAccountabilityController(JdbcTemplate db, AccessService access) { this.db = db; this.access = access; }

    private double penalty(long userId, String stage) {
        return penalty(userId, stage, null);
    }

    private double penalty(long userId, String stage, Long excludedReservationId) {
        double base = "OVERDUE_NOT_RETURNED".equals(stage) ? 5.0 : 3.0;
        double cap = "OVERDUE_NOT_RETURNED".equals(stage) ? 10.0 : 5.0;
        Long prior = db.queryForObject("SELECT COUNT(DISTINCT cl.reservation_id) FROM credit_ledger cl JOIN reservation r ON r.id=cl.reservation_id WHERE cl.user_id=? AND cl.delta<0 AND r.fulfillment_status IN ('NOT_FULFILLED','OVERDUE_NOT_RETURNED') AND cl.created_at>COALESCE((SELECT MAX(ok.created_at) FROM credit_ledger ok JOIN reservation recovered ON recovered.id=ok.reservation_id AND recovered.fulfillment_status='RETURNED' WHERE ok.user_id=? AND ok.reason='正常履约恢复'), '1970-01-01') AND (? IS NULL OR cl.reservation_id<>?)", Long.class, userId, userId, excludedReservationId, excludedReservationId);
        return Math.min(cap, base + (prior == null ? 0 : prior));
    }

    @PostMapping("/{id}/fulfillment")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
    @Transactional
    public ApiResult<?> fulfillment(@PathVariable long id, @RequestBody Map<String,Object> body, Authentication auth) {
        String stage = String.valueOf(body.get("stage"));
        if (!java.util.Set.of("OVERDUE_NOT_RETURNED","NOT_FULFILLED","RETURNED").contains(stage)) throw new BusinessException("履约阶段不合法");
        var rows = db.queryForList("SELECT * FROM reservation WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException("预约不存在");
        var row = rows.get(0);
        access.requireEquipment(auth, ((Number) row.get("equipment_id")).longValue());
        if (!"APPROVED".equals(row.get("status")) && !"COMPLETED".equals(row.get("status"))) throw new BusinessException("只有已通过预约可以登记履约结果");
        Integer ended = db.queryForObject("SELECT COUNT(*) FROM reservation WHERE id=? AND ends_at<=UTC_TIMESTAMP()", Integer.class, id);
        if (ended == null || ended == 0) throw new BusinessException("预约结束后才能登记履约结果");
        Long operator = db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, auth.getName());
        String currentStage = String.valueOf(row.get("fulfillment_status"));
        if (!"NOT_STARTED".equals(currentStage)) throw new BusinessException("该预约已登记履约结果，请使用修正操作");
        String reason = String.valueOf(body.getOrDefault("reason", "")).trim();
        String imageUrl = String.valueOf(body.getOrDefault("imageUrl", "")).trim();
        if (!"RETURNED".equals(stage) && reason.isBlank()) throw new BusinessException("登记未履约或逾期时必须填写原因");
        if (!imageUrl.isBlank() && !imageUrl.matches("/uploads/[a-zA-Z0-9-]+\\.(jpg|png|webp|gif)")) throw new BusinessException("履约图片地址无效");
        db.update("UPDATE reservation SET fulfillment_status=?,status=IF(status='APPROVED','COMPLETED',status),fulfillment_reason=?,fulfillment_image_url=?,fulfillment_operator_id=?,fulfillment_penalty=0 WHERE id=?", stage, reason, imageUrl, operator, id);
        if (!"RETURNED".equals(stage)) {
            double points = penalty(((Number) row.get("user_id")).longValue(), stage);
            String ledgerReason = "OVERDUE_NOT_RETURNED".equals(stage) ? "逾期未归还" : "未履约";
            int inserted = db.update("INSERT IGNORE INTO credit_ledger(user_id,reservation_id,delta,reason,idempotency_key) VALUES(?,?,?,?,?)", row.get("user_id"), id, -points, ledgerReason, "reservation:" + id + ":" + stage);
            if (inserted == 1) {
                db.update("UPDATE lab_user SET credit_score=GREATEST(0.0,credit_score-?) WHERE id=?", points, row.get("user_id"));
                db.update("UPDATE reservation SET fulfillment_penalty=? WHERE id=?", points, id);
            }
        } else {
            int inserted = db.update("INSERT IGNORE INTO credit_ledger(user_id,reservation_id,delta,reason,idempotency_key) VALUES(?,?,0.5,'正常履约恢复',?)", row.get("user_id"), id, "fulfillment:" + id + ":recovery");
            if (inserted == 1) db.update("UPDATE lab_user SET credit_score=LEAST(100.0,credit_score+0.5) WHERE id=?", row.get("user_id"));
        }
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'RESERVATION_FULFILLMENT','reservation',?,JSON_OBJECT('stage',?,'reason',?,'imageUrl',?))", operator, id, stage, reason, imageUrl);
        db.update("INSERT INTO notification(user_id,title,content) SELECT r.user_id,'预约履约结果',CONCAT('设备【',e.code,' ',e.name,'】的预约履约结果已登记为：',CASE ? WHEN 'RETURNED' THEN '已归还（信誉积分恢复0.5分）' WHEN 'NOT_FULFILLED' THEN '未履约（已扣信誉积分）' ELSE '逾期未归还（已扣信誉积分）' END,'。',IF(?='RETURNED','','如有异议，可在预约管理中提交申诉。')) FROM reservation r JOIN equipment e ON e.id=r.equipment_id WHERE r.id=?", stage, stage, id);
        return ApiResult.ok(Map.of("stage", stage));
    }

    @PutMapping("/{id}/fulfillment")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
    @Transactional
    public ApiResult<?> correctFulfillment(@PathVariable long id, @RequestBody Map<String,Object> body, Authentication auth) {
        var rows = db.queryForList("SELECT * FROM reservation WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException("预约不存在");
        var row = rows.get(0);
        access.requireEquipment(auth, ((Number) row.get("equipment_id")).longValue());
        Long operator = db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, auth.getName());
        if (row.get("fulfillment_operator_id") == null || !operator.equals(((Number) row.get("fulfillment_operator_id")).longValue())) throw new BusinessException("只能撤销或修正自己登记的履约结果");
        String stage = String.valueOf(body.get("stage"));
        if (!Set.of("OVERDUE_NOT_RETURNED", "NOT_FULFILLED", "RETURNED", "NOT_STARTED").contains(stage)) throw new BusinessException("履约阶段不合法");
        String oldStage = String.valueOf(row.get("fulfillment_status"));
        if (oldStage.equals(stage)) throw new BusinessException("履约阶段没有变化");
        String reason = String.valueOf(body.getOrDefault("reason", "")).trim();
        String imageUrl = String.valueOf(body.getOrDefault("imageUrl", "")).trim();
        if (!Set.of("RETURNED", "NOT_STARTED").contains(stage) && reason.isBlank()) throw new BusinessException("未履约或逾期必须填写原因");
        if (!imageUrl.isBlank() && !imageUrl.matches("/uploads/[a-zA-Z0-9-]+\\.(jpg|png|webp|gif)")) throw new BusinessException("履约图片地址无效");
        double newPenalty = "NOT_STARTED".equals(stage) || "RETURNED".equals(stage) ? 0 : penalty(((Number) row.get("user_id")).longValue(), stage, id);
        double oldPenalty = number(row.get("fulfillment_penalty"));
        if ("RETURNED".equals(oldStage)) addCorrectionLedger(row, id, -0.5, "\u6b63\u5e38\u5c65\u7ea6\u6062\u590d\u64a4\u9500");
        else if ("NOT_FULFILLED".equals(oldStage) || "OVERDUE_NOT_RETURNED".equals(oldStage)) addCorrectionLedger(row, id, oldPenalty, "\u5c65\u7ea6\u8fdd\u7ea6\u64a4\u9500\u8fd4\u8fd8");
        if ("RETURNED".equals(stage)) addCorrectionLedger(row, id, 0.5, "\u6b63\u5e38\u5c65\u7ea6\u6062\u590d");
        else if ("NOT_FULFILLED".equals(stage) || "OVERDUE_NOT_RETURNED".equals(stage)) {
            String ledgerReason = "OVERDUE_NOT_RETURNED".equals(stage) ? "逾期未归还" : "未履约";
            addCorrectionLedger(row, id, -newPenalty, ledgerReason);
        }
        db.update("UPDATE reservation SET fulfillment_status=?,fulfillment_reason=?,fulfillment_image_url=?,fulfillment_operator_id=?,fulfillment_penalty=? WHERE id=?", stage, "NOT_STARTED".equals(stage) ? null : reason, "NOT_STARTED".equals(stage) ? null : imageUrl, "NOT_STARTED".equals(stage) ? null : operator, newPenalty, id);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'RESERVATION_FULFILLMENT_CORRECTION','reservation',?,JSON_OBJECT('from',?,'to',?,'reason',?,'imageUrl',?))", operator, id, oldStage, stage, reason, imageUrl);
        return ApiResult.ok(Map.of("stage", stage));
    }

    private double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private void addCorrectionLedger(Map<String, Object> reservation, long reservationId, double delta, String reason) {
        db.update("INSERT INTO credit_ledger(user_id,reservation_id,delta,reason,idempotency_key) VALUES(?,?,?,?,?)", reservation.get("user_id"), reservationId, delta, reason, "correction:" + reservationId + ":" + java.util.UUID.randomUUID());
        db.update("UPDATE lab_user SET credit_score=LEAST(100.0,GREATEST(0.0,credit_score+?)) WHERE id=?", delta, reservation.get("user_id"));
    }

    @PostMapping("/{id}/appeal")
    @Transactional
    public ApiResult<?> appeal(@PathVariable long id, @RequestBody Map<String,String> body, Authentication auth) {
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) throw new BusinessException("请填写申诉理由");
        Long uid = db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, auth.getName());
        Integer count = db.queryForObject("SELECT COUNT(*) FROM reservation WHERE id=? AND user_id=? AND fulfillment_status IN ('OVERDUE_NOT_RETURNED','NOT_FULFILLED')", Integer.class, id, uid);
        if (count == null || count == 0) throw new BusinessException("当前预约不符合申诉条件");
        Integer withinDeadline = db.queryForObject("SELECT COUNT(*) FROM reservation WHERE id=? AND ends_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY)", Integer.class, id);
        if (withinDeadline == null || withinDeadline == 0) throw new BusinessException("预约结束超过7天，已无法申诉");
        int inserted = db.update("INSERT IGNORE INTO reservation_appeal(reservation_id,applicant_id,reason) VALUES(?,?,?)", id, uid, reason);
        if (inserted == 0) throw new BusinessException("该预约已经提交过申诉，不能重复提交");
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'RESERVATION_APPEAL','reservation',?,JSON_OBJECT('reason',?))", uid, id, reason);
        return ApiResult.ok(Map.of("submitted", true));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN','READ_ONLY_ADMIN')")
    public ApiResult<?> audit(Authentication auth) {
        String scope = access.isGlobalViewer(auth) ? "" : " AND EXISTS(SELECT 1 FROM lab_admin_lab al WHERE al.lab_id=e.lab_id AND al.user_id=(SELECT id FROM lab_user WHERE username=?))";
        String sql = "SELECT o.*,u.real_name operator_name,e.name equipment_name FROM operation_log o LEFT JOIN lab_user u ON u.id=o.operator_id LEFT JOIN reservation r ON o.target_type='reservation' AND r.id=o.target_id LEFT JOIN reservation_appeal a ON o.target_type='reservation_appeal' AND a.id=o.target_id LEFT JOIN equipment direct_e ON o.target_type='equipment' AND direct_e.id=o.target_id LEFT JOIN repair_ticket t ON o.target_type='repair_ticket' AND t.id=o.target_id LEFT JOIN equipment repair_e ON repair_e.id=t.equipment_id LEFT JOIN equipment e ON e.id=COALESCE(r.equipment_id,(SELECT equipment_id FROM reservation WHERE id=a.reservation_id),direct_e.id,repair_e.id) WHERE 1=1" + scope + " ORDER BY o.created_at DESC LIMIT 500";
        return ApiResult.ok(access.isGlobalViewer(auth) ? db.queryForList(sql) : db.queryForList(sql, auth.getName()));
    }

    @GetMapping("/appeals")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN','READ_ONLY_ADMIN')")
    public ApiResult<?> appeals(Authentication auth) {
        String suffix = access.isGlobalViewer(auth) ? "" : " AND EXISTS(SELECT 1 FROM reservation r JOIN equipment e ON e.id=r.equipment_id JOIN lab_admin_lab al ON al.lab_id=e.lab_id JOIN lab_user au ON au.id=al.user_id WHERE r.id=a.reservation_id AND au.username=?)";
        String sql = "SELECT a.*,u.real_name applicant_name,r.user_id,r.fulfillment_status,e.name equipment_name FROM reservation_appeal a JOIN lab_user u ON u.id=a.applicant_id JOIN reservation r ON r.id=a.reservation_id JOIN equipment e ON e.id=r.equipment_id WHERE a.status='PENDING'" + suffix + " ORDER BY a.created_at";
        return ApiResult.ok(access.isGlobalViewer(auth) ? db.queryForList(sql) : db.queryForList(sql, auth.getName()));
    }

    @PostMapping("/appeals/{id}/review")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
    @Transactional
    public ApiResult<?> reviewAppeal(@PathVariable long id, @RequestBody Map<String,String> body, Authentication auth) {
        String decision = body.get("decision");
        if (!java.util.Set.of("APPROVED","REJECTED").contains(decision)) throw new BusinessException("申诉结果不合法");
        var rows = db.queryForList("SELECT a.*,r.equipment_id FROM reservation_appeal a JOIN reservation r ON r.id=a.reservation_id WHERE a.id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException("申诉不存在");
        var appeal = rows.get(0);
        access.requireEquipment(auth, ((Number) appeal.get("equipment_id")).longValue());
        if (!"PENDING".equals(appeal.get("status"))) {
            return ApiResult.ok(Map.of("status", appeal.get("status"), "unchanged", true));
        }
        Long reviewer = db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, auth.getName());
        String comment = body.getOrDefault("comment", "");
        int changed = db.update("UPDATE reservation_appeal SET status=?,reviewer_id=?,review_comment=?,reviewed_at=UTC_TIMESTAMP() WHERE id=? AND status='PENDING'", decision, reviewer, comment, id);
        if (changed == 1 && "APPROVED".equals(decision)) {
            var reservation = db.queryForMap("SELECT a.applicant_id,a.reservation_id,r.fulfillment_status FROM reservation_appeal a JOIN reservation r ON r.id=a.reservation_id WHERE a.id=?", id);
            double delta = "OVERDUE_NOT_RETURNED".equals(reservation.get("fulfillment_status")) ? 2.0 : 1.0;
            int inserted = db.update("INSERT IGNORE INTO credit_ledger(user_id,reservation_id,delta,reason,idempotency_key) VALUES(?,?,?,?,?)", reservation.get("applicant_id"), reservation.get("reservation_id"), delta, "申诉通过返还", "appeal:" + id);
            if (inserted == 1) db.update("UPDATE lab_user SET credit_score=LEAST(100.0,credit_score+?) WHERE id=?", delta, reservation.get("applicant_id"));
        }
        db.update("INSERT INTO notification(user_id,title,content) SELECT a.applicant_id,'履约申诉审核结果',CONCAT('您对设备【',e.code,' ',e.name,'】预约的履约申诉已',CASE ? WHEN 'APPROVED' THEN '通过，相关信誉积分已返还' ELSE '驳回' END,IF(?<>'',CONCAT('。审核意见：',?),''),'。') FROM reservation_appeal a JOIN reservation r ON r.id=a.reservation_id JOIN equipment e ON e.id=r.equipment_id WHERE a.id=?", decision, comment, comment, id);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'RESERVATION_APPEAL_REVIEW','reservation_appeal',?,JSON_OBJECT('decision',?,'comment',?))", reviewer, id, decision, comment);
        return ApiResult.ok(Map.of("status", decision));
    }
}
