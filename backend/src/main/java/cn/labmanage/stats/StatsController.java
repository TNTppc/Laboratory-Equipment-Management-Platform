package cn.labmanage.stats;

import cn.labmanage.common.ApiResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import cn.labmanage.security.AccessService;

@RestController @RequestMapping("/api/stats") @PreAuthorize("hasAnyRole('LAB_ADMIN','SYS_ADMIN','READ_ONLY_ADMIN')")
public class StatsController {
    private final JdbcTemplate db; private final AccessService access;
    public StatsController(JdbcTemplate db, AccessService access) { this.db=db; this.access=access; }
    @GetMapping public ApiResult<?> summary(Authentication auth) {
        db.update("UPDATE reservation SET status='COMPLETED' WHERE status='APPROVED' AND ends_at<=UTC_TIMESTAMP()");
        String scope=access.isGlobalViewer(auth)?"":" WHERE EXISTS(SELECT 1 FROM lab_admin_lab al JOIN lab_user au ON au.id=al.user_id WHERE al.lab_id=e.lab_id AND au.username=?)";
        Object[] args=access.isGlobalViewer(auth)?new Object[]{}:new Object[]{auth.getName()};
        var equipment=db.queryForMap("SELECT COUNT(*) total,COALESCE(SUM(e.status='AVAILABLE'),0) available,COALESCE(SUM(e.status='MAINTENANCE'),0) maintenance FROM equipment e"+scope,args);
        String reservationWhere=scope.replace(" WHERE "," WHERE r.equipment_id=e.id AND ");
        var bookings=db.queryForMap("SELECT COUNT(*) total,COALESCE(SUM(r.status='APPROVED'),0) approved,COALESCE(SUM(r.status IN ('APPROVED','REJECTED')),0) reviewed FROM reservation r JOIN equipment e ON e.id=r.equipment_id"+scope,args);
        var repairs=db.queryForMap("SELECT COUNT(*) total,COALESCE(SUM(t.status='COMPLETED'),0) completed FROM repair_ticket t JOIN equipment e ON e.id=t.equipment_id"+scope,args);
        var byLab=db.queryForList("SELECT l.name laboratory,COUNT(r.id) bookings FROM laboratory l LEFT JOIN equipment e ON e.lab_id=l.id LEFT JOIN reservation r ON r.equipment_id=e.id"+scope+" GROUP BY l.id,l.name ORDER BY bookings DESC",args);
        var usage=db.queryForList("SELECT e.name equipment,ROUND(COALESCE(SUM(TIMESTAMPDIFF(MINUTE,r.starts_at,r.ends_at)),0)/60,1) usageHours FROM equipment e LEFT JOIN reservation r ON r.equipment_id=e.id AND r.status IN ('APPROVED','COMPLETED')"+scope+" GROUP BY e.id,e.name ORDER BY usageHours DESC LIMIT 10",args);
        return ApiResult.ok(Map.of("equipment",equipment,"reservations",bookings,"repairs",repairs,"byLab",byLab,"usage",usage));
    }
}
