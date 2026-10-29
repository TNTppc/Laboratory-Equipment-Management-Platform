package cn.labmanage;

import cn.labmanage.common.DateTimeValues;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateTimeValuesTest {
    @Test void readsJdbcLocalDateTimeAndTimestampValues() {
        LocalDateTime value = LocalDateTime.of(2026, 10, 29, 6, 49, 48);
        assertEquals(value, DateTimeValues.toLocalDateTime(value));
        assertEquals(value, DateTimeValues.toLocalDateTime(Timestamp.valueOf(value)));
        assertEquals(value, DateTimeValues.toLocalDateTime("2026-10-29 06:49:48"));
    }
}
