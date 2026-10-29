package cn.labmanage.admin;

import cn.labmanage.common.ApiResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('SYS_ADMIN')")
public class AdminController {
    private final JdbcTemplate db;
    public AdminController(JdbcTemplate db) { this.db=db; }
    @GetMapping("/users") public ApiResult<?> users() { return ApiResult.ok(db.queryForList("SELECT u.id,u.username,u.real_name realName,u.email,u.phone,u.enabled,r.code role FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id ORDER BY u.id")); }
    @PostMapping("/users") public ApiResult<?> user(@RequestBody Map<String,String> in,org.springframework.security.crypto.password.PasswordEncoder encoder) {
        db.update("INSERT INTO lab_user(username,password_hash,real_name,email) VALUES(?,?,?,?)",in.get("username"),encoder.encode(in.getOrDefault("password","ChangeMe123!")),in.get("realName"),in.get("email"));
        db.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code=?",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class),in.getOrDefault("role","USER")); return ApiResult.ok(Map.of("created",true));
    }
    @PutMapping("/users/{id}") @Transactional public ApiResult<?> updateUser(@PathVariable long id,@RequestBody Map<String,Object> in) { db.update("UPDATE lab_user SET real_name=?,email=?,phone=?,enabled=? WHERE id=?",in.get("realName"),in.get("email"),in.get("phone"),Boolean.TRUE.equals(in.get("enabled")),id); db.update("DELETE FROM user_role WHERE user_id=?",id); db.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code=?",id,in.get("role")); return ApiResult.ok(Map.of("updated",true)); }
    @PostMapping("/labs") public ApiResult<?> lab(@RequestBody Map<String,String> in) { db.update("INSERT INTO laboratory(name,location) VALUES(?,?)",in.get("name"),in.get("location")); return ApiResult.ok(Map.of("created",true)); }
    @PostMapping("/categories") public ApiResult<?> category(@RequestBody Map<String,String> in) { db.update("INSERT INTO equipment_category(name,description) VALUES(?,?)",in.get("name"),in.get("description")); return ApiResult.ok(Map.of("created",true)); }
    @PutMapping("/labs/{id}") public ApiResult<?> updateLab(@PathVariable long id,@RequestBody Map<String,String> in) { db.update("UPDATE laboratory SET name=?,location=? WHERE id=?",in.get("name"),in.get("location"),id); return ApiResult.ok(Map.of("updated",true)); }
    @PutMapping("/categories/{id}") public ApiResult<?> updateCategory(@PathVariable long id,@RequestBody Map<String,String> in) { db.update("UPDATE equipment_category SET name=?,description=? WHERE id=?",in.get("name"),in.get("description"),id); return ApiResult.ok(Map.of("updated",true)); }
}
