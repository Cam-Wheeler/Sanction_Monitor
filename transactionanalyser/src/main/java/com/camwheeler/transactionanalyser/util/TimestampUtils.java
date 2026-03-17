package com.camwheeler.transactionanalyser.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

public final class TimestampUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TimestampUtils.class);

    private TimestampUtils() {}

    // Parses transaction date and time strings into epoch millis (UTC).
    // Falls back to current time if parsing fails.
    public static long toEpochMillis(String date, String time) {
        try {
            LocalDateTime dateTime = LocalDateTime.of(
                    LocalDate.parse(date),
                    LocalTime.parse(time)
            );
            return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            LOG.warn("Failed to parse date='{}' time='{}', falling back to current time", date, time, e);
            return System.currentTimeMillis();
        }
    }
}
