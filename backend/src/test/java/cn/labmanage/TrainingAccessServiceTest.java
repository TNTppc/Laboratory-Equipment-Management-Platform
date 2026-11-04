package cn.labmanage;

import cn.labmanage.common.BusinessException;
import cn.labmanage.training.TrainingAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingAccessServiceTest {
    @Test
    void missingGrantsBlockReservationAndListTrainingNames() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(contains("FROM equipment_training_requirement"), eq(9L), eq(2L)))
            .thenReturn(List.of(Map.of("name", "协作机器人操作考试"), Map.of("name", "实验室安全基础")));

        BusinessException error = assertThrows(BusinessException.class,
            () -> new TrainingAccessService(db).requireEquipmentAccess(2L, 9L));

        assertTrue(error.getMessage().contains("协作机器人操作考试"));
        assertTrue(error.getMessage().contains("实验室安全基础"));
    }

    @Test
    void allRequiredGrantsAllowReservation() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(contains("FROM equipment_training_requirement"), eq(9L), eq(2L))).thenReturn(List.of());

        assertDoesNotThrow(() -> new TrainingAccessService(db).requireEquipmentAccess(2L, 9L));
    }
}
