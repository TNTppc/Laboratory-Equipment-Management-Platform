package cn.labmanage.reservation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationWorkflowService {
    private final JdbcTemplate db;

    public ReservationWorkflowService(JdbcTemplate db) { this.db = db; }

    @Scheduled(initialDelay = 10_000, fixedDelay = 60_000)
    @Transactional
    public void expireOverdueReservations() {
        var rows = db.queryForList("SELECT r.id FROM reservation r WHERE r.status='PENDING' AND r.starts_at<=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 2 HOUR) FOR UPDATE");
        for (var row : rows) {
            long id = ((Number) row.get("id")).longValue();
            int changed = db.update("UPDATE reservation SET status='REJECTED',review_comment='超过审批时限，系统自动驳回' WHERE id=? AND status='PENDING'", id);
            if (changed == 0) continue;
            db.update("INSERT INTO reservation_review(reservation_id,reviewer_id,decision,comment) VALUES(?,NULL,'REJECTED','超过审批时限，系统自动驳回')", id);
            db.update("INSERT INTO notification(user_id,title,content) SELECT r.user_id,'预约已自动驳回',CONCAT('设备【',e.code,' ',e.name,'】的预约因距离开始时间不足2小时仍未审批，系统已自动驳回。预约时间：',DATE_FORMAT(CONVERT_TZ(r.starts_at,'+00:00','+08:00'),'%Y年%m月%d日 %H:%i'),'。如仍需使用，请联系实验室管理员。') FROM reservation r JOIN equipment e ON e.id=r.equipment_id WHERE r.id=?", id);
            db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(NULL,'RESERVATION_AUTO_REJECT','reservation',?,JSON_OBJECT('reason','approval_timeout'))", id);
        }
    }
}
