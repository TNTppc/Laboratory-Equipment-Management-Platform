package cn.labmanage;

import cn.labmanage.common.BusinessException;
import cn.labmanage.reservation.AutoApprovalController;
import cn.labmanage.reservation.ReservationAccountabilityController;
import cn.labmanage.security.AccessService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationAccountabilityControllerTest {
    @Test
    void fulfillmentCannotChangeAnExistingResult() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("labadmin");
        when(db.queryForList(contains("SELECT * FROM reservation WHERE id=? FOR UPDATE"), eq(9L)))
            .thenReturn(List.of(Map.of("equipment_id", 4L, "status", "COMPLETED", "fulfillment_status", "NOT_FULFILLED")));

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        assertThrows(BusinessException.class, () -> controller.fulfillment(
            9L, Map.of("stage", "OVERDUE_NOT_RETURNED"), auth));
        verify(db, never()).update(startsWith("UPDATE reservation SET fulfillment_status"), (Object[]) any());
    }

    @Test
    void appealCannotBeSubmittedTwice() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("student");
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("student"))).thenReturn(2L);
        when(db.queryForObject(contains("FROM reservation WHERE id=? AND user_id=?"), eq(Integer.class), eq(7L), eq(2L))).thenReturn(1);
        when(db.update(startsWith("INSERT IGNORE INTO reservation_appeal"), eq(7L), eq(2L), eq("说明"))).thenReturn(0);

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        assertThrows(BusinessException.class, () -> controller.appeal(7L, Map.of("reason", "说明"), auth));
    }

    @Test
    void labAdminCannotConfigureGlobalAutoApproval() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(access.isSystemAdmin(auth)).thenReturn(false);

        AutoApprovalController controller = new AutoApprovalController(db, access);

        assertThrows(BusinessException.class, () -> controller.update(
            Map.of("scopeType", "GLOBAL", "action", "APPROVE", "minutes", 60), auth));
        verifyNoInteractions(db);
    }
}
