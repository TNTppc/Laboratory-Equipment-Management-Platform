package cn.labmanage.security;

import cn.labmanage.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AccessService {
    private final JdbcTemplate db;
    public AccessService(JdbcTemplate db) { this.db = db; }

    public boolean isSystemAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SYS_ADMIN"));
    }

    public void requireLab(Authentication auth, long labId) {
        if (isSystemAdmin(auth)) return;
        Integer count = db.queryForObject("SELECT COUNT(*) FROM lab_admin_lab al JOIN lab_user u ON u.id=al.user_id WHERE u.username=? AND al.lab_id=?", Integer.class, auth.getName(), labId);
        if (count == null || count == 0) throw new BusinessException("无权操作该实验室的数据");
    }

    public void requireEquipment(Authentication auth, long equipmentId) {
        Long labId = db.queryForObject("SELECT lab_id FROM equipment WHERE id=?", Long.class, equipmentId);
        if (labId == null) throw new BusinessException("设备不存在");
        requireLab(auth, labId);
    }
}
