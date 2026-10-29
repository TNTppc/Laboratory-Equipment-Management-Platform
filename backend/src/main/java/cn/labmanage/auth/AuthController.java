package cn.labmanage.auth;

import cn.labmanage.common.*;
import cn.labmanage.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final JwtService jwt;
    public AuthController(JdbcTemplate db, PasswordEncoder encoder, JwtService jwt) { this.db=db; this.encoder=encoder; this.jwt=jwt; }
    @PostMapping("/login") public ApiResult<?> login(@Valid @RequestBody LoginRequest req) {
        var rows=db.queryForList("SELECT u.id,u.username,u.password_hash,u.real_name,r.code role FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id WHERE u.username=? AND u.enabled=1",req.username());
        if(rows.isEmpty() || !encoder.matches(req.password(),(String)rows.get(0).get("password_hash"))) throw new BusinessException("用户名或密码错误");
        var row=rows.get(0); String token=jwt.issue(((Number)row.get("id")).longValue(),(String)row.get("username"),(String)row.get("role"));
        return ApiResult.ok(Map.of("token",token,"user",Map.of("id",row.get("id"),"username",row.get("username"),"realName",row.get("real_name"),"role",row.get("role"))));
    }
    @GetMapping("/me") public ApiResult<?> me(java.security.Principal principal) { return ApiResult.ok(db.queryForList("SELECT u.id,u.username,u.real_name realName,r.code role FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id WHERE u.username=?",principal.getName())); }
    @PostMapping("/logout") public ApiResult<?> logout() { return ApiResult.ok(Map.of("loggedOut",true)); }
}
