package cn.labmanage.notification;

import cn.labmanage.common.ApiResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcTemplate db;
    public NotificationController(JdbcTemplate db) { this.db=db; }
    @GetMapping public ApiResult<?> list(Authentication auth) { return ApiResult.ok(db.queryForList("SELECT n.* FROM notification n JOIN lab_user u ON u.id=n.user_id WHERE u.username=? ORDER BY n.created_at DESC",auth.getName())); }
    @PostMapping("/{id}/read") public ApiResult<?> read(@PathVariable long id,Authentication auth) { db.update("UPDATE notification n JOIN lab_user u ON u.id=n.user_id SET n.read_at=UTC_TIMESTAMP() WHERE n.id=? AND u.username=?",id,auth.getName()); return ApiResult.ok(Map.of("read",true)); }
}
