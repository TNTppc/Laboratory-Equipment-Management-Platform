package cn.labmanage.reservation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationWorkflowService {
    private final JdbcTemplate db;

    public ReservationWorkflowService(JdbcTemplate db) { this.db = db; }

    @Scheduled(initialDelay = 20_000, fixedDelay = 86_400_000)
    @Transactional
    public void recoverCreditDaily() {
        var users = db.queryForList("SELECT id,credit_score,credit_recovered_on FROM lab_user WHERE credit_recovered_on IS NULL OR credit_recovered_on<UTC_DATE() FOR UPDATE");
        for (var user : users) {
            long id = ((Number) user.get("id")).longValue();
            java.sql.Date recoveredOn = (java.sql.Date) user.get("credit_recovered_on");
            long days = recoveredOn == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(recoveredOn.toLocalDate(), java.time.LocalDate.now(java.time.ZoneOffset.UTC));
            if (days > 0) {
                double score = ((Number) user.get("credit_score")).doubleValue();
                double delta = Math.min(100.0, score + days * 0.5) - score;
                if (delta > 0) {
                    String key = "recovery:" + id + ":" + java.time.LocalDate.now(java.time.ZoneOffset.UTC);
                    if (db.update("INSERT IGNORE INTO credit_ledger(user_id,delta,reason,idempotency_key) VALUES(?,?,?,?)", id, delta, "每日信誉恢复", key) == 1) {
                        db.update("UPDATE lab_user SET credit_score=LEAST(100.0,credit_score+?),credit_recovered_on=UTC_DATE() WHERE id=?", delta, id);
                    }
                } else db.update("UPDATE lab_user SET credit_recovered_on=UTC_DATE() WHERE id=?", id);
            } else db.update("UPDATE lab_user SET credit_recovered_on=UTC_DATE() WHERE id=?", id);
        }
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 60_000)
    @Transactional
    public void expireOverdueReservations() {
        var rows = db.queryForList("SELECT r.id FROM reservation r WHERE r.status='PENDING' AND r.starts_at<=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 2 HOUR) FOR UPDATE");
        for (var row : rows) {
            long id = ((Number) row.get("id")).longValue();
            int changed = db.update("UPDATE reservation SET status='REJECTED',reject_reason='APPROVAL_TIMEOUT',review_comment='超过审批时限，系统自动驳回' WHERE id=? AND status='PENDING'", id);
            if (changed == 0) continue;
            db.update("INSERT INTO reservation_review(reservation_id,reviewer_id,decision,comment) VALUES(?,NULL,'REJECTED','超过审批时限，系统自动驳回')", id);
            db.update("INSERT INTO notification(user_id,title,content) SELECT r.user_id,'预约已自动驳回',CONCAT('设备【',e.code,' ',e.name,'】的预约因距离开始时间不足2小时仍未审批，系统已自动驳回。预约时间：',DATE_FORMAT(CONVERT_TZ(r.starts_at,'+00:00','+08:00'),'%Y年%m月%d日 %H:%i'),'。如仍需使用，请联系实验室管理员。') FROM reservation r JOIN equipment e ON e.id=r.equipment_id WHERE r.id=?", id);
            db.update("INSERT INTO operation_log(operator_id,action,target_type,target_id,detail) VALUES(NULL,'RESERVATION_AUTO_REJECT','reservation',?,JSON_OBJECT('reason','approval_timeout'))", id);
        }
    }
}
