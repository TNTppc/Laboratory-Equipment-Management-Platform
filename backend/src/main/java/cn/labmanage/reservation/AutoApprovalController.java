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
import java.util.Set;

@RestController
@RequestMapping("/api/reservations/auto-approval")
@PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
public class AutoApprovalController {
    private static final Set<Integer> DURATIONS = Set.of(0, 60, 240, 480);
    private final JdbcTemplate db;
    private final AccessService access;

    public AutoApprovalController(JdbcTemplate db, AccessService access) { this.db = db; this.access = access; }

    @GetMapping
    public ApiResult<?> list(Authentication auth) {
        String sql = "SELECT l.id id,l.name,l.location,a.enabled_until enabledUntil,(a.enabled_until>UTC_TIMESTAMP()) active,u.real_name enabledBy FROM laboratory l LEFT JOIN lab_auto_approval a ON a.lab_id=l.id LEFT JOIN lab_user u ON u.id=a.enabled_by WHERE l.enabled=1";
        if (access.isSystemAdmin(auth)) return ApiResult.ok(db.queryForList(sql + " ORDER BY l.name"));
        return ApiResult.ok(db.queryForList(sql + " AND EXISTS(SELECT 1 FROM lab_admin_lab al JOIN lab_user au ON au.id=al.user_id WHERE al.lab_id=l.id AND au.username=?) ORDER BY l.name", auth.getName()));
    }

    @PostMapping
    @Transactional
    public ApiResult<?> update(@RequestBody Map<String, Object> body, Authentication auth) {
        Object labValue = body.get("labId"), durationValue = body.get("minutes");
        if (!(labValue instanceof Number) || !(durationValue instanceof Number)) throw new BusinessException("请选择实验室和自动审批时长");
        long labId = ((Number) labValue).longValue();
        int minutes = ((Number) durationValue).intValue();
        if (!DURATIONS.contains(minutes)) throw new BusinessException("自动审批时长不合法");
        access.requireLab(auth, labId);
        if (minutes == 0) {
            db.update("DELETE FROM lab_auto_approval WHERE lab_id=?", labId);
            return ApiResult.ok(Map.of("active", false));
        }
        Long userId = db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, auth.getName());
        db.update("INSERT INTO lab_auto_approval(lab_id,enabled_until,enabled_by) VALUES(?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? MINUTE),?) ON DUPLICATE KEY UPDATE enabled_until=VALUES(enabled_until),enabled_by=VALUES(enabled_by),updated_at=UTC_TIMESTAMP()", labId, minutes, userId);
        return ApiResult.ok(Map.of("active", true, "minutes", minutes));
    }
}
