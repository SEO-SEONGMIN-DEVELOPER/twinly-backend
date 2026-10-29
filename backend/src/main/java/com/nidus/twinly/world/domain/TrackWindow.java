package com.nidus.twinly.world.domain;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public record TrackWindow(LocalDateTime from, LocalDateTime to) {

    private static final Duration LENGTH = Duration.ofHours(1);
    private static final LocalTime ROUND_START = LocalTime.of(6, 0);
    private static final LocalTime ROUND_END = LocalTime.of(4, 0);

    public static TrackWindow of(LocalDate date, String rawFrom, String rawTo) {
        LocalDateTime from = toKstLocalDateTime(rawFrom);
        LocalDateTime to = toKstLocalDateTime(rawTo);

        if (!from.toLocalTime().equals(from.toLocalTime().withMinute(0).withSecond(0).withNano(0))
                || !to.equals(from.plus(LENGTH))
                || from.isBefore(date.atTime(ROUND_START))
                || to.isAfter(date.plusDays(1).atTime(ROUND_END))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return new TrackWindow(from, to);
    }

    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        if (start.equals(end)) {
            return !start.isBefore(from) && start.isBefore(to);
        }

        return start.isBefore(to) && end.isAfter(from);
    }

    public boolean isAtOrBeforeStart(LocalDateTime at) {
        return !at.isAfter(from);
    }

    public boolean containsStrictly(LocalDateTime at) {
        return at.isAfter(from) && at.isBefore(to);
    }

    private static LocalDateTime toKstLocalDateTime(String raw) {
        try {
            return KstTimes.toLocalDateTime(OffsetDateTime.parse(raw.replace(' ', '+')).toInstant());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e);
        }
    }
}
