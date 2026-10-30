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
        String sql = "SELECT r.id,r.scope_type,r.scope_id,r.action,r.enabled_until,r.enabled_until>UTC_TIMESTAMP() active,u.real_name enabled_by,COALESCE(l.name,c.name,e.name,'全局') scope_name FROM auto_approval_rule r JOIN lab_user u ON u.id=r.enabled_by LEFT JOIN laboratory l ON r.scope_type='LAB' AND r.scope_id=l.id LEFT JOIN equipment_category c ON r.scope_type='CATEGORY' AND r.scope_id=c.id LEFT JOIN equipment e ON r.scope_type='EQUIPMENT' AND r.scope_id=e.id WHERE 1=1";
        if (!access.isSystemAdmin(auth)) sql += " AND (r.scope_type='GLOBAL' OR (r.scope_type='LAB' AND EXISTS(SELECT 1 FROM lab_admin_lab al JOIN lab_user au ON au.id=al.user_id WHERE al.lab_id=r.scope_id AND au.username=?)) OR (r.scope_type IN ('CATEGORY','EQUIPMENT') AND EXISTS(SELECT 1 FROM equipment eq JOIN lab_admin_lab al ON al.lab_id=eq.lab_id JOIN lab_user au ON au.id=al.user_id WHERE (r.scope_type='EQUIPMENT' AND eq.id=r.scope_id OR r.scope_type='CATEGORY' AND eq.category_id=r.scope_id) AND au.username=?)))";
        return ApiResult.ok(access.isSystemAdmin(auth) ? db.queryForList(sql + " ORDER BY r.scope_type,r.id") : db.queryForList(sql + " ORDER BY r.scope_type,r.id", auth.getName(), auth.getName()));
    }

    @PostMapping
    @Transactional
    public ApiResult<?> update(@RequestBody Map<String, Object> body, Authentication auth) {
        Object scopeValue = body.get("scopeId"), durationValue = body.get("minutes");
        String scopeType = String.valueOf(body.getOrDefault("scopeType", "LAB"));
        String action = String.valueOf(body.getOrDefault("action", "APPROVE"));
        if (!(durationValue instanceof Number)) throw new BusinessException("请选择自动审批时长");
        int minutes = ((Number) durationValue).intValue();
        if (!DURATIONS.contains(minutes)) throw new BusinessException("自动审批时长不合法");
        if (!Set.of("GLOBAL","LAB","CATEGORY","EQUIPMENT").contains(scopeType) || !Set.of("APPROVE","REJECT").contains(action)) throw new BusinessException("自动审批规则不合法");
        Long scopeId = "GLOBAL".equals(scopeType) ? 0L : scopeValue instanceof Number ? ((Number) scopeValue).longValue() : null;
        if (!"GLOBAL".equals(scopeType) && scopeId == null) throw new BusinessException("请选择自动审批范围");
        if ("GLOBAL".equals(scopeType) && !access.isSystemAdmin(auth)) throw new BusinessException("只有系统管理员可以配置全局自动审批");
        if ("LAB".equals(scopeType)) access.requireLab(auth, scopeId);
        if ("EQUIPMENT".equals(scopeType)) access.requireEquipment(auth, scopeId);
        if ("CATEGORY".equals(scopeType) && !access.isGlobalViewer(auth)) {
            Integer allowed = db.queryForObject("SELECT COUNT(*) FROM equipment e WHERE e.category_id=? AND EXISTS(SELECT 1 FROM lab_admin_lab al WHERE al.lab_id=e.lab_id AND al.user_id=(SELECT id FROM lab_user WHERE username=?))", Integer.class, scopeId, auth.getName());
            if (allowed == null || allowed == 0) throw new BusinessException("无权操作该设备分类");
        }
        if (minutes == 0) {
            db.update("DELETE FROM auto_approval_rule WHERE scope_type=? AND scope_id=?", scopeType, scopeId);
            return ApiResult.ok(Map.of("active", false));
        }
        Long userId = db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, auth.getName());
        db.update("INSERT INTO auto_approval_rule(scope_type,scope_id,action,enabled_until,enabled_by) VALUES(?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? MINUTE),?) ON DUPLICATE KEY UPDATE action=VALUES(action),enabled_until=VALUES(enabled_until),enabled_by=VALUES(enabled_by),updated_at=UTC_TIMESTAMP()", scopeType, scopeId, action, minutes, userId);
        return ApiResult.ok(Map.of("active", true, "minutes", minutes));
    }
}
