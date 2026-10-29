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
import java.util.Map;
import cn.labmanage.security.AccessService;

@RestController @RequestMapping("/api/reservations")
public class ReservationController {
    private static final ZoneId LABORATORY_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db; private final AccessService access;
    public ReservationController(JdbcTemplate db, AccessService access) { this.db=db; this.access=access; }
    public record Booking(@NotNull Long equipmentId,@NotNull LocalDateTime startsAt,@NotNull LocalDateTime endsAt,@NotBlank String purpose,@Min(1) int participants) {}
    private Long userId(String name) { return db.queryForObject("SELECT id FROM lab_user WHERE username=?",Long.class,name); }
    private void validateAndCheck(Booking b, Long excludeReservationId) {
        var eq=db.queryForList("SELECT e.status,l.open_time,l.close_time FROM equipment e JOIN laboratory l ON l.id=e.lab_id WHERE e.id=?",b.equipmentId());
        if(eq.isEmpty()) throw new BusinessException("设备不存在"); var item=eq.get(0);
        LocalTime open=((java.sql.Time)item.get("open_time")).toLocalTime(), close=((java.sql.Time)item.get("close_time")).toLocalTime();
        try { ReservationRules.validateUtc(b.startsAt(),b.endsAt(),LocalDateTime.now(ZoneOffset.UTC),open,close,(String)item.get("status"),LABORATORY_ZONE); }
        catch (IllegalArgumentException e) { throw new BusinessException(e.getMessage()); }
        Integer count=db.queryForObject("SELECT COUNT(*) FROM reservation WHERE equipment_id=? AND status IN ('PENDING','APPROVED') AND starts_at < ? AND ends_at > ? AND (? IS NULL OR id<>?)",Integer.class,b.equipmentId(),b.endsAt(),b.startsAt(),excludeReservationId,excludeReservationId);
        if(count!=null&&count>0) throw new BusinessException("该设备在所选时间段已被预约");
    }
    @PostMapping @Transactional public ApiResult<?> create(@Valid @RequestBody Booking b,Authentication auth) {
        db.queryForObject("SELECT id FROM equipment WHERE id=? FOR UPDATE",Long.class,b.equipmentId()); validateAndCheck(b,null);
        Long uid=userId(auth.getName()); db.update("INSERT INTO reservation(user_id,equipment_id,starts_at,ends_at,purpose,participants) VALUES(?,?,?,?,?,?)",uid,b.equipmentId(),b.startsAt(),b.endsAt(),b.purpose(),b.participants());
        return ApiResult.ok(Map.of("id",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class)));
    }
    @GetMapping public ApiResult<?> list(Authentication auth) { return ApiResult.ok(db.queryForList("SELECT r.*,u.real_name user_name,e.name equipment_name FROM reservation r JOIN lab_user u ON u.id=r.user_id JOIN equipment e ON e.id=r.equipment_id WHERE r.user_id=? ORDER BY r.submitted_at DESC",userId(auth.getName()))); }
    @GetMapping("/pending") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") public ApiResult<?> pending(Authentication auth) { String scope=access.isSystemAdmin(auth)?"":" AND EXISTS(SELECT 1 FROM lab_admin_lab al JOIN lab_user au ON au.id=al.user_id WHERE al.lab_id=e.lab_id AND au.username=?)"; String sql="SELECT r.*,u.real_name user_name,e.name equipment_name,l.name laboratory FROM reservation r JOIN lab_user u ON u.id=r.user_id JOIN equipment e ON e.id=r.equipment_id JOIN laboratory l ON l.id=e.lab_id WHERE r.status='PENDING'"+scope+" ORDER BY r.submitted_at"; return ApiResult.ok(access.isSystemAdmin(auth)?db.queryForList(sql):db.queryForList(sql,auth.getName())); }
    @PostMapping("/{id}/review") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") @Transactional public ApiResult<?> review(@PathVariable long id,@RequestBody Map<String,String> body,Authentication auth) {
        var rows=db.queryForList("SELECT * FROM reservation WHERE id=? FOR UPDATE",id); if(rows.isEmpty()) throw new BusinessException("预约不存在"); var r=rows.get(0);
        access.requireEquipment(auth,((Number)r.get("equipment_id")).longValue());
        if(!"PENDING".equals(r.get("status"))) throw new BusinessException("只有待审批预约可以审核"); String decision=body.get("decision");
        if(!"APPROVED".equals(decision)&&!"REJECTED".equals(decision)) throw new BusinessException("审核结果不合法");
        if("APPROVED".equals(decision)) { Booking b=new Booking(((Number)r.get("equipment_id")).longValue(),((java.sql.Timestamp)r.get("starts_at")).toLocalDateTime(),((java.sql.Timestamp)r.get("ends_at")).toLocalDateTime(),(String)r.get("purpose"),((Number)r.get("participants")).intValue()); db.queryForObject("SELECT id FROM equipment WHERE id=? FOR UPDATE",Long.class,b.equipmentId()); validateAndCheck(b,id); }
        Long reviewer=userId(auth.getName()); String comment=body.getOrDefault("comment",""); db.update("UPDATE reservation SET status=?,reviewer_id=?,review_comment=? WHERE id=?",decision,reviewer,comment,id); db.update("INSERT INTO reservation_review(reservation_id,reviewer_id,decision,comment) VALUES(?,?,?,?)",id,reviewer,decision,comment);
        db.update("INSERT INTO notification(user_id,title,content) SELECT user_id,'预约审核结果',CONCAT('预约 #',id,' 已',?) FROM reservation WHERE id=?", "APPROVED".equals(decision)?"通过":"驳回",id); db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'RESERVATION_REVIEW','reservation',?,JSON_OBJECT('decision',?,'comment',?))",reviewer,id,decision,comment); return ApiResult.ok(Map.of("status",decision));
    }
    @PostMapping("/{id}/cancel") @Transactional public ApiResult<?> cancel(@PathVariable long id,Authentication auth) {
        Long uid=userId(auth.getName()); int n=db.update("UPDATE reservation SET status='CANCELLED' WHERE id=? AND user_id=? AND status IN ('PENDING','APPROVED') AND starts_at > UTC_TIMESTAMP()",id,uid);
        if(n==0) throw new BusinessException("预约不存在，或当前状态/时间不允许取消"); return ApiResult.ok(Map.of("cancelled",true));
    }
}
