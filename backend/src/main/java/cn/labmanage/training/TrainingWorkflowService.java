package cn.labmanage.training;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class TrainingWorkflowService {
    private static final ZoneId LABORATORY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private final JdbcTemplate db;

    public TrainingWorkflowService(JdbcTemplate db) { this.db = db; }

    @Scheduled(initialDelay = 30_000, fixedDelay = 86_400_000)
    @Transactional
    public void remindExpiringGrants() {
        var rows = db.queryForList("SELECT g.id,g.user_id,g.expires_at,ti.name FROM training_grant g JOIN training_item ti ON ti.id=g.training_item_id WHERE g.status='APPROVED' AND g.expires_at IS NOT NULL AND g.expires_at BETWEEN UTC_TIMESTAMP() AND DATE_ADD(UTC_TIMESTAMP(),INTERVAL 30 DAY) AND g.reminded_on IS NULL FOR UPDATE");
        for (var row : rows) {
            long id = ((Number) row.get("id")).longValue();
            long userId = ((Number) row.get("user_id")).longValue();
            LocalDateTime expiresAt = cn.labmanage.common.DateTimeValues.toLocalDateTime(row.get("expires_at"));
            String content = "您的培训授权【" + row.get("name") + "】将于 " + expiresAt.atZone(ZoneOffset.UTC).withZoneSameInstant(LABORATORY_ZONE).format(DATE_LABEL) + " 到期。到期后相关设备将不能预约，请及时重新申请或参加考试。";
            db.update("INSERT INTO notification(user_id,title,content) VALUES(?, '培训授权即将到期', ?)", userId, content);
            db.update("UPDATE training_grant SET reminded_on=UTC_DATE() WHERE id=?", id);
        }
    }
}
