package com.nidus.twinly.season.domain;

import java.time.LocalDate;

public record SeasonScope(
        Long seasonId,
        LocalDate startDate
) {

    public static final SeasonScope UNBOUNDED = new SeasonScope(null, LocalDate.EPOCH);

    public boolean includes(Long rowSeasonId, LocalDate rowDate) {
        return rowSeasonId != null ? rowSeasonId.equals(seasonId) : !rowDate.isBefore(startDate);
    }
}
