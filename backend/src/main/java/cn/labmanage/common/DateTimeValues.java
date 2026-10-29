package cn.labmanage.common;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class DateTimeValues {
    private DateTimeValues() {}

    public static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.util.Date date) return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }
}
