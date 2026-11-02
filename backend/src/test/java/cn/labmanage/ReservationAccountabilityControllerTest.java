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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        when(db.queryForObject(contains("ends_at>=DATE_SUB"), eq(Integer.class), eq(7L))).thenReturn(1);
        when(db.update(startsWith("INSERT IGNORE INTO reservation_appeal"), eq(7L), eq(2L), eq("说明"), isNull())).thenReturn(0);

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        assertThrows(BusinessException.class, () -> controller.appeal(7L, Map.of("reason", "说明"), auth));
    }

    @Test
    void revokingFulfillmentAddsCompensatingLedgerEntryWithoutChangingHistory() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("labadmin");
        when(db.queryForList(contains("SELECT * FROM reservation WHERE id=? FOR UPDATE"), eq(9L)))
            .thenReturn(List.of(Map.of("equipment_id", 4L, "user_id", 2L, "fulfillment_status", "NOT_FULFILLED",
                "fulfillment_operator_id", 3L, "fulfillment_penalty", 4.0)));
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("labadmin"))).thenReturn(3L);

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        controller.correctFulfillment(9L, Map.of("stage", "NOT_STARTED"), auth);

        verify(db).update(contains("INSERT INTO credit_ledger"), eq(2L), eq(9L), eq(4.0), eq("履约违约撤销返还"), anyString());
        verify(db).update(contains("UPDATE lab_user SET credit_score"), eq(4.0), eq(2L));
        verify(db, never()).update(startsWith("UPDATE credit_ledger"), (Object[]) any());
    }

    @Test
    void appealIsAllowedAtSevenDaysButNotAfter() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("student");
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("student"))).thenReturn(2L);
        when(db.queryForObject(contains("fulfillment_status IN"), eq(Integer.class), eq(7L), eq(2L))).thenReturn(1);
        when(db.queryForObject(contains("ends_at>=DATE_SUB"), eq(Integer.class), eq(7L))).thenReturn(1);
        when(db.update(startsWith("INSERT IGNORE INTO reservation_appeal"), eq(7L), eq(2L), eq("说明"), isNull())).thenReturn(1);

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        assertDoesNotThrow(() -> controller.appeal(7L, Map.of("reason", "说明"), auth));
        verify(db).queryForObject(contains("ends_at>=DATE_SUB"), eq(Integer.class), eq(7L));
    }

    @Test
    void appealMayIncludeEvidenceImage() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("student");
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("student"))).thenReturn(2L);
        when(db.queryForObject(contains("fulfillment_status IN"), eq(Integer.class), eq(7L), eq(2L))).thenReturn(1);
        when(db.queryForObject(contains("ends_at>=DATE_SUB"), eq(Integer.class), eq(7L))).thenReturn(1);
        when(db.update(startsWith("INSERT IGNORE INTO reservation_appeal"), eq(7L), eq(2L), eq("\u8bf4\u660e"), eq("/uploads/evidence-1.png"))).thenReturn(1);

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        assertDoesNotThrow(() -> controller.appeal(7L, Map.of("reason", "\u8bf4\u660e", "imageUrl", "/uploads/evidence-1.png"), auth));
        verify(db).update(startsWith("INSERT IGNORE INTO reservation_appeal"), eq(7L), eq(2L), eq("\u8bf4\u660e"), eq("/uploads/evidence-1.png"));
    }

    @Test
    void approvingAppealRefundsStoredPenalty() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("labadmin");
        when(db.queryForList(contains("SELECT a.*,r.equipment_id"), eq(12L))).thenReturn(List.of(Map.of("equipment_id", 4L, "status", "PENDING")));
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("labadmin"))).thenReturn(3L);
        when(db.update(startsWith("UPDATE reservation_appeal SET status"), eq("APPROVED"), eq(3L), eq(""), eq(12L))).thenReturn(1);
        when(db.queryForMap(contains("fulfillment_penalty"), eq(12L))).thenReturn(Map.of("applicant_id", 2L, "reservation_id", 9L, "fulfillment_status", "OVERDUE_NOT_RETURNED", "fulfillment_penalty", 7.0));
        when(db.update(startsWith("INSERT IGNORE INTO credit_ledger"), eq(2L), eq(9L), eq(7.0), eq("申诉通过返还"), eq("appeal:12"))).thenReturn(1);

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        controller.reviewAppeal(12L, Map.of("decision", "APPROVED"), auth);

        verify(db).update(startsWith("INSERT IGNORE INTO credit_ledger"), eq(2L), eq(9L), eq(7.0), eq("申诉通过返还"), eq("appeal:12"));
        verify(db).update(contains("UPDATE lab_user SET credit_score=LEAST(100.0,credit_score+?)"), eq(7.0), eq(2L));
        verify(db).update(startsWith("UPDATE reservation SET fulfillment_status='COMPLETED'"), eq(9L));
    }

    @Test
    void appealSubmissionNotifiesReviewersInsteadOfOnlyTheApplicant() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("student");
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("student"))).thenReturn(2L);
        when(db.queryForObject(contains("fulfillment_status IN"), eq(Integer.class), eq(7L), eq(2L))).thenReturn(1);
        when(db.queryForObject(contains("ends_at>=DATE_SUB"), eq(Integer.class), eq(7L))).thenReturn(1);
        when(db.update(startsWith("INSERT IGNORE INTO reservation_appeal"), eq(7L), eq(2L), eq("说明"), isNull())).thenReturn(1);

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        controller.appeal(7L, Map.of("reason", "说明"), auth);

        verify(db).update(contains("新的履约申诉待审核"), eq("说明"), eq(7L));
    }

    @Test
    void correctionIsBlockedAfterAppealWasApproved() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("labadmin");
        when(db.queryForList(contains("SELECT * FROM reservation WHERE id=? FOR UPDATE"), eq(9L)))
            .thenReturn(List.of(Map.of("equipment_id", 4L, "user_id", 2L, "fulfillment_status", "NOT_FULFILLED",
                "fulfillment_operator_id", 3L, "fulfillment_penalty", 4.0)));
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("labadmin"))).thenReturn(3L);
        when(db.queryForObject(contains("SELECT COUNT(*) FROM reservation_appeal"), eq(Integer.class), eq(9L))).thenReturn(1);

        ReservationAccountabilityController controller = new ReservationAccountabilityController(db, access);

        assertThrows(BusinessException.class, () -> controller.correctFulfillment(9L, Map.of("stage", "RETURNED"), auth));
        verify(db, never()).update(startsWith("UPDATE reservation SET fulfillment_status"), (Object[]) any());
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
