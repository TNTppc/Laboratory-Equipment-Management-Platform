package cn.labmanage;

import cn.labmanage.reservation.ReservationRules;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;

class ReservationRulesTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 28, 0, 0);

    @Test void detectsOverlapUsingHalfOpenIntervals() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 29, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 29, 10, 0);
        assertTrue(ReservationRules.overlaps(start, end, start.plusMinutes(30), end.plusMinutes(30)));
        assertFalse(ReservationRules.overlaps(start, end, end, end.plusHours(1)));
        assertFalse(ReservationRules.overlaps(start, end, start.minusHours(1), start));
    }

    @Test void rejectsInvalidAndPastBookings() {
        assertThrows(IllegalArgumentException.class, () -> ReservationRules.validate(now.plusHours(2), now.plusHours(1), now, LocalTime.of(8,0), LocalTime.of(22,0), "AVAILABLE"));
        assertThrows(IllegalArgumentException.class, () -> ReservationRules.validate(now.minusHours(1), now.plusHours(1), now, LocalTime.of(8,0), LocalTime.of(22,0), "AVAILABLE"));
    }

    @Test void rejectsMaintenanceEquipmentAndClosedHours() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 29, 9, 0);
        assertThrows(IllegalArgumentException.class, () -> ReservationRules.validate(start, start.plusHours(1), now, LocalTime.of(8,0), LocalTime.of(22,0), "MAINTENANCE"));
        assertThrows(IllegalArgumentException.class, () -> ReservationRules.validate(start.minusHours(2), start, now, LocalTime.of(8,0), LocalTime.of(22,0), "AVAILABLE"));
    }

    @Test void acceptsValidBooking() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 29, 9, 0);
        assertDoesNotThrow(() -> ReservationRules.validate(start, start.plusHours(1), now, LocalTime.of(8,0), LocalTime.of(22,0), "AVAILABLE"));
    }

    @Test void validatesOpeningHoursInLaboratoryTimeZone() {
        LocalDateTime startUtc = LocalDateTime.of(2026, 10, 29, 3, 49);
        LocalDateTime endUtc = LocalDateTime.of(2026, 10, 29, 4, 49);
        assertDoesNotThrow(() -> ReservationRules.validateUtc(startUtc, endUtc, now, LocalTime.of(8,0), LocalTime.of(22,0), "AVAILABLE", ZoneId.of("Asia/Shanghai")));
        assertThrows(IllegalArgumentException.class, () -> ReservationRules.validateUtc(startUtc.minusHours(5), endUtc.minusHours(5), now.minusDays(1), LocalTime.of(8,0), LocalTime.of(22,0), "AVAILABLE", ZoneId.of("Asia/Shanghai")));
    }

    @Test void enforcesBookingWindowFromTomorrowThroughSevenDays() {
        LocalDateTime nowUtc = LocalDateTime.of(2026, 10, 28, 0, 0);
        ZoneId labZone = ZoneId.of("Asia/Shanghai");
        LocalDateTime tomorrowStart = LocalDateTime.of(2026, 10, 28, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> ReservationRules.validateUtc(tomorrowStart, tomorrowStart.plusHours(1), nowUtc, LocalTime.of(8, 0), LocalTime.of(22, 0), "AVAILABLE", labZone));
        LocalDateTime daySevenStart = LocalDateTime.of(2026, 11, 4, 1, 0);
        assertDoesNotThrow(() -> ReservationRules.validateUtc(daySevenStart, daySevenStart.plusHours(1), nowUtc, LocalTime.of(8, 0), LocalTime.of(22, 0), "AVAILABLE", labZone));
        LocalDateTime dayEightStart = daySevenStart.plusDays(1);
        assertThrows(IllegalArgumentException.class, () -> ReservationRules.validateUtc(dayEightStart, dayEightStart.plusHours(1), nowUtc, LocalTime.of(8, 0), LocalTime.of(22, 0), "AVAILABLE", labZone));
    }

    @Test void approvalRechecksAvailabilityWithoutReapplyingSubmissionLeadTime() {
        LocalDateTime nowUtc = LocalDateTime.of(2026, 10, 29, 1, 0);
        LocalDateTime startsUtc = LocalDateTime.of(2026, 10, 29, 5, 0);
        assertDoesNotThrow(() -> ReservationRules.validateUtc(startsUtc, startsUtc.plusHours(1), nowUtc, LocalTime.of(8, 0), LocalTime.of(22, 0), "AVAILABLE", ZoneId.of("Asia/Shanghai"), false));
    }
}
