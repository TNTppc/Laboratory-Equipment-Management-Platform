package cn.labmanage.repair;

import cn.labmanage.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import cn.labmanage.security.AccessService;

@RestController @RequestMapping("/api/repairs")
public class RepairController {
    private static final Set<String> FAULT_TYPES = Set.of("无法开机","运行异常","测量不准","部件损坏","网络或软件故障","安全隐患","其他");
    private final JdbcTemplate db; private final AccessService access;
    public RepairController(JdbcTemplate db, AccessService access) { this.db=db; this.access=access; }
    public record Report(@NotBlank String faultType,@NotBlank String description,String imageUrls) {}
    @PostMapping("/equipment/{equipmentId}") @Transactional public ApiResult<?> report(@PathVariable long equipmentId,@Valid @RequestBody Report r,Authentication auth) {
        if(equipmentId<=0) throw new BusinessException("请选择报修设备");
        if(!FAULT_TYPES.contains(r.faultType())) throw new BusinessException("请选择有效的故障类型");
        Long uid=db.queryForObject("SELECT id FROM lab_user WHERE username=?",Long.class,auth.getName());
        var equipment=db.queryForList("SELECT id FROM equipment WHERE id=? FOR UPDATE",equipmentId);
        if(equipment.isEmpty()) throw new BusinessException("请选择有效的报修设备");
        db.update("INSERT INTO repair_ticket(equipment_id,reporter_id,fault_type,description,image_urls) VALUES(?,?,?,?,?)",equipmentId,uid,r.faultType(),r.description(),r.imageUrls());
        db.update("UPDATE equipment SET status='MAINTENANCE' WHERE id=?",equipmentId);
        db.update("INSERT INTO notification(user_id,title,content) SELECT ur.user_id,'新设备报修',? FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE r.code IN ('LAB_ADMIN','SYS_ADMIN')",r.description());
        return ApiResult.ok(Map.of("id",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class)));
    }
    @GetMapping public ApiResult<?> list(Authentication auth) {
        boolean manager=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_LAB_ADMIN")||a.getAuthority().equals("ROLE_SYS_ADMIN"));
        String base="SELECT t.*,e.name equipment_name,u.real_name reporter_name FROM repair_ticket t JOIN equipment e ON e.id=t.equipment_id JOIN lab_user u ON u.id=t.reporter_id ";
        if(!manager) return ApiResult.ok(db.queryForList(base+"WHERE u.username=? ORDER BY t.created_at DESC",auth.getName()));
        if(access.isSystemAdmin(auth)) return ApiResult.ok(db.queryForList(base+"ORDER BY t.created_at DESC"));
        return ApiResult.ok(db.queryForList(base+"WHERE EXISTS(SELECT 1 FROM lab_admin_lab al JOIN lab_user au ON au.id=al.user_id WHERE al.lab_id=e.lab_id AND au.username=?) ORDER BY t.created_at DESC",auth.getName()));
    }
    @GetMapping("/{id}") public ApiResult<?> detail(@PathVariable long id,Authentication auth) {
        var rows=db.queryForList("SELECT t.*,e.name equipment_name,u.real_name reporter_name FROM repair_ticket t JOIN equipment e ON e.id=t.equipment_id JOIN lab_user u ON u.id=t.reporter_id WHERE t.id=?",id);
        if(rows.isEmpty()) throw new BusinessException("维修工单不存在"); var ticket=rows.get(0);
        boolean manager=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_LAB_ADMIN")||a.getAuthority().equals("ROLE_SYS_ADMIN"));
        if(manager) access.requireEquipment(auth,((Number)ticket.get("equipment_id")).longValue()); else if(!auth.getName().equals(db.queryForObject("SELECT username FROM lab_user WHERE id=?",String.class,ticket.get("reporter_id")))) throw new BusinessException("无权查看该维修工单");
        return ApiResult.ok(Map.of("ticket",ticket,"records",db.queryForList("SELECT rr.*,u.real_name operator_name FROM repair_record rr JOIN lab_user u ON u.id=rr.operator_id WHERE rr.ticket_id=? ORDER BY rr.created_at",id)));
    }
    @PostMapping("/{id}/process") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN')") @Transactional public ApiResult<?> process(@PathVariable long id,@RequestBody Map<String,Object> body,Authentication auth) {
        String next=(String)body.get("status"); if(next==null||!java.util.Set.of("PROCESSING","WAIT_ACCEPTANCE","COMPLETED","REJECTED").contains(next)) throw new BusinessException("维修状态不合法");
        var rows=db.queryForList("SELECT * FROM repair_ticket WHERE id=? FOR UPDATE",id); if(rows.isEmpty()) throw new BusinessException("维修工单不存在"); var t=rows.get(0); String prev=(String)t.get("status");
        access.requireEquipment(auth,((Number)t.get("equipment_id")).longValue());
        if(!RepairStateRules.canTransition(prev,next)) throw new BusinessException("不允许从"+prev+"流转到"+next);
        Long operator=db.queryForObject("SELECT id FROM lab_user WHERE username=?",Long.class,auth.getName()); String note=(String)body.getOrDefault("note",""); BigDecimal cost=body.containsKey("cost")?new BigDecimal(body.get("cost").toString()):null;
        db.update("UPDATE repair_ticket SET status=?,assignee_id=?,result=?,cost=? WHERE id=?",next,operator,note,cost,id); db.update("INSERT INTO repair_record(ticket_id,operator_id,status,note,cost) VALUES(?,?,?,?,?)",id,operator,next,note,cost);
        if(next.equals("COMPLETED")) db.update("UPDATE equipment SET status=? WHERE id=?",Boolean.parseBoolean(String.valueOf(body.getOrDefault("restoreAvailable",false)))?"AVAILABLE":"DISABLED",t.get("equipment_id"));
        db.update("INSERT INTO notification(user_id,title,content) SELECT reporter_id,'维修进度更新',? FROM repair_ticket WHERE id=?",next,id); db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,'REPAIR_PROCESS','repair_ticket',?,JSON_OBJECT('status',?,'note',?))",operator,id,next,note); return ApiResult.ok(Map.of("status",next));
    }
}
