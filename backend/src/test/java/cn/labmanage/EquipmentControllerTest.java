package cn.labmanage;

import cn.labmanage.common.BusinessException;
import cn.labmanage.device.EquipmentController;
import cn.labmanage.device.EquipmentEntity;
import cn.labmanage.device.EquipmentMapper;
import cn.labmanage.security.AccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EquipmentControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private record Fixture(EquipmentController controller, JdbcTemplate db, EquipmentMapper mapper, Authentication auth) {}

    private static Fixture fixture(boolean sysadmin, Long currentOwner) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        EquipmentMapper mapper = mock(EquipmentMapper.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("labadmin");
        when(access.isSystemAdmin(auth)).thenReturn(sysadmin);
        when(db.queryForList(contains("SELECT owner_id FROM equipment"), eq(4L)))
            .thenReturn(List.of(Collections.singletonMap("owner_id", currentOwner)));
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("labadmin"))).thenReturn(2L);
        when(db.queryForObject(contains("SELECT COUNT(*) FROM equipment WHERE code"), eq(Integer.class), anyString(), anyLong())).thenReturn(0);
        when(db.queryForList(contains("SELECT id FROM equipment_change_request"), eq(4L))).thenReturn(List.of());
        return new Fixture(new EquipmentController(db, access, mapper, JSON), db, mapper, auth);
    }

    private static EquipmentController.EquipmentInput input(Long ownerId, String name) {
        return new EquipmentController.EquipmentInput("EQ-001", name, 1L, 1L, "Rigol DS1104Z", "A201", ownerId,
            "设备说明", "使用规则", "", LocalDate.of(2024, 1, 10), "AVAILABLE");
    }

    @Test
    void labAdminCannotChangeOwner() {
        Fixture fixture = fixture(false, 3L);

        assertThrows(BusinessException.class, () -> fixture.controller().update(4L, input(5L, "数字示波器"), fixture.auth()));
        verify(fixture.mapper(), never()).updateById(any(EquipmentEntity.class));
    }

    @Test
    void labAdminChangeIsQueuedForSystemAdminApproval() {
        Fixture fixture = fixture(false, 3L);

        assertDoesNotThrow(() -> fixture.controller().update(4L, input(3L, "数字示波器"), fixture.auth()));

        verify(fixture.db()).update(startsWith("INSERT INTO equipment_change_request"), eq(4L), eq(2L), anyString());
        verify(fixture.mapper(), never()).updateById(any(EquipmentEntity.class));
        verify(fixture.db()).update(contains("设备档案变更待审批"), anyLong(), eq(4L));
    }

    @Test
    void systemAdminApprovalAppliesPendingChange() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        EquipmentMapper mapper = mock(EquipmentMapper.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
        String payload = JSON.writeValueAsString(input(3L, "新名称"));
        when(db.queryForList(contains("SELECT * FROM equipment_change_request"), eq(11L)))
            .thenReturn(List.of(Map.of("equipment_id", 4L, "payload", payload)));
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("admin"))).thenReturn(3L);
        when(db.queryForObject(contains("SELECT COUNT(*) FROM equipment WHERE code"), eq(Integer.class), anyString(), anyLong())).thenReturn(0);
        EquipmentController controller = new EquipmentController(db, access, mapper, JSON);

        controller.reviewChange(11L, Map.of("decision", "APPROVED"), auth);

        verify(mapper).updateById(argThat((EquipmentEntity entity) -> entity.id == 4L && "新名称".equals(entity.name) && entity.ownerId == null));
        verify(db).update(startsWith("UPDATE equipment_change_request SET status"), eq("APPROVED"), eq(3L), eq(""), eq(11L));
    }

    @Test
    void rejectedChangeDoesNotTouchEquipment() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        EquipmentMapper mapper = mock(EquipmentMapper.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
        when(db.queryForList(contains("SELECT * FROM equipment_change_request"), eq(11L)))
            .thenReturn(List.of(Map.of("equipment_id", 4L, "payload", "{}")));
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("admin"))).thenReturn(3L);
        EquipmentController controller = new EquipmentController(db, access, mapper, JSON);

        controller.reviewChange(11L, Map.of("decision", "REJECTED", "comment", "信息不完整"), auth);

        verify(mapper, never()).updateById(any(EquipmentEntity.class));
        verify(db).update(startsWith("UPDATE equipment_change_request SET status"), eq("REJECTED"), eq(3L), eq("信息不完整"), eq(11L));
    }
}
