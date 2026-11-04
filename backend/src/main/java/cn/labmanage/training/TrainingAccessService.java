package cn.labmanage.training;

import cn.labmanage.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrainingAccessService {
    private final JdbcTemplate db;

    public TrainingAccessService(JdbcTemplate db) { this.db = db; }

    public List<Map<String, Object>> requiredItems(long equipmentId) {
        return db.queryForList("SELECT ti.id,ti.code,ti.name,ti.type FROM equipment_training_requirement r JOIN training_item ti ON ti.id=r.training_item_id AND ti.enabled=1 WHERE r.equipment_id=? ORDER BY ti.id", equipmentId);
    }

    public List<Map<String, Object>> missingItems(long userId, long equipmentId) {
        return db.queryForList("SELECT ti.id,ti.code,ti.name,ti.type FROM equipment_training_requirement r JOIN training_item ti ON ti.id=r.training_item_id AND ti.enabled=1 WHERE r.equipment_id=? AND NOT EXISTS(SELECT 1 FROM training_grant g WHERE g.user_id=? AND g.training_item_id=r.training_item_id AND g.status='APPROVED' AND (g.expires_at IS NULL OR g.expires_at>UTC_TIMESTAMP())) ORDER BY ti.id", equipmentId, userId);
    }

    public Map<String, Object> access(long userId, long equipmentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("required", requiredItems(equipmentId));
        result.put("missing", missingItems(userId, equipmentId));
        return result;
    }

    public void requireEquipmentAccess(Long userId, long equipmentId) {
        if (userId == null) throw new BusinessException("无法确认预约人，不能校验设备准入授权");
        List<Map<String, Object>> missing = missingItems(userId, equipmentId);
        if (missing.isEmpty()) return;
        String names = missing.stream().map(item -> String.valueOf(item.get("name"))).collect(Collectors.joining("、"));
        throw new BusinessException("缺少设备准入授权：" + names + "。请先在“培训与授权”中完成安全培训或仪器操作考试，审批通过后再预约。");
    }
}
