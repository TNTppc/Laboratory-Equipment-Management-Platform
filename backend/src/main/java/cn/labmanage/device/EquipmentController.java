package cn.labmanage.device;

import cn.labmanage.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import cn.labmanage.security.AccessService;
import java.time.LocalDate;

@RestController @RequestMapping("/api")
public class EquipmentController {
    private final JdbcTemplate db; private final AccessService access; private final EquipmentMapper mapper;
    public EquipmentController(JdbcTemplate db, AccessService access, EquipmentMapper mapper) { this.db=db; this.access=access; this.mapper=mapper; }
    @GetMapping("/equipment") public ApiResult<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long labId,@RequestParam(required=false) Long categoryId,@RequestParam(required=false) String status) {
        int p=Math.max(page,1), s=Math.min(Math.max(size,1),100); String kw="%"+(keyword==null?"":keyword)+"%";
        var rows=db.queryForList("SELECT e.id,e.code,e.name,e.category_id categoryId,e.lab_id labId,e.owner_id ownerId,e.brand_model brandModel,e.location,e.description,e.usage_rules usageRules,e.image_url imageUrl,e.purchase_date purchaseDate,e.status,c.name category,l.name laboratory,l.open_time,l.close_time,u.real_name ownerName FROM equipment e JOIN equipment_category c ON c.id=e.category_id JOIN laboratory l ON l.id=e.lab_id LEFT JOIN lab_user u ON u.id=e.owner_id WHERE (e.name LIKE ? OR e.code LIKE ?) AND (? IS NULL OR e.lab_id=?) AND (? IS NULL OR e.category_id=?) AND (? IS NULL OR e.status=?) ORDER BY e.id DESC LIMIT ? OFFSET ?",kw,kw,labId,labId,categoryId,categoryId,status,status,s,(p-1)*s);
        Integer total=db.queryForObject("SELECT COUNT(*) FROM equipment e WHERE (e.name LIKE ? OR e.code LIKE ?) AND (? IS NULL OR e.lab_id=?) AND (? IS NULL OR e.category_id=?) AND (? IS NULL OR e.status=?)",Integer.class,kw,kw,labId,labId,categoryId,categoryId,status,status);
        return ApiResult.ok(Map.of("items",rows,"total",total,"page",p,"size",s));
    }
    @GetMapping("/equipment/{id}") public ApiResult<?> detail(@PathVariable long id) { return ApiResult.ok(db.queryForMap("SELECT e.*,c.name category,l.name laboratory,u.real_name ownerName FROM equipment e JOIN equipment_category c ON c.id=e.category_id JOIN laboratory l ON l.id=e.lab_id LEFT JOIN lab_user u ON u.id=e.owner_id WHERE e.id=?",id)); }
    @GetMapping("/equipment/options") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") public ApiResult<?> options(Authentication auth) {
        if (access.isGlobalViewer(auth)) return ApiResult.ok(db.queryForList("SELECT e.id,e.code,e.name,l.name laboratory FROM equipment e JOIN laboratory l ON l.id=e.lab_id ORDER BY l.name,e.code"));
        return ApiResult.ok(db.queryForList("SELECT e.id,e.code,e.name,l.name laboratory FROM equipment e JOIN laboratory l ON l.id=e.lab_id WHERE EXISTS(SELECT 1 FROM lab_admin_lab al JOIN lab_user u ON u.id=al.user_id WHERE al.lab_id=e.lab_id AND u.username=?) ORDER BY l.name,e.code", auth.getName()));
    }
    public record EquipmentInput(@NotBlank String code,@NotBlank String name,long categoryId,long labId,String brandModel,String location,Long ownerId,String description,String usageRules,String imageUrl,LocalDate purchaseDate,String status) {}
    @PostMapping("/equipment") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") public ApiResult<?> create(@Valid @RequestBody EquipmentInput in, Authentication auth) {
        access.requireLab(auth,in.labId());
        EquipmentEntity entity=toEntity(in); mapper.insert(entity);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) SELECT id,'EQUIPMENT_CREATE','equipment',?,JSON_OBJECT('code',?) FROM lab_user WHERE username=?",entity.id,in.code(),auth.getName()); return ApiResult.ok(Map.of("id",entity.id));
    }
    @PutMapping("/equipment/{id}") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") public ApiResult<?> update(@PathVariable long id,@Valid @RequestBody EquipmentInput in,Authentication auth) {
        access.requireEquipment(auth,id); access.requireLab(auth,in.labId());
        EquipmentEntity entity=toEntity(in); entity.id=id; mapper.updateById(entity); db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) SELECT id,'EQUIPMENT_UPDATE','equipment',?,JSON_OBJECT('status',?) FROM lab_user WHERE username=?",id,entity.status,auth.getName()); return ApiResult.ok(Map.of("updated",true));
    }
    private EquipmentEntity toEntity(EquipmentInput in) { EquipmentEntity e=new EquipmentEntity(); e.code=in.code();e.name=in.name();e.categoryId=in.categoryId();e.labId=in.labId();e.brandModel=in.brandModel();e.location=in.location();e.ownerId=in.ownerId();e.description=in.description();e.usageRules=in.usageRules();e.imageUrl=in.imageUrl();e.purchaseDate=in.purchaseDate();e.status=in.status()==null?"AVAILABLE":in.status();return e; }
    @GetMapping("/labs") public ApiResult<?> labs() { return ApiResult.ok(db.queryForList("SELECT * FROM laboratory WHERE enabled=1 ORDER BY name")); }
    @GetMapping("/categories") public ApiResult<?> categories() { return ApiResult.ok(db.queryForList("SELECT * FROM equipment_category ORDER BY name")); }
    @GetMapping("/users/options") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") public ApiResult<?> userOptions() { return ApiResult.ok(db.queryForList("SELECT id,real_name name FROM lab_user WHERE enabled=1 ORDER BY real_name")); }
}
