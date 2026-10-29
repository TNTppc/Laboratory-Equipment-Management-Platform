package cn.labmanage;

import cn.labmanage.reservation.ReservationRules;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
}
