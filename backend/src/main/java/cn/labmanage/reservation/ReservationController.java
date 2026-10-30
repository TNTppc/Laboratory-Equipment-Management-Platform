package cn.labmanage.reservation;

import cn.labmanage.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.Map;
import cn.labmanage.security.AccessService;

@RestController @RequestMapping("/api/reservations")
public class ReservationController {
    private static final ZoneId LABORATORY_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db; private final AccessService access; private final ReservationWorkflowService workflow;
    public ReservationController(JdbcTemplate db, AccessService access, ReservationWorkflowService workflow) { this.db=db; this.access=access; this.workflow=workflow; }
    public record Booking(@NotNull Long equipmentId,@NotNull LocalDateTime startsAt,@NotNull LocalDateTime endsAt,@NotBlank String purpose,@Min(1) int participants) {}
    private Long userId(String name) { return db.queryForObject("SELECT id FROM lab_user WHERE username=?",Long.class,name); }
    private void refreshCredit(Long uid) {
        db.update("UPDATE lab_user SET credit_score=LEAST(100.0,credit_score+0.5*DATEDIFF(UTC_DATE(),COALESCE(credit_recovered_on,UTC_DATE()))),credit_recovered_on=UTC_DATE() WHERE id=?", uid);
    }
    private Map<String,Object> autoRule(long equipmentId) {
        var rules=db.queryForList("SELECT r.enabled_by,r.action,r.scope_type FROM auto_approval_rule r JOIN equipment e ON (r.scope_type='EQUIPMENT' AND r.scope_id=e.id) OR (r.scope_type='CATEGORY' AND r.scope_id=e.category_id) OR (r.scope_type='LAB' AND r.scope_id=e.lab_id) OR r.scope_type='GLOBAL' WHERE e.id=? AND r.enabled_until>UTC_TIMESTAMP() ORDER BY CASE r.scope_type WHEN 'EQUIPMENT' THEN 1 WHEN 'CATEGORY' THEN 2 WHEN 'LAB' THEN 3 WHEN 'GLOBAL' THEN 4 END,r.updated_at DESC LIMIT 1", equipmentId);
        return rules.isEmpty() ? Map.of() : rules.get(0);
    }
    private void validateAndCheck(Booking b, Long excludeReservationId, boolean enforceSubmissionWindow) {
        var eq=db.queryForList("SELECT e.status,l.open_time,l.close_time FROM equipment e JOIN laboratory l ON l.id=e.lab_id WHERE e.id=?",b.equipmentId());
        if(eq.isEmpty()) throw new BusinessException("设备不存在"); var item=eq.get(0);
        LocalTime open=((java.sql.Time)item.get("open_time")).toLocalTime(), close=((java.sql.Time)item.get("close_time")).toLocalTime();
        try { ReservationRules.validateUtc(b.startsAt(),b.endsAt(),LocalDateTime.now(ZoneOffset.UTC),open,close,(String)item.get("status"),LABORATORY_ZONE,enforceSubmissionWindow); }
        catch (IllegalArgumentException e) { throw new BusinessException(e.getMessage()); }
        Integer count=db.queryForObject("SELECT COUNT(*) FROM reservation WHERE equipment_id=? AND status IN ('PENDING','APPROVED') AND starts_at < ? AND ends_at > ? AND (? IS NULL OR id<>?)",Integer.class,b.equipmentId(),b.endsAt(),b.startsAt(),excludeReservationId,excludeReservationId);
        if(count!=null&&count>0) throw new BusinessException("该设备在所选时间段已被预约");
    }
    @PostMapping @Transactional public ApiResult<?> create(@Valid @RequestBody Booking b,Authentication auth) {
        db.queryForObject("SELECT id FROM equipment WHERE id=? FOR UPDATE",Long.class,b.equipmentId());
        Long uid=userId(auth.getName()); refreshCredit(uid);
        BigDecimal credit=db.queryForObject("SELECT credit_score FROM lab_user WHERE id=?",BigDecimal.class,uid);
        if (credit != null && credit.compareTo(BigDecimal.valueOf(60)) < 0) throw new BusinessException("信誉积分过低，请联系管理员");
        validateAndCheck(b,null,true);
        var rule=autoRule(b.equipmentId());
        boolean creditAuto=credit != null && credit.compareTo(BigDecimal.valueOf(90)) >= 0;
        boolean ruleActive=!rule.isEmpty();
        boolean autoApproved=creditAuto || (ruleActive && "APPROVE".equals(rule.get("action")));
        boolean autoRejected=ruleActive && "REJECT".equals(rule.get("action"));
        Long reviewer=ruleActive?((Number)rule.get("enabled_by")).longValue():null;
        String status=autoRejected?"REJECTED":autoApproved?"APPROVED":"PENDING";
        String reason=autoRejected?"AUTO_REJECTED":null;
        String comment=autoRejected?"命中自动审批拒绝规则":creditAuto?"信誉积分达到90分，系统自动通过":ruleActive?"命中自动审批通过规则":"";
        db.update("INSERT INTO reservation(user_id,equipment_id,starts_at,ends_at,purpose,participants,status,reject_reason,reviewer_id,review_comment) VALUES(?,?,?,?,?,?,?,?,?,?)",uid,b.equipmentId(),b.startsAt(),b.endsAt(),b.purpose(),b.participants(),status,reason,reviewer,comment);
        Long reservationId=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        if(autoApproved || autoRejected) {
            db.update("INSERT INTO reservation_review(reservation_id,reviewer_id,decision,comment) VALUES(?,?,?,?)",reservationId,reviewer,status,comment);
            if(autoRejected) db.update("INSERT INTO notification(user_id,title,content) SELECT r.user_id,'预约自动拒绝',CONCAT('设备【',e.code,' ',e.name,'】的预约命中了自动拒绝规则，系统已拒绝。请联系实验室管理员。') FROM reservation r JOIN equipment e ON e.id=r.equipment_id WHERE r.id=?",reservationId);
            else
            db.update("INSERT INTO notification(user_id,title,content) SELECT r.user_id,'预约已自动通过',CONCAT('设备【',e.code,' ',e.name,'】（',l.name,'）当前开启自动审批，您的预约已通过。使用时间：',DATE_FORMAT(CONVERT_TZ(r.starts_at,'+00:00','+08:00'),'%Y年%m月%d日 %H:%i'),' 至 ',DATE_FORMAT(CONVERT_TZ(r.ends_at,'+00:00','+08:00'),'%H:%i'),'。') FROM reservation r JOIN equipment e ON e.id=r.equipment_id JOIN laboratory l ON l.id=e.lab_id WHERE r.id=?",reservationId);
        } else db.update("INSERT INTO notification(user_id,title,content) SELECT DISTINCT ur.user_id,'新预约待审批',CONCAT('用户【',u.real_name,'】申请使用设备【',e.code,' ',e.name,'】（',l.name,'）。使用时间：',DATE_FORMAT(CONVERT_TZ(r.starts_at,'+00:00','+08:00'),'%Y年%m月%d日 %H:%i'),' 至 ',DATE_FORMAT(CONVERT_TZ(r.ends_at,'+00:00','+08:00'),'%H:%i'),'；用途：',r.purpose,'；参与人数：',r.participants,'人。') FROM reservation r JOIN lab_user u ON u.id=r.user_id JOIN equipment e ON e.id=r.equipment_id JOIN laboratory l ON l.id=e.lab_id JOIN user_role ur JOIN role role ON role.id=ur.role_id LEFT JOIN lab_admin_lab al ON al.user_id=ur.user_id AND al.lab_id=e.lab_id WHERE r.id=? AND (role.code='SYS_ADMIN' OR (role.code='LAB_ADMIN' AND al.lab_id IS NOT NULL))",reservationId);
        return ApiResult.ok(Map.of("id",reservationId,"status",status,"creditScore",credit));
    }
    @GetMapping public ApiResult<?> list(Authentication auth) { return ApiResult.ok(db.queryForList("SELECT r.*,u.real_name user_name,e.name equipment_name,a.status appeal_status FROM reservation r JOIN lab_user u ON u.id=r.user_id JOIN equipment e ON e.id=r.equipment_id LEFT JOIN reservation_appeal a ON a.reservation_id=r.id WHERE r.user_id=? ORDER BY r.submitted_at DESC",userId(auth.getName()))); }
    @GetMapping("/pending") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN','READ_ONLY_ADMIN')") public ApiResult<?> pending(Authentication auth) { workflow.expireOverdueReservations(); String scope=access.isGlobalViewer(auth)?"":" AND EXISTS(SELECT 1 FROM lab_admin_lab al JOIN lab_user au ON au.id=al.user_id WHERE al.lab_id=e.lab_id AND au.username=?)"; String sql="SELECT r.*,u.real_name user_name,u.email user_email,u.phone user_phone,ci.identity_no user_identity_no,e.name equipment_name,l.name laboratory FROM reservation r JOIN lab_user u ON u.id=r.user_id LEFT JOIN campus_identity ci ON ci.id=u.identity_id JOIN equipment e ON e.id=r.equipment_id JOIN laboratory l ON l.id=e.lab_id WHERE r.status IN ('PENDING','APPROVED','COMPLETED')"+scope+" ORDER BY CASE r.status WHEN 'PENDING' THEN 0 ELSE 1 END,r.starts_at"; return ApiResult.ok(access.isGlobalViewer(auth)?db.queryForList(sql):db.queryForList(sql,auth.getName())); }
    @PostMapping("/{id}/review") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") @Transactional public ApiResult<?> review(@PathVariable long id,@RequestBody Map<String,String> body,Authentication auth) {
        workflow.expireOverdueReservations();
        var rows=db.queryForList("SELECT * FROM reservation WHERE id=? FOR UPDATE",id); if(rows.isEmpty()) throw new BusinessException("预约不存在"); var r=rows.get(0);
        access.requireEquipment(auth,((Number)r.get("equipment_id")).longValue());
        if(!"PENDING".equals(r.get("status"))) throw new BusinessException("只有待审批预约可以审核"); String decision=body.get("decision");
        if(!"APPROVED".equals(decision)&&!"REJECTED".equals(decision)) throw new BusinessException("审核结果不合法");
        if("APPROVED".equals(decision)) { Booking b=new Booking(((Number)r.get("equipment_id")).longValue(),DateTimeValues.toLocalDateTime(r.get("starts_at")),DateTimeValues.toLocalDateTime(r.get("ends_at")),(String)r.get("purpose"),((Number)r.get("participants")).intValue()); db.queryForObject("SELECT id FROM equipment WHERE id=? FOR UPDATE",Long.class,b.equipmentId()); validateAndCheck(b,id,false); }
        Long reviewer=userId(auth.getName()); String comment=body.getOrDefault("comment",""); db.update("UPDATE reservation SET status=?,reject_reason=?,reviewer_id=?,review_comment=? WHERE id=?",decision,"REJECTED".equals(decision)?"ADMIN_REJECTED":null,reviewer,comment,id); db.update("INSERT INTO reservation_review(reservation_id,reviewer_id,decision,comment) VALUES(?,?,?,?)",id,reviewer,decision,comment);
        db.update("INSERT INTO notification(user_id,title,content) SELECT r.user_id,'预约审核结果',CONCAT('设备【',e.code,' ',e.name,'】（',l.name,'）的预约已',?,'。使用时间：',DATE_FORMAT(CONVERT_TZ(r.starts_at,'+00:00','+08:00'),'%Y年%m月%d日 %H:%i'),' 至 ',DATE_FORMAT(CONVERT_TZ(r.ends_at,'+00:00','+08:00'),'%H:%i'),'；用途：',r.purpose,IF(?<>'','；审核意见：',''),?) FROM reservation r JOIN equipment e ON e.id=r.equipment_id JOIN laboratory l ON l.id=e.lab_id WHERE r.id=?", "APPROVED".equals(decision)?"通过":"驳回",comment,comment,id); db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'RESERVATION_REVIEW','reservation',?,JSON_OBJECT('decision',?,'comment',?))",reviewer,id,decision,comment); return ApiResult.ok(Map.of("status",decision));
    }
    @PostMapping("/{id}/cancel") @Transactional public ApiResult<?> cancel(@PathVariable long id,Authentication auth) {
        Long uid=userId(auth.getName()); int n=db.update("UPDATE reservation SET status='CANCELLED' WHERE id=? AND user_id=? AND status IN ('PENDING','APPROVED') AND starts_at > UTC_TIMESTAMP()",id,uid);
        if(n==0) throw new BusinessException("预约不存在，或当前状态/时间不允许取消"); return ApiResult.ok(Map.of("cancelled",true));
    }
}
