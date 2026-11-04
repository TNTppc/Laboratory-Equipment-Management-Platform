package cn.labmanage;

import cn.labmanage.reservation.ReservationController;
import cn.labmanage.reservation.ReservationWorkflowService;
import cn.labmanage.security.AccessService;
import cn.labmanage.training.TrainingAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationControllerTest {
    private static Authentication auth(String name) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(name);
        return auth;
    }

    private static ReservationController controller(JdbcTemplate db) {
        return new ReservationController(db, mock(AccessService.class), mock(ReservationWorkflowService.class), mock(TrainingAccessService.class));
    }

    private static JdbcTemplate reservation(JdbcTemplate db, long id, long userId, String status, LocalDateTime startsAt) {
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("student"))).thenReturn(userId);
        when(db.queryForList(contains("FROM reservation WHERE id=? AND user_id=?"), eq(id), eq(userId)))
            .thenReturn(List.of(Map.of("status", status, "starts_at", startsAt, "ends_at", startsAt.plusHours(2))));
        return db;
    }

    private static double cancelledPenalty(JdbcTemplate db, long id) {
        return ((Number) ((Map<?, ?>) controller(db).cancel(id, auth("student")).data()).get("penalty")).doubleValue();
    }

    @Test
    void cancellingMoreThan48HoursBeforeStartDeductsOnePoint() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        reservation(db, 9L, 2L, "APPROVED", LocalDateTime.now(ZoneOffset.UTC).plusHours(49));
        when(db.update(startsWith("INSERT IGNORE INTO credit_ledger"), eq(2L), eq(9L), eq(-1.0), eq("cancel:9"))).thenReturn(1);

        assertEquals(1.0, cancelledPenalty(db, 9L));
        verify(db).update(contains("UPDATE lab_user SET credit_score=GREATEST(0.0,credit_score-?)"), eq(1.0), eq(2L));
    }

    @Test
    void cancellingBetween24And48HoursBeforeStartDeductsTwoPoints() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        reservation(db, 9L, 2L, "APPROVED", LocalDateTime.now(ZoneOffset.UTC).plusHours(30));
        when(db.update(startsWith("INSERT IGNORE INTO credit_ledger"), eq(2L), eq(9L), eq(-2.0), eq("cancel:9"))).thenReturn(1);

        assertEquals(2.0, cancelledPenalty(db, 9L));
        verify(db).update(contains("UPDATE lab_user SET credit_score=GREATEST(0.0,credit_score-?)"), eq(2.0), eq(2L));
    }

    @Test
    void cancellingWithin24HoursBeforeStartDeductsFourPoints() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        reservation(db, 9L, 2L, "APPROVED", LocalDateTime.now(ZoneOffset.UTC).plusHours(5));
        when(db.update(startsWith("INSERT IGNORE INTO credit_ledger"), eq(2L), eq(9L), eq(-4.0), eq("cancel:9"))).thenReturn(1);

        assertEquals(4.0, cancelledPenalty(db, 9L));
        verify(db).update(contains("UPDATE lab_user SET credit_score=GREATEST(0.0,credit_score-?)"), eq(4.0), eq(2L));
        verify(db).update(contains("UPDATE reservation SET status='CANCELLED'"), eq(9L));
    }

    @Test
    void cancellingPendingReservationDoesNotDeductCredit() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        reservation(db, 9L, 2L, "PENDING", LocalDateTime.now(ZoneOffset.UTC).plusHours(10));

        assertEquals(0.0, cancelledPenalty(db, 9L));
        verify(db, never()).update(startsWith("INSERT IGNORE INTO credit_ledger"), (Object[]) any());
        verify(db, never()).update(contains("credit_score-?"), (Object[]) any());
    }

    @Test
    void cancelPreviewReportsPointsWithoutChangingReservation() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("student"))).thenReturn(2L);
        when(db.queryForList(contains("SELECT id,status,starts_at FROM reservation"), eq(9L), eq(2L)))
            .thenReturn(List.of(Map.of("status", "APPROVED", "starts_at", LocalDateTime.now(ZoneOffset.UTC).plusHours(30))));

        Map<?, ?> preview = (Map<?, ?>) controller(db).cancelPreview(9L, auth("student")).data();

        assertEquals(2.0, ((Number) preview.get("penalty")).doubleValue());
        verify(db, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void revokingApprovedReservationWithinTwoHoursIsRejected() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("labadmin"))).thenReturn(3L);
        when(db.queryForList(contains("SELECT * FROM reservation WHERE id=? FOR UPDATE"), eq(9L)))
            .thenReturn(List.of(Map.of("equipment_id", 4L, "status", "APPROVED", "auto_credit_approved", false,
                "starts_at", LocalDateTime.now(ZoneOffset.UTC).plusHours(1),
                "ends_at", LocalDateTime.now(ZoneOffset.UTC).plusHours(2))));

        ReservationController controller = new ReservationController(db, access, mock(ReservationWorkflowService.class), mock(TrainingAccessService.class));

        org.junit.jupiter.api.Assertions.assertThrows(cn.labmanage.common.BusinessException.class,
            () -> controller.revokeApproval(9L, auth("labadmin")));
        verify(db, never()).update(startsWith("UPDATE reservation SET status='PENDING'"), (Object[]) any());
    }

    @Test
    void revokingAnyApprovedReservationBeforeTheTwoHourDeadlineReturnsItToPending() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AccessService access = mock(AccessService.class);
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("labadmin"))).thenReturn(3L);
        when(db.queryForList(contains("SELECT * FROM reservation WHERE id=? FOR UPDATE"), eq(9L)))
            .thenReturn(List.of(Map.of("equipment_id", 4L, "status", "APPROVED", "auto_credit_approved", false,
                "starts_at", LocalDateTime.now(ZoneOffset.UTC).plusHours(5),
                "ends_at", LocalDateTime.now(ZoneOffset.UTC).plusHours(6))));

        ReservationController controller = new ReservationController(db, access, mock(ReservationWorkflowService.class), mock(TrainingAccessService.class));

        controller.revokeApproval(9L, auth("labadmin"));

        verify(db).update(contains("UPDATE reservation SET status='PENDING',auto_credit_approved=FALSE"), eq(9L));
        verify(db).update(contains("预约已被撤销通过"), eq(9L));
    }

    @Test
    void creatingReservationWithoutTrainingGrantIsRejectedBeforeInsert() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        TrainingAccessService training = mock(TrainingAccessService.class);
        when(db.queryForObject("SELECT id FROM equipment WHERE id=? FOR UPDATE", Long.class, 4L)).thenReturn(4L);
        when(db.queryForObject(contains("SELECT id FROM lab_user"), eq(Long.class), eq("student"))).thenReturn(2L);
        when(db.queryForObject(contains("SELECT credit_score"), eq(BigDecimal.class), eq(2L))).thenReturn(BigDecimal.valueOf(100));
        when(db.queryForList(contains("SELECT e.status,l.open_time,l.close_time"), eq(4L))).thenReturn(List.of(Map.of(
            "status", "AVAILABLE", "open_time", java.sql.Time.valueOf("08:00:00"), "close_time", java.sql.Time.valueOf("22:00:00"))));
        when(db.queryForObject(contains("SELECT COUNT(*) FROM reservation"), eq(Integer.class), any(), any(), any(), any(), any())).thenReturn(0);
        doThrow(new cn.labmanage.common.BusinessException("缺少设备准入授权：协作机器人操作考试")).when(training).requireEquipmentAccess(2L, 4L);
        ReservationController controller = new ReservationController(db, mock(AccessService.class), mock(ReservationWorkflowService.class), training);

        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).plusDays(2).withHour(1).withMinute(0).withSecond(0).withNano(0);
        ReservationController.Booking booking = new ReservationController.Booking(4L, start, start.plusHours(2), "测试", 1);

        assertThrows(cn.labmanage.common.BusinessException.class, () -> controller.create(booking, auth("student")));
        verify(db, never()).update(startsWith("INSERT INTO reservation("), (Object[]) any());
    }

    @Test
    void approvingReservationWithoutTrainingGrantIsRejected() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        TrainingAccessService training = mock(TrainingAccessService.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).plusDays(2).withHour(1).withMinute(0).withSecond(0).withNano(0);
        when(db.queryForList("SELECT * FROM reservation WHERE id=? FOR UPDATE", 9L)).thenReturn(List.of(Map.of(
            "equipment_id", 4L, "user_id", 2L, "status", "PENDING", "purpose", "测试", "participants", 1,
            "starts_at", start, "ends_at", start.plusHours(2))));
        when(db.queryForObject("SELECT id FROM equipment WHERE id=? FOR UPDATE", Long.class, 4L)).thenReturn(4L);
        when(db.queryForList(contains("SELECT e.status,l.open_time,l.close_time"), eq(4L))).thenReturn(List.of(Map.of(
            "status", "AVAILABLE", "open_time", java.sql.Time.valueOf("08:00:00"), "close_time", java.sql.Time.valueOf("22:00:00"))));
        when(db.queryForObject(contains("SELECT COUNT(*) FROM reservation"), eq(Integer.class), any(), any(), any(), any(), any())).thenReturn(0);
        doThrow(new cn.labmanage.common.BusinessException("缺少设备准入授权：协作机器人操作考试")).when(training).requireEquipmentAccess(2L, 4L);
        ReservationController controller = new ReservationController(db, mock(AccessService.class), mock(ReservationWorkflowService.class), training);

        assertThrows(cn.labmanage.common.BusinessException.class, () -> controller.review(9L, Map.of("decision", "APPROVED"), auth("labadmin")));
        verify(db, never()).update(startsWith("UPDATE reservation SET status=?"), (Object[]) any());
    }
}
