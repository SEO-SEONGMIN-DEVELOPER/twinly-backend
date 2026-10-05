package com.nidus.twinly.balancegame.domain;

import com.nidus.twinly.common.time.KstTimes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class BalanceGameSchedule {

    private static final List<Integer> ROUND_START_HOURS = List.of(12, 18);

    private BalanceGameSchedule() {
    }

    public static Instant roundStartOf(LocalDateTime kstNow) {
        return KstTimes.toInstant(roundStartAt(kstNow));
    }

    public static Instant previousRoundStartOf(LocalDateTime kstNow) {
        return KstTimes.toInstant(roundStartAt(roundStartAt(kstNow).minusNanos(1)));
    }

    public static Instant nextRoundStartOf(Instant roundStart) {
        LocalDateTime start = LocalDateTime.ofInstant(roundStart, KstTimes.ZONE);

        return ROUND_START_HOURS.stream()
                .filter(hour -> hour > start.getHour())
                .findFirst()
                .map(hour -> start.toLocalDate().atTime(hour, 0))
                .map(KstTimes::toInstant)
                .orElseGet(() -> KstTimes.toInstant(start.toLocalDate().plusDays(1).atTime(ROUND_START_HOURS.getFirst(), 0)));
    }

    public static long sequenceOf(Instant roundStart) {
        LocalDateTime start = LocalDateTime.ofInstant(roundStart, KstTimes.ZONE);

        return start.toLocalDate().toEpochDay() * ROUND_START_HOURS.size() + ROUND_START_HOURS.indexOf(start.getHour());
    }

    private static LocalDateTime roundStartAt(LocalDateTime kstNow) {
        LocalDate today = kstNow.toLocalDate();

        return ROUND_START_HOURS.reversed().stream()
                .map(hour -> today.atTime(hour, 0))
                .filter(start -> !start.isAfter(kstNow))
                .findFirst()
                .orElseGet(() -> today.minusDays(1).atTime(ROUND_START_HOURS.getLast(), 0));
    }
}
