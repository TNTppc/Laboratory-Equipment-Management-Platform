package cn.labmanage;

import cn.labmanage.reservation.ReservationWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationWorkflowServiceTest {
    @Test void overduePendingReservationIsRejectedAndAudited() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.queryForList(contains("starts_at<=DATE_ADD"))).thenReturn(List.of(Map.of("id", 9L)));
        when(db.update(startsWith("UPDATE reservation SET status='REJECTED'"), eq(9L))).thenReturn(1);
        new ReservationWorkflowService(db).expireOverdueReservations();
        verify(db).update(startsWith("UPDATE reservation SET status='REJECTED'"), eq(9L));
        verify(db).update(startsWith("INSERT INTO reservation_review"), eq(9L));
        verify(db).update(startsWith("INSERT INTO notification"), eq(9L));
        verify(db).update(startsWith("INSERT INTO operation_log"), eq(9L));
    }
}
