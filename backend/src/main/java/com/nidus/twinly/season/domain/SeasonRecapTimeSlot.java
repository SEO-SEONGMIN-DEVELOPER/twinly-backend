package com.nidus.twinly.season.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SeasonRecapTimeSlot {
    @JsonProperty("morning")   MORNING,
    @JsonProperty("lunch")     LUNCH,
    @JsonProperty("evening")   EVENING,
    @JsonProperty("lateNight") LATE_NIGHT;

    public static SeasonRecapTimeSlot of(int hour) {
        if (hour >= 6 && hour < 11) {
            return MORNING;
        }
        if (hour >= 11 && hour < 17) {
            return LUNCH;
        }
        if (hour >= 17 && hour < 22) {
            return EVENING;
        }
        return LATE_NIGHT;
    }
}
