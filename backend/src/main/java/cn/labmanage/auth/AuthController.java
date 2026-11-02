package cn.labmanage.auth;

import cn.labmanage.common.*;
import cn.labmanage.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank String username,@NotBlank @Size(min=8) String password,@NotBlank String identityNo,@NotBlank String realName,@NotBlank @Email String email,@NotBlank String phone) {}
    public record ProfileRequest(@NotBlank @Email String email,@NotBlank String phone,String currentPassword,String newPassword) {}
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final JwtService jwt;
    public AuthController(JdbcTemplate db, PasswordEncoder encoder, JwtService jwt) { this.db=db; this.encoder=encoder; this.jwt=jwt; }
    @PostMapping("/login") public ApiResult<?> login(@Valid @RequestBody LoginRequest req) {
        var rows=db.queryForList("SELECT u.id,u.username,u.password_hash,u.real_name,u.email,u.phone,u.credit_score,ci.identity_no identityNo,r.code role FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id LEFT JOIN campus_identity ci ON ci.id=u.identity_id WHERE u.username=? AND u.enabled=1",req.username());
        if(rows.isEmpty() || !encoder.matches(req.password(),(String)rows.get(0).get("password_hash"))) throw new BusinessException("用户名或密码错误");
        var row=rows.get(0); String token=jwt.issue(((Number)row.get("id")).longValue(),(String)row.get("username"),(String)row.get("role"));
        return ApiResult.ok(Map.of("token",token,"user",profile(row)));
    }
    @PostMapping("/register") @Transactional public ApiResult<?> register(@Valid @RequestBody RegisterRequest req) {
        if(!req.identityNo().matches("\\d{7}|\\d{9}")) throw new BusinessException("学生学号应为9位数字，教职工工号应为7位数字");
        var identities=db.queryForList("SELECT ci.* FROM campus_identity ci LEFT JOIN lab_user u ON u.identity_id=ci.id WHERE ci.identity_no=? AND ci.real_name=? AND ci.active=1 AND u.id IS NULL FOR UPDATE",req.identityNo(),req.realName());
        if(identities.isEmpty()) throw new BusinessException("姓名与学号或工号不匹配，无法注册");
        var identity=identities.get(0); String role=(String)identity.get("default_role_code");
        db.update("INSERT INTO lab_user(username,password_hash,real_name,email,phone,identity_id) VALUES(?,?,?,?,?,?)",req.username(),encoder.encode(req.password()),req.realName(),req.email(),req.phone(),identity.get("id"));
        Long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        db.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code=?",id,role); return ApiResult.ok(Map.of("registered",true));
    }
    @GetMapping("/me") public ApiResult<?> me(java.security.Principal principal) { var rows=db.queryForList("SELECT u.id,u.username,u.real_name,u.email,u.phone,u.credit_score,ci.identity_no identityNo,ci.person_type personType,r.code role FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id LEFT JOIN campus_identity ci ON ci.id=u.identity_id WHERE u.username=?",principal.getName()); return ApiResult.ok(rows.isEmpty()?Map.of():profile(rows.get(0))); }
    @PutMapping("/me") @Transactional public ApiResult<?> updateProfile(@Valid @RequestBody ProfileRequest req, Authentication auth) {
        var row=db.queryForMap("SELECT id,password_hash FROM lab_user WHERE username=? FOR UPDATE",auth.getName());
        if(req.newPassword()!=null&&!req.newPassword().isBlank()) { if(req.currentPassword()==null||!encoder.matches(req.currentPassword(),(String)row.get("password_hash"))) throw new BusinessException("当前密码不正确"); if(req.newPassword().length()<8) throw new BusinessException("新密码至少8位"); db.update("UPDATE lab_user SET email=?,phone=?,password_hash=? WHERE id=?",req.email(),req.phone(),encoder.encode(req.newPassword()),row.get("id")); }
        else db.update("UPDATE lab_user SET email=?,phone=? WHERE id=?",req.email(),req.phone(),row.get("id"));
        return ApiResult.ok(Map.of("updated",true));
    }
    public record DeactivateRequest(@NotBlank String currentPassword) {}
    @DeleteMapping("/me") @Transactional public ApiResult<?> deactivate(@Valid @RequestBody DeactivateRequest req, Authentication auth) {
        var row=db.queryForMap("SELECT id,password_hash FROM lab_user WHERE username=? AND enabled=1 FOR UPDATE",auth.getName());
        if(!encoder.matches(req.currentPassword(),(String)row.get("password_hash"))) throw new BusinessException("当前密码不正确，账号未注销");
        Long userId=((Number)row.get("id")).longValue();
        Integer sysAdmin=db.queryForObject("SELECT COUNT(*) FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='SYS_ADMIN'",Integer.class,userId);
        if(sysAdmin!=null&&sysAdmin>0) throw new BusinessException("系统管理员账号不能自行注销，请先交接系统管理权限");
        db.update("UPDATE lab_user SET enabled=0 WHERE id=?",userId);
        db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(?,?,?,?,JSON_OBJECT('username',?))",userId,"注销账号","USER",userId,auth.getName());
        return ApiResult.ok(Map.of("deactivated",true));
    }
    @GetMapping("/contacts") public ApiResult<?> contacts(Authentication auth) {
        String role=db.queryForObject("SELECT r.code FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id WHERE u.username=?",String.class,auth.getName());
        if("USER".equals(role)) return ApiResult.ok(db.queryForList("SELECT DISTINCT u.real_name name,u.email,u.phone,l.name laboratory FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id JOIN lab_admin_lab al ON al.user_id=u.id JOIN laboratory l ON l.id=al.lab_id WHERE r.code='LAB_ADMIN' AND u.enabled=1 ORDER BY l.name,u.real_name"));
        return ApiResult.ok(db.queryForList("SELECT DISTINCT u.real_name name,u.email,u.phone,ci.identity_no identityNo FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id LEFT JOIN campus_identity ci ON ci.id=u.identity_id WHERE r.code='USER' AND u.enabled=1 ORDER BY u.real_name"));
    }
    @PostMapping("/logout") public ApiResult<?> logout() { return ApiResult.ok(Map.of("loggedOut",true)); }
    private Map<String,Object> profile(Map<String,Object> row) { java.util.HashMap<String,Object> result=new java.util.HashMap<>(); result.put("id",row.get("id"));result.put("username",row.get("username"));result.put("realName",row.get("real_name"));result.put("role",row.get("role"));result.put("email",row.get("email"));result.put("phone",row.get("phone"));result.put("creditScore",row.get("credit_score"));result.put("identityNo",row.get("identityNo"));result.put("personType",row.get("personType"));result.put("labIds",db.queryForList("SELECT lab_id FROM lab_admin_lab WHERE user_id=?",Long.class,row.get("id")));return result; }
}
