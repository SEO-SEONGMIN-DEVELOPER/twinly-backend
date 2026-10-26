package com.nidus.twinly.season.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record SeasonRecapData(
        Instant startedAt,
        Instant endedAt,
        int population,
        int togetherDays,
        Ranked dialogueMinutes,
        Ranked actionMinutes,
        Ranked moveMinutes,
        Ranked bubbles,
        List<Place> places,
        Ranked talked,
        Ranked friends,
        List<MostMet> mostMet,
        List<Closest> closest,
        List<BestMatch> bestMatches,
        Ranked profileViewers,
        ActivityType activityType
) {

    public record Ranked(
            int value,
            Integer topPercent
    ) {

        public static Ranked of(int value, SeasonRecapRanking ranking) {
            return new Ranked(value, ranking.topPercentOf(value));
        }
    }

    public record Place(
            String placeCode,
            String name,
            int visitCount,
            int minutes
    ) {
    }

    public record MostMet(
            Long userId,
            int days,
            int sceneCount
    ) {
    }

    public record Closest(
            Long userId,
            int twinIntimacy
    ) {
    }

    public record BestMatch(
            Long userId,
            double matchRate,
            int comparedCount,
            int matchedCount
    ) {
    }

    public record ActivityType(
            SeasonRecapTimeSlot type,
            int morning,
            int lunch,
            int evening,
            int lateNight
    ) {

        private static final int MIN_TOTAL = 10;

        public static ActivityType of(Map<SeasonRecapTimeSlot, Integer> countBySlot) {
            int morning = countBySlot.getOrDefault(SeasonRecapTimeSlot.MORNING, 0);
            int lunch = countBySlot.getOrDefault(SeasonRecapTimeSlot.LUNCH, 0);
            int evening = countBySlot.getOrDefault(SeasonRecapTimeSlot.EVENING, 0);
            int lateNight = countBySlot.getOrDefault(SeasonRecapTimeSlot.LATE_NIGHT, 0);

            if (morning + lunch + evening + lateNight < MIN_TOTAL) {
                return null;
            }

            SeasonRecapTimeSlot type = List.of(SeasonRecapTimeSlot.values()).stream()
                    .max(Comparator.<SeasonRecapTimeSlot>comparingInt(slot -> countBySlot.getOrDefault(slot, 0))
                            .thenComparing(Comparator.reverseOrder()))
                    .orElseThrow();

            return new ActivityType(type, morning, lunch, evening, lateNight);
        }
    }
}
