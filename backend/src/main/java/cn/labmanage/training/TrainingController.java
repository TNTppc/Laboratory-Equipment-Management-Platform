package cn.labmanage.training;

import cn.labmanage.common.ApiResult;
import cn.labmanage.common.BusinessException;
import cn.labmanage.security.AccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class TrainingController {
    private static final Set<String> TYPES = Set.of("SAFETY_TRAINING", "OPERATION_EXAM");
    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "REVOKED");
    private static final ZoneId LABORATORY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private final JdbcTemplate db;
    private final AccessService access;
    private final TrainingAccessService trainingAccess;

    public TrainingController(JdbcTemplate db, AccessService access, TrainingAccessService trainingAccess) { this.db = db; this.access = access; this.trainingAccess = trainingAccess; }

    public record ItemInput(@NotBlank String code, @NotBlank String name, @NotBlank String type, Long labId, @Min(0) @Max(120) Integer validityMonths, String description, Boolean enabled) {}
    public record ApplyInput(String remark, String evidenceUrl) {}
    public record ReviewInput(String decision, String comment, String expiresAt) {}
    public record RevokeInput(String comment) {}
    public record RequirementInput(List<Long> itemIds) {}

    private Long userId(String name) { return db.queryForObject("SELECT id FROM lab_user WHERE username=?", Long.class, name); }

    private Map<String, Object> trainingItem(long id) {
        var rows = db.queryForList("SELECT * FROM training_item WHERE id=?", id);
        if (rows.isEmpty()) throw new BusinessException("培训项目不存在");
        return rows.get(0);
    }

    @GetMapping("/trainings")
    public ApiResult<?> catalog(Authentication auth) {
        Long uid = userId(auth.getName());
        String sql = "SELECT ti.id,ti.code,ti.name,ti.type,ti.lab_id labId,l.name laboratory,ti.validity_months validityMonths,ti.description,ti.enabled,"
            + "g.id grantId,g.status grantStatus,g.source grantSource,g.score,g.remark applyRemark,g.review_comment reviewComment,g.applied_at appliedAt,g.granted_at grantedAt,g.expires_at expiresAt,"
            + "CASE WHEN g.status='APPROVED' AND (g.expires_at IS NULL OR g.expires_at>UTC_TIMESTAMP()) THEN 1 ELSE 0 END effective "
            + "FROM training_item ti LEFT JOIN laboratory l ON l.id=ti.lab_id "
            + "LEFT JOIN training_grant g ON g.id=(SELECT g2.id FROM training_grant g2 WHERE g2.training_item_id=ti.id AND g2.user_id=? "
            + "ORDER BY (g2.status='APPROVED' AND (g2.expires_at IS NULL OR g2.expires_at>UTC_TIMESTAMP())) DESC,CASE WHEN g2.status='PENDING' THEN 0 ELSE 1 END,g2.id DESC LIMIT 1) "
            + "WHERE ti.enabled=1 ORDER BY ti.lab_id IS NULL DESC,ti.id";
        return ApiResult.ok(db.queryForList(sql, uid));
    }

    @GetMapping("/trainings/items")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN','READ_ONLY_ADMIN')")
    public ApiResult<?> items(Authentication auth) {
        String sql = "SELECT ti.id,ti.code,ti.name,ti.type,ti.lab_id labId,l.name laboratory,ti.validity_months validityMonths,ti.description,ti.enabled,ti.created_at createdAt FROM training_item ti LEFT JOIN laboratory l ON l.id=ti.lab_id";
        String order = " ORDER BY ti.enabled DESC,ti.lab_id IS NULL DESC,ti.id";
        if (access.isGlobalViewer(auth)) return ApiResult.ok(db.queryForList(sql + order));
        return ApiResult.ok(db.queryForList(sql + " WHERE ti.lab_id IS NULL OR ti.lab_id IN(SELECT lab_id FROM lab_admin_lab al JOIN lab_user u ON u.id=al.user_id WHERE u.username=?)" + order, auth.getName()));
    }

    @GetMapping("/trainings/mine")
    public ApiResult<?> mine(Authentication auth) {
        String sql = "SELECT g.id,g.status,g.source,g.score,g.evidence_url evidenceUrl,g.remark,g.review_comment reviewComment,g.applied_at appliedAt,g.reviewed_at reviewedAt,g.granted_at grantedAt,g.expires_at expiresAt,"
            + "ti.id itemId,ti.code itemCode,ti.name itemName,ti.type itemType,ti.validity_months validityMonths,l.name laboratory,"
            + "CASE WHEN g.status='APPROVED' AND (g.expires_at IS NULL OR g.expires_at>UTC_TIMESTAMP()) THEN 1 ELSE 0 END effective,"
            + "CASE WHEN g.expires_at IS NULL THEN NULL ELSE DATEDIFF(g.expires_at,UTC_DATE()) END daysLeft "
            + "FROM training_grant g JOIN training_item ti ON ti.id=g.training_item_id LEFT JOIN laboratory l ON l.id=ti.lab_id "
            + "WHERE g.user_id=? ORDER BY g.id DESC";
        return ApiResult.ok(db.queryForList(sql, userId(auth.getName())));
    }

    @GetMapping("/trainings/access/{equipmentId}")
    public ApiResult<?> access(@PathVariable long equipmentId, Authentication auth) {
        var rows = db.queryForList("SELECT lab_id FROM equipment WHERE id=?", equipmentId);
        if (rows.isEmpty()) throw new BusinessException("设备不存在");
        Map<String, Object> result = trainingAccess.access(userId(auth.getName()), equipmentId);
        result.put("equipmentId", equipmentId);
        return ApiResult.ok(result);
    }

    @PostMapping("/trainings/{itemId}/apply")
    @Transactional
    public ApiResult<?> apply(@PathVariable long itemId, @RequestBody(required = false) ApplyInput input, Authentication auth) {
        var items = db.queryForList("SELECT * FROM training_item WHERE id=? AND enabled=1", itemId);
        if (items.isEmpty()) throw new BusinessException("培训项目不存在或已停用");
        ApplyInput in = input == null ? new ApplyInput(null, null) : input;
        String remark = in.remark() == null ? "" : in.remark().trim();
        if (remark.length() > 500) throw new BusinessException("申请说明不能超过 500 字");
        String evidenceUrl = in.evidenceUrl() == null ? null : in.evidenceUrl().trim();
        if (evidenceUrl != null && !evidenceUrl.isEmpty() && !evidenceUrl.startsWith("/uploads/")) throw new BusinessException("证明材料地址不合法");
        Long uid = userId(auth.getName());
        Integer effective = db.queryForObject("SELECT COUNT(*) FROM training_grant WHERE user_id=? AND training_item_id=? AND status='APPROVED' AND (expires_at IS NULL OR expires_at>UTC_TIMESTAMP())", Integer.class, uid, itemId);
        if (effective != null && effective > 0) throw new BusinessException("您已获得该项目授权，无需重复申请");
        Integer pending = db.queryForObject("SELECT COUNT(*) FROM training_grant WHERE user_id=? AND training_item_id=? AND status='PENDING'", Integer.class, uid, itemId);
        if (pending != null && pending > 0) throw new BusinessException("该项目已有待审批的申请，请等待审批结果");
        db.update("INSERT INTO training_grant(user_id,training_item_id,status,source,remark,evidence_url) VALUES(?,?,'PENDING','TRAINING',?,?)", uid, itemId, remark, evidenceUrl);
        Long grantId = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        db.update("INSERT INTO notification(user_id,title,content) SELECT DISTINCT ur.user_id,'培训授权申请待审批',CONCAT('用户【',u.real_name,'】（',COALESCE(ci.identity_no,'未登记'),'）申请参加培训项目【',ti.name,'】。申请说明：',IF(?='','未填写',?),'。') FROM user_role ur JOIN role role ON role.id=ur.role_id JOIN lab_user u ON u.id=? JOIN training_item ti ON ti.id=? LEFT JOIN campus_identity ci ON ci.id=u.identity_id LEFT JOIN lab_admin_lab al ON al.user_id=ur.user_id AND (ti.lab_id IS NULL OR al.lab_id=ti.lab_id) WHERE role.code='SYS_ADMIN' OR (role.code='LAB_ADMIN' AND (ti.lab_id IS NULL OR al.lab_id IS NOT NULL))", remark, remark, uid, itemId);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'TRAINING_APPLY','training_grant',?,JSON_OBJECT('itemId',?))", uid, grantId, itemId);
        return ApiResult.ok(Map.of("id", grantId, "status", "PENDING"));
    }

    @GetMapping("/trainings/grants")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN','READ_ONLY_ADMIN')")
    public ApiResult<?> grants(@RequestParam(required = false, defaultValue = "PENDING") String status, Authentication auth) {
        String filter = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase();
        if (filter != null && !STATUSES.contains(filter)) throw new BusinessException("授权状态不合法");
        StringBuilder sql = new StringBuilder("SELECT g.id,g.user_id userId,g.status,g.source,g.score,g.evidence_url evidenceUrl,g.remark,g.review_comment reviewComment,g.applied_at appliedAt,g.reviewed_at reviewedAt,g.granted_at grantedAt,g.expires_at expiresAt,u.real_name applicantName,ci.identity_no identityNo,u.phone,u.email,ti.id itemId,ti.code itemCode,ti.name itemName,ti.type itemType,ti.lab_id itemLabId,ti.validity_months itemValidity,l.name laboratory,rv.real_name reviewerName,CASE WHEN g.status='APPROVED' AND (g.expires_at IS NULL OR g.expires_at>UTC_TIMESTAMP()) THEN 1 ELSE 0 END effective FROM training_grant g JOIN lab_user u ON u.id=g.user_id LEFT JOIN campus_identity ci ON ci.id=u.identity_id JOIN training_item ti ON ti.id=g.training_item_id LEFT JOIN laboratory l ON l.id=ti.lab_id LEFT JOIN lab_user rv ON rv.id=g.reviewer_id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (filter != null) { sql.append(" AND g.status=?"); params.add(filter); }
        if (!access.isGlobalViewer(auth)) { sql.append(" AND (ti.lab_id IS NULL OR ti.lab_id IN(SELECT al.lab_id FROM lab_admin_lab al JOIN lab_user u2 ON u2.id=al.user_id WHERE u2.username=?))"); params.add(auth.getName()); }
        sql.append(" ORDER BY CASE WHEN g.status='PENDING' THEN 0 ELSE 1 END,g.applied_at DESC LIMIT 500");
        return ApiResult.ok(db.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping("/trainings/grants/{id}/review")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
    @Transactional
    public ApiResult<?> reviewGrant(@PathVariable long id, @RequestBody ReviewInput input, Authentication auth) {
        String decision = input.decision();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) throw new BusinessException("审批结果不合法");
        var rows = db.queryForList("SELECT g.*,ti.lab_id item_lab_id,ti.name item_name,ti.validity_months FROM training_grant g JOIN training_item ti ON ti.id=g.training_item_id WHERE g.id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException("授权申请不存在");
        var row = rows.get(0);
        if (!"PENDING".equals(row.get("status"))) throw new BusinessException("只有待审批的授权申请可以审核");
        Object itemLabId = row.get("item_lab_id");
        if (itemLabId != null) access.requireLab(auth, ((Number) itemLabId).longValue());
        Long reviewer = userId(auth.getName());
        String comment = input.comment() == null ? "" : input.comment().trim();
        if (comment.length() > 500) throw new BusinessException("审批意见不能超过 500 字");
        LocalDateTime expiresAt = null;
        if ("APPROVED".equals(decision)) {
            expiresAt = parseExpiresAt(input.expiresAt());
            if (expiresAt == null) {
                int validity = ((Number) row.get("validity_months")).intValue();
                if (validity > 0) expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusMonths(validity);
            }
            db.update("UPDATE training_grant SET status='APPROVED',reviewer_id=?,review_comment=?,reviewed_at=UTC_TIMESTAMP(),granted_at=UTC_TIMESTAMP(),expires_at=?,reminded_on=NULL WHERE id=?", reviewer, comment, expiresAt, id);
        } else db.update("UPDATE training_grant SET status='REJECTED',reviewer_id=?,review_comment=?,reviewed_at=UTC_TIMESTAMP() WHERE id=?", reviewer, comment, id);
        Long applicant = ((Number) row.get("user_id")).longValue();
        String content = "您申请的培训项目【" + row.get("item_name") + "】已" + ("APPROVED".equals(decision) ? "通过并生效" : "驳回")
            + (comment.isBlank() ? "" : "。审批意见：" + comment) + "。"
            + ("APPROVED".equals(decision) ? (expiresAt == null ? "该授权长期有效。" : "授权有效期至：" + formatExpiry(expiresAt) + "。") : "")
            + "可在“培训与授权”中查看。";
        db.update("INSERT INTO notification(user_id,title,content) VALUES(?, '培训授权审批结果', ?)", applicant, content);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'TRAINING_GRANT_REVIEW','training_grant',?,JSON_OBJECT('decision',?,'itemId',?,'comment',?))", reviewer, id, decision, row.get("training_item_id"), comment);
        return ApiResult.ok(Map.of("status", decision, "expiresAt", expiresAt == null ? "" : expiresAt.toString()));
    }

    @PostMapping("/trainings/grants/{id}/revoke")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
    @Transactional
    public ApiResult<?> revokeGrant(@PathVariable long id, @RequestBody(required = false) RevokeInput input, Authentication auth) {
        var rows = db.queryForList("SELECT g.*,ti.lab_id item_lab_id,ti.name item_name FROM training_grant g JOIN training_item ti ON ti.id=g.training_item_id WHERE g.id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException("授权记录不存在");
        var row = rows.get(0);
        if (!"APPROVED".equals(row.get("status"))) throw new BusinessException("只有已通过的授权可以撤销");
        Object itemLabId = row.get("item_lab_id");
        if (itemLabId != null) access.requireLab(auth, ((Number) itemLabId).longValue());
        Long reviewer = userId(auth.getName());
        String comment = input == null || input.comment() == null ? "" : input.comment().trim();
        if (comment.length() > 500) throw new BusinessException("撤销原因不能超过 500 字");
        db.update("UPDATE training_grant SET status='REVOKED',reviewer_id=?,review_comment=?,reviewed_at=UTC_TIMESTAMP() WHERE id=?", reviewer, comment, id);
        db.update("INSERT INTO notification(user_id,title,content) VALUES(?, '培训授权已撤销', ?)", ((Number) row.get("user_id")).longValue(), "您的培训项目【" + row.get("item_name") + "】授权已被撤销" + (comment.isBlank() ? "" : "。原因：" + comment) + "。如需继续使用相关设备，请重新申请并通过审批。");
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'TRAINING_GRANT_REVOKE','training_grant',?,JSON_OBJECT('itemId',?,'comment',?))", reviewer, id, row.get("training_item_id"), comment);
        return ApiResult.ok(Map.of("status", "REVOKED"));
    }

    @PostMapping("/trainings")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
    @Transactional
    public ApiResult<?> createItem(@Valid @RequestBody ItemInput input, Authentication auth) {
        ItemInput in = normalize(input);
        requireItemInput(in, auth);
        Integer duplicate = db.queryForObject("SELECT COUNT(*) FROM training_item WHERE code=?", Integer.class, in.code());
        if (duplicate != null && duplicate > 0) throw new BusinessException("培训项目编号已存在");
        db.update("INSERT INTO training_item(code,name,type,lab_id,validity_months,description,enabled) VALUES(?,?,?,?,?,?,?)", in.code(), in.name(), in.type(), in.labId(), in.validityMonths(), in.description(), in.enabled());
        Long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'TRAINING_ITEM_CREATE','training_item',?,JSON_OBJECT('code',?))", userId(auth.getName()), id, in.code());
        return ApiResult.ok(Map.of("id", id));
    }

    @PutMapping("/trainings/{id}")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
    @Transactional
    public ApiResult<?> updateItem(@PathVariable long id, @Valid @RequestBody ItemInput input, Authentication auth) {
        var current = trainingItem(id);
        Object currentLabId = current.get("lab_id");
        if (currentLabId == null && !access.isSystemAdmin(auth)) throw new BusinessException("校级培训项目只能由系统管理员维护");
        if (currentLabId != null) access.requireLab(auth, ((Number) currentLabId).longValue());
        ItemInput in = normalize(input);
        requireItemInput(in, auth);
        Integer duplicate = db.queryForObject("SELECT COUNT(*) FROM training_item WHERE code=? AND id<>?", Integer.class, in.code(), id);
        if (duplicate != null && duplicate > 0) throw new BusinessException("培训项目编号已存在");
        db.update("UPDATE training_item SET code=?,name=?,type=?,lab_id=?,validity_months=?,description=?,enabled=? WHERE id=?", in.code(), in.name(), in.type(), in.labId(), in.validityMonths(), in.description(), in.enabled(), id);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'TRAINING_ITEM_UPDATE','training_item',?,JSON_OBJECT('code',?,'enabled',?))", userId(auth.getName()), id, in.code(), in.enabled());
        return ApiResult.ok(Map.of("updated", true));
    }

    @PutMapping("/equipment/{id}/training-requirements")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')")
    @Transactional
    public ApiResult<?> updateRequirements(@PathVariable long id, @RequestBody RequirementInput input, Authentication auth) {
        access.requireEquipment(auth, id);
        List<Long> itemIds = input.itemIds() == null ? List.of() : input.itemIds().stream().filter(Objects::nonNull).distinct().toList();
        if (!itemIds.isEmpty()) {
            String marks = String.join(",", Collections.nCopies(itemIds.size(), "?"));
            String scope = access.isGlobalViewer(auth) ? "" : " AND (lab_id IS NULL OR lab_id IN(SELECT al.lab_id FROM lab_admin_lab al JOIN lab_user u ON u.id=al.user_id WHERE u.username=?))";
            List<Object> params = new ArrayList<>(itemIds);
            if (!scope.isEmpty()) params.add(auth.getName());
            Integer usable = db.queryForObject("SELECT COUNT(*) FROM training_item WHERE enabled=1 AND id IN(" + marks + ")" + scope, Integer.class, params.toArray());
            if (usable == null || usable != itemIds.size()) throw new BusinessException("准入要求包含不存在、已停用或无权使用的培训项目");
        }
        db.update("DELETE FROM equipment_training_requirement WHERE equipment_id=?", id);
        for (Long itemId : itemIds) db.update("INSERT INTO equipment_training_requirement(equipment_id,training_item_id) VALUES(?,?)", id, itemId);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'EQUIPMENT_TRAINING_REQUIREMENTS','equipment',?,JSON_OBJECT('count',?))", userId(auth.getName()), id, itemIds.size());
        return ApiResult.ok(Map.of("count", itemIds.size()));
    }

    private ItemInput normalize(ItemInput input) {
        String code = input.code().trim().toUpperCase();
        String name = input.name().trim();
        String type = input.type().trim().toUpperCase();
        String description = input.description() == null ? null : input.description().trim();
        return new ItemInput(code, name, type, input.labId(), input.validityMonths() == null ? 12 : input.validityMonths(), description, input.enabled() == null || input.enabled());
    }

    private void requireItemInput(ItemInput in, Authentication auth) {
        if (in.code().length() > 32) throw new BusinessException("培训项目编号不能超过 32 个字符");
        if (in.name().length() > 120) throw new BusinessException("培训项目名称不能超过 120 个字符");
        if (!TYPES.contains(in.type())) throw new BusinessException("培训项目类型不合法");
        if (in.description() != null && in.description().length() > 1000) throw new BusinessException("培训项目说明不能超过 1000 字");
        if (in.labId() == null) {
            if (!access.isSystemAdmin(auth)) throw new BusinessException("实验室管理员只能维护本实验室的培训项目");
            return;
        }
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM laboratory WHERE id=?", Integer.class, in.labId());
        if (exists == null || exists == 0) throw new BusinessException("所属实验室不存在");
        if (!access.isSystemAdmin(auth)) access.requireLab(auth, in.labId());
    }

    private LocalDateTime parseExpiresAt(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String value = text.trim().replace(' ', 'T');
            LocalDateTime expiresAt = value.length() == 10 ? LocalDate.parse(value).atTime(23, 59, 59) : LocalDateTime.parse(value);
            if (!expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) throw new BusinessException("授权有效期必须晚于当前时间");
            return expiresAt;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("授权有效期格式不正确"); }
    }

    private String formatExpiry(LocalDateTime expiresAt) {
        return expiresAt.atZone(ZoneOffset.UTC).withZoneSameInstant(LABORATORY_ZONE).format(DATE_LABEL);
    }
}
