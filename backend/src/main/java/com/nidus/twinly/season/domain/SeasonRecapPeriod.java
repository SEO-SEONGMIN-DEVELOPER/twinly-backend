package com.nidus.twinly.season.domain;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.season.entity.Season;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

public record SeasonRecapPeriod(
        Instant startedAt,
        Instant endedAt
) {

    public static SeasonRecapPeriod of(Season season, Season nextSeason) {
        Instant resetAt = nextSeason.getActivatedAt();
        Instant endedAt = resetAt.isBefore(season.getEndedAt()) ? resetAt : season.getEndedAt();

        return new SeasonRecapPeriod(season.getStartedAt(), endedAt);
    }

    public LocalDateTime kstStartedAt() {
        return KstTimes.toLocalDateTime(startedAt);
    }

    public LocalDateTime kstEndedAt() {
        return KstTimes.toLocalDateTime(endedAt);
    }

    public boolean overlaps(LocalDateTime startsAt, LocalDateTime endsAt) {
        return startsAt.isBefore(kstEndedAt()) && endsAt.isAfter(kstStartedAt());
    }

    public boolean contains(LocalDateTime at) {
        return !at.isBefore(kstStartedAt()) && at.isBefore(kstEndedAt());
    }

    public long overlapSeconds(LocalDateTime startsAt, LocalDateTime endsAt) {
        LocalDateTime from = clip(startsAt);
        LocalDateTime to = endsAt.isAfter(kstEndedAt()) ? kstEndedAt() : endsAt;

        return Math.max(0, Duration.between(from, to).getSeconds());
    }

    public LocalDateTime clip(LocalDateTime at) {
        LocalDateTime from = at.isBefore(kstStartedAt()) ? kstStartedAt() : at;

        return from.isAfter(kstEndedAt()) ? kstEndedAt() : from;
    }
}
