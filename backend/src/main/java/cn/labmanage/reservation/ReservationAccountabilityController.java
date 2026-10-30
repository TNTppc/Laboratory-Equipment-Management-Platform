package cn.labmanage.reservation;

import cn.labmanage.common.ApiResult;
import cn.labmanage.common.BusinessException;
import cn.labmanage.security.AccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationAccountabilityController {
    private final JdbcTemplate db;
    private final AccessService access;
    public ReservationAccountabilityController(JdbcTemplate db, AccessService access) { this.db = db; this.access = access; }

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
        String currentStage = String.valueOf(row.get("fulfillment_status"));
        if (!"NOT_STARTED".equals(currentStage) && !currentStage.equals(stage)) {
            throw new BusinessException("该预约已经登记过履约结果，不能重复修改");
        }
        if (currentStage.equals(stage)) return ApiResult.ok(Map.of("stage", stage, "unchanged", true));
        db.update("UPDATE reservation SET fulfillment_status=?,status=IF(status='APPROVED','COMPLETED',status) WHERE id=?", stage, id);
        if (!"RETURNED".equals(stage)) {
            double delta = "OVERDUE_NOT_RETURNED".equals(stage) ? -2.0 : -1.0;
            String reason = "OVERDUE_NOT_RETURNED".equals(stage) ? "逾期未归还" : "未履约";
            int inserted = db.update("INSERT IGNORE INTO credit_ledger(user_id,reservation_id,delta,reason,idempotency_key) VALUES(?,?,?,?,?)", row.get("user_id"), id, delta, reason, "reservation:" + id + ":" + stage);
            if (inserted == 1) {
                db.update("UPDATE lab_user SET credit_score=GREATEST(0.0,credit_score+?) WHERE id=?", delta, row.get("user_id"));
            }
        }
        Long operator = db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, auth.getName());
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'RESERVATION_FULFILLMENT','reservation',?,JSON_OBJECT('stage',?))", operator, id, stage);
        db.update("INSERT INTO notification(user_id,title,content) SELECT r.user_id,'预约履约结果',CONCAT('设备【',e.code,' ',e.name,'】的预约履约结果已登记为：',CASE ? WHEN 'RETURNED' THEN '已归还' WHEN 'NOT_FULFILLED' THEN '未履约（信誉积分扣1分）' ELSE '逾期未归还（信誉积分扣2分）' END,'。',IF(?='RETURNED','','如有异议，可在预约管理中提交申诉。')) FROM reservation r JOIN equipment e ON e.id=r.equipment_id WHERE r.id=?", stage, stage, id);
        return ApiResult.ok(Map.of("stage", stage));
    }

    @PostMapping("/{id}/appeal")
    @Transactional
    public ApiResult<?> appeal(@PathVariable long id, @RequestBody Map<String,String> body, Authentication auth) {
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) throw new BusinessException("请填写申诉理由");
        Long uid = db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, auth.getName());
        Integer count = db.queryForObject("SELECT COUNT(*) FROM reservation WHERE id=? AND user_id=? AND fulfillment_status IN ('OVERDUE_NOT_RETURNED','NOT_FULFILLED')", Integer.class, id, uid);
        if (count == null || count == 0) throw new BusinessException("当前预约不符合申诉条件");
        int inserted = db.update("INSERT IGNORE INTO reservation_appeal(reservation_id,applicant_id,reason) VALUES(?,?,?)", id, uid, reason);
        if (inserted == 0) throw new BusinessException("该预约已经提交过申诉，不能重复提交");
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'RESERVATION_APPEAL','reservation',?,JSON_OBJECT('reason',?))", uid, id, reason);
        return ApiResult.ok(Map.of("submitted", true));
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
