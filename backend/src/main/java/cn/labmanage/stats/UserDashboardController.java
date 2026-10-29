package cn.labmanage.stats;

import cn.labmanage.common.ApiResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class UserDashboardController {
    private final JdbcTemplate db;
    public UserDashboardController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/me")
    public ApiResult<?> mine(Authentication auth) {
        Long uid=db.queryForObject("SELECT id FROM lab_user WHERE username=?",Long.class,auth.getName());
        var summary=db.queryForMap("SELECT COALESCE(SUM(status='PENDING'),0) pending,COALESCE(SUM(status='APPROVED' AND ends_at>UTC_TIMESTAMP()),0) upcoming FROM reservation WHERE user_id=?",uid);
        Integer unread=db.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=? AND read_at IS NULL",Integer.class,uid);
        Integer repairs=db.queryForObject("SELECT COUNT(*) FROM repair_ticket WHERE reporter_id=? AND status IN ('OPEN','PROCESSING','WAIT_ACCEPTANCE')",Integer.class,uid);
        var upcoming=db.queryForList("SELECT r.id,e.name equipment_name,l.name laboratory,r.starts_at,r.ends_at,r.status FROM reservation r JOIN equipment e ON e.id=r.equipment_id JOIN laboratory l ON l.id=e.lab_id WHERE r.user_id=? AND r.status IN ('PENDING','APPROVED') AND r.ends_at>UTC_TIMESTAMP() ORDER BY r.starts_at LIMIT 5",uid);
        var managers=db.queryForList("SELECT DISTINCT u.real_name name,u.email,u.phone,l.name laboratory FROM lab_user u JOIN user_role ur ON ur.user_id=u.id JOIN role role ON role.id=ur.role_id JOIN lab_admin_lab al ON al.user_id=u.id JOIN laboratory l ON l.id=al.lab_id WHERE role.code='LAB_ADMIN' AND u.enabled=1 ORDER BY l.name,u.real_name");
        return ApiResult.ok(Map.of("reservations",summary,"unreadNotifications",unread,"activeRepairs",repairs,"upcoming",upcoming,"managers",managers));
    }
}
