package cn.labmanage.device;

import cn.labmanage.common.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Objects;
import cn.labmanage.security.AccessService;
import java.time.LocalDate;

@RestController @RequestMapping("/api")
public class EquipmentController {
    private final JdbcTemplate db; private final AccessService access; private final EquipmentMapper mapper; private final ObjectMapper json;
    public EquipmentController(JdbcTemplate db, AccessService access, EquipmentMapper mapper, ObjectMapper json) { this.db=db; this.access=access; this.mapper=mapper; this.json=json; }
    @GetMapping("/equipment") public ApiResult<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long labId,@RequestParam(required=false) Long categoryId,@RequestParam(required=false) String status) {
        int p=Math.max(page,1), s=Math.min(Math.max(size,1),100); String kw="%"+(keyword==null?"":keyword)+"%";
        var rows=db.queryForList("SELECT e.id,e.code,e.name,e.category_id categoryId,e.lab_id labId,e.owner_id ownerId,e.brand_model brandModel,e.location,e.description,e.usage_rules usageRules,e.image_url imageUrl,e.purchase_date purchaseDate,e.status,c.name category,l.name laboratory,l.open_time,l.close_time,u.real_name ownerName,EXISTS(SELECT 1 FROM equipment_change_request cr WHERE cr.equipment_id=e.id AND cr.status='PENDING') changePending,(SELECT GROUP_CONCAT(ti.name ORDER BY ti.id SEPARATOR '、') FROM equipment_training_requirement r JOIN training_item ti ON ti.id=r.training_item_id AND ti.enabled=1 WHERE r.equipment_id=e.id) trainingRequirements,(SELECT COUNT(*) FROM equipment_training_requirement r JOIN training_item ti ON ti.id=r.training_item_id AND ti.enabled=1 WHERE r.equipment_id=e.id) trainingRequirementCount FROM equipment e JOIN equipment_category c ON c.id=e.category_id JOIN laboratory l ON l.id=e.lab_id LEFT JOIN lab_user u ON u.id=e.owner_id WHERE (e.name LIKE ? OR e.code LIKE ?) AND (? IS NULL OR e.lab_id=?) AND (? IS NULL OR e.category_id=?) AND (? IS NULL OR e.status=?) ORDER BY e.id DESC LIMIT ? OFFSET ?",kw,kw,labId,labId,categoryId,categoryId,status,status,s,(p-1)*s);
        Integer total=db.queryForObject("SELECT COUNT(*) FROM equipment e WHERE (e.name LIKE ? OR e.code LIKE ?) AND (? IS NULL OR e.lab_id=?) AND (? IS NULL OR e.category_id=?) AND (? IS NULL OR e.status=?)",Integer.class,kw,kw,labId,labId,categoryId,categoryId,status,status);
        return ApiResult.ok(Map.of("items",rows,"total",total,"page",p,"size",s));
    }
    @GetMapping("/equipment/{id}") public ApiResult<?> detail(@PathVariable long id) {
        var rows=db.queryForList("SELECT e.*,c.name category,l.name laboratory,u.real_name ownerName FROM equipment e JOIN equipment_category c ON c.id=e.category_id JOIN laboratory l ON l.id=e.lab_id LEFT JOIN lab_user u ON u.id=e.owner_id WHERE e.id=?",id);
        if (rows.isEmpty()) throw new BusinessException("设备不存在");
        java.util.Map<String,Object> detail=new java.util.HashMap<>(rows.get(0));
        detail.put("trainingRequirements",db.queryForList("SELECT ti.id,ti.code,ti.name,ti.type FROM equipment_training_requirement r JOIN training_item ti ON ti.id=r.training_item_id AND ti.enabled=1 WHERE r.equipment_id=? ORDER BY ti.id",id));
        detail.put("trainingRequirementIds",db.queryForList("SELECT training_item_id FROM equipment_training_requirement WHERE equipment_id=? ORDER BY training_item_id",Long.class,id));
        return ApiResult.ok(detail);
    }
    @GetMapping("/equipment/options") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") public ApiResult<?> options(Authentication auth) {
        if (access.isGlobalViewer(auth)) return ApiResult.ok(db.queryForList("SELECT e.id,e.code,e.name,l.name laboratory FROM equipment e JOIN laboratory l ON l.id=e.lab_id ORDER BY l.name,e.code"));
        return ApiResult.ok(db.queryForList("SELECT e.id,e.code,e.name,l.name laboratory FROM equipment e JOIN laboratory l ON l.id=e.lab_id WHERE EXISTS(SELECT 1 FROM lab_admin_lab al JOIN lab_user u ON u.id=al.user_id WHERE al.lab_id=e.lab_id AND u.username=?) ORDER BY l.name,e.code", auth.getName()));
    }
    public record EquipmentInput(@NotBlank String code,@NotBlank String name,long categoryId,long labId,String brandModel,String location,Long ownerId,String description,String usageRules,String imageUrl,LocalDate purchaseDate,String status) {}
    @PostMapping("/equipment") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") public ApiResult<?> create(@Valid @RequestBody EquipmentInput in, Authentication auth) {
        access.requireLab(auth,in.labId());
        if (!access.isSystemAdmin(auth) && in.ownerId() != null) throw new BusinessException("只有系统管理员可以设置设备责任人");
        EquipmentEntity entity=toEntity(in);
        validateCode(0L,in.code()); mapper.insert(entity);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) SELECT id,'EQUIPMENT_CREATE','equipment',?,JSON_OBJECT('code',?) FROM lab_user WHERE username=?",entity.id,in.code(),auth.getName()); return ApiResult.ok(Map.of("id",entity.id));
    }
    @PutMapping("/equipment/{id}") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") @Transactional public ApiResult<?> update(@PathVariable long id,@Valid @RequestBody EquipmentInput in,Authentication auth) {
        access.requireEquipment(auth,id); access.requireLab(auth,in.labId());
        var current=db.queryForList("SELECT owner_id FROM equipment WHERE id=? FOR UPDATE",id);
        if (current.isEmpty()) throw new BusinessException("设备不存在");
        Object ownerRow=current.get(0).get("owner_id"); Long currentOwner=ownerRow==null?null:((Number)ownerRow).longValue();
        Long operator=db.queryForObject("SELECT id FROM lab_user WHERE username=?",Long.class,auth.getName());
        if (!access.isSystemAdmin(auth)) {
            if (!Objects.equals(currentOwner,in.ownerId())) throw new BusinessException("只有系统管理员可以修改设备责任人");
            validateCode(id,in.code());
            String payload=toPayload(in);
            var pending=db.queryForList("SELECT id FROM equipment_change_request WHERE equipment_id=? AND status='PENDING' FOR UPDATE",id);
            if (pending.isEmpty()) db.update("INSERT INTO equipment_change_request(equipment_id,applicant_id,payload) VALUES(?,?,?)",id,operator,payload);
            else db.update("UPDATE equipment_change_request SET applicant_id=?,payload=?,created_at=UTC_TIMESTAMP() WHERE id=?",operator,payload,pending.get(0).get("id"));
            db.update("INSERT INTO notification(user_id,title,content) SELECT DISTINCT ur.user_id,'设备档案变更待审批',CONCAT('实验室管理员【',ap.real_name,'】提交了设备【',e.code,' ',e.name,'】（',l.name,'）的档案变更申请，审批通过后才会生效。') FROM user_role ur JOIN role role ON role.id=ur.role_id JOIN lab_user ap ON ap.id=? JOIN equipment e ON e.id=? JOIN laboratory l ON l.id=e.lab_id WHERE role.code='SYS_ADMIN'",operator,id);
            db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'EQUIPMENT_CHANGE_SUBMIT','equipment',?,JSON_OBJECT('status',?))",operator,id,in.status());
            return ApiResult.ok(Map.of("pendingApproval",true));
        }
        EquipmentEntity entity=toEntity(in); entity.id=id;
        validateCode(id,in.code()); mapper.updateById(entity);
        if (in.ownerId()==null) db.update("UPDATE equipment SET owner_id=NULL WHERE id=?",id);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'EQUIPMENT_UPDATE','equipment',?,JSON_OBJECT('status',?))",operator,id,entity.status);
        return ApiResult.ok(Map.of("updated",true));
    }
    @GetMapping("/equipment/change-requests") @PreAuthorize("hasRole('SYS_ADMIN')") public ApiResult<?> changeRequests() {
        return ApiResult.ok(db.queryForList("SELECT c.id,c.equipment_id equipmentId,c.payload,c.created_at,u.real_name applicant_name,e.code currentCode,e.name currentName,e.category_id currentCategoryId,e.lab_id currentLabId,e.brand_model currentBrandModel,e.location currentLocation,e.owner_id currentOwnerId,e.description currentDescription,e.usage_rules currentUsageRules,e.image_url currentImageUrl,DATE_FORMAT(e.purchase_date,'%Y-%m-%d') currentPurchaseDate,e.status currentStatus,l.name laboratory FROM equipment_change_request c JOIN lab_user u ON u.id=c.applicant_id JOIN equipment e ON e.id=c.equipment_id JOIN laboratory l ON l.id=e.lab_id WHERE c.status='PENDING' ORDER BY c.created_at"));
    }
    @PostMapping("/equipment/change-requests/{id}/review") @PreAuthorize("hasRole('SYS_ADMIN')") @Transactional public ApiResult<?> reviewChange(@PathVariable long id,@RequestBody Map<String,String> body,Authentication auth) {
        String decision=body.get("decision");
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) throw new BusinessException("审批结果不合法");
        var rows=db.queryForList("SELECT * FROM equipment_change_request WHERE id=? AND status='PENDING' FOR UPDATE",id);
        if (rows.isEmpty()) throw new BusinessException("设备变更申请不存在或已处理");
        var request=rows.get(0); long equipmentId=((Number)request.get("equipment_id")).longValue();
        Long reviewer=db.queryForObject("SELECT id FROM lab_user WHERE username=?",Long.class,auth.getName());
        String comment=body.getOrDefault("comment","");
        if ("APPROVED".equals(decision)) {
            EquipmentInput in;
            try { in=json.readValue(String.valueOf(request.get("payload")),EquipmentInput.class); }
            catch (JsonProcessingException e) { throw new BusinessException("设备变更内容无法解析，请驳回后重新提交"); }
            validateCode(equipmentId,in.code());
            EquipmentEntity entity=toEntity(in); entity.id=equipmentId; entity.ownerId=null; mapper.updateById(entity);
        }
        db.update("UPDATE equipment_change_request SET status=?,reviewer_id=?,review_comment=?,reviewed_at=UTC_TIMESTAMP() WHERE id=?",decision,reviewer,comment,id);
        db.update("INSERT INTO notification(user_id,title,content) SELECT c.applicant_id,'设备档案变更审批结果',CONCAT('您提交的设备【',e.code,' ',e.name,'】档案变更已',CASE ? WHEN 'APPROVED' THEN '通过并生效' ELSE '驳回' END,IF(?<>'',CONCAT('。审核意见：',?),''),'。') FROM equipment_change_request c JOIN equipment e ON e.id=c.equipment_id WHERE c.id=?",decision,comment,comment,id);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'EQUIPMENT_CHANGE_REVIEW','equipment',?,JSON_OBJECT('requestId',?,'decision',?,'comment',?))",reviewer,equipmentId,id,decision,comment);
        return ApiResult.ok(Map.of("status",decision));
    }
    private void validateCode(long excludeId,String code) {
        Integer duplicate=db.queryForObject("SELECT COUNT(*) FROM equipment WHERE code=? AND id<>?",Integer.class,code,excludeId);
        if (duplicate!=null && duplicate>0) throw new BusinessException("设备编号已存在");
    }
    private String toPayload(EquipmentInput in) {
        try { return json.writeValueAsString(in); }
        catch (JsonProcessingException e) { throw new BusinessException("设备变更内容处理失败"); }
    }
    private EquipmentEntity toEntity(EquipmentInput in) { EquipmentEntity e=new EquipmentEntity(); e.code=in.code();e.name=in.name();e.categoryId=in.categoryId();e.labId=in.labId();e.brandModel=in.brandModel();e.location=in.location();e.ownerId=in.ownerId();e.description=in.description();e.usageRules=in.usageRules();e.imageUrl=in.imageUrl();e.purchaseDate=in.purchaseDate();e.status=in.status()==null?"AVAILABLE":in.status();return e; }
    @GetMapping("/labs") public ApiResult<?> labs() { return ApiResult.ok(db.queryForList("SELECT * FROM laboratory WHERE enabled=1 ORDER BY name")); }
    @GetMapping("/categories") public ApiResult<?> categories() { return ApiResult.ok(db.queryForList("SELECT * FROM equipment_category ORDER BY name")); }
    @GetMapping("/users/options") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") public ApiResult<?> userOptions() { return ApiResult.ok(db.queryForList("SELECT id,real_name name FROM lab_user WHERE enabled=1 ORDER BY real_name")); }
}
