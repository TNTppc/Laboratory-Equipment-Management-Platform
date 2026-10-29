package cn.labmanage.reservation;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class ReservationRules {
    private ReservationRules() {}

    public static boolean overlaps(LocalDateTime existingStart, LocalDateTime existingEnd, LocalDateTime requestedStart, LocalDateTime requestedEnd) {
        return existingStart.isBefore(requestedEnd) && existingEnd.isAfter(requestedStart);
    }

    public static void validate(LocalDateTime start, LocalDateTime end, LocalDateTime now, LocalTime open, LocalTime close, String equipmentStatus) {
        if (start == null || end == null || !start.isBefore(end)) throw new IllegalArgumentException("预约开始时间必须早于结束时间");
        if (!start.isAfter(now)) throw new IllegalArgumentException("不能预约过去的时间");
        if (!"AVAILABLE".equals(equipmentStatus)) throw new IllegalArgumentException("设备当前不可预约");
        if (!start.toLocalDate().equals(end.toLocalDate()) || start.toLocalTime().isBefore(open) || end.toLocalTime().isAfter(close)) {
            throw new IllegalArgumentException("预约时间不在实验室开放时间内");
        }
    }

    public static void validateUtc(LocalDateTime startUtc, LocalDateTime endUtc, LocalDateTime nowUtc, LocalTime open, LocalTime close, String equipmentStatus, ZoneId laboratoryZone) {
        if (startUtc == null || endUtc == null || !startUtc.isBefore(endUtc)) throw new IllegalArgumentException("预约开始时间必须早于结束时间");
        if (!startUtc.isAfter(nowUtc)) throw new IllegalArgumentException("不能预约过去的时间");
        if (!"AVAILABLE".equals(equipmentStatus)) throw new IllegalArgumentException("设备当前不可预约");
        LocalDateTime localStart = startUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(laboratoryZone).toLocalDateTime();
        LocalDateTime localEnd = endUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(laboratoryZone).toLocalDateTime();
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate()) || localStart.toLocalTime().isBefore(open) || localEnd.toLocalTime().isAfter(close)) {
            throw new IllegalArgumentException("预约时间不在实验室开放时间内");
        }
    }
}
