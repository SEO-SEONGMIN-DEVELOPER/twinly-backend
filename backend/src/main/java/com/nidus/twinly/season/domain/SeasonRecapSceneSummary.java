package com.nidus.twinly.season.domain;

import com.nidus.twinly.activity.domain.SceneType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SeasonRecapSceneSummary(
        int dialogueMinutes,
        int actionMinutes,
        int moveMinutes,
        int bubbles,
        List<SeasonRecapData.Place> places,
        Set<Long> talkedPartnerUserIds,
        Map<Long, Meeting> meetingByPartnerUserId,
        LocalDateTime lastEndsAt
) {

    private static final String HOME_PLACE_CODE = "P0258";
    private static final String HOME_PLACE_NAME = "주택가 집";
    private static final int TOP_SIZE = 3;
    private static final Comparator<SeasonRecapScene> CHRONOLOGICAL = Comparator
            .comparing(SeasonRecapScene::startsAt)
            .thenComparing(SeasonRecapScene::id);

    public record Meeting(
            int days,
            int sceneCount
    ) {
    }

    public static SeasonRecapSceneSummary of(List<SeasonRecapScene> scenes, SeasonRecapPeriod period) {
        List<SeasonRecapScene> inPeriod = scenes.stream()
                .filter(scene -> period.overlaps(scene.startsAt(), scene.endsAt()))
                .sorted(CHRONOLOGICAL)
                .toList();

        Map<SceneType, Long> secondsByType = new EnumMap<>(SceneType.class);
        Set<Long> talkedPartnerUserIds = new HashSet<>();
        Map<Long, Set<LocalDate>> daysByPartnerUserId = new HashMap<>();
        Map<Long, Integer> sceneCountByPartnerUserId = new HashMap<>();
        int bubbles = 0;
        LocalDateTime lastEndsAt = null;

        for (SeasonRecapScene scene : inPeriod) {
            secondsByType.merge(scene.type(), period.overlapSeconds(scene.startsAt(), scene.endsAt()), Long::sum);
            bubbles += scene.myBubbleCount();

            LocalDate day = period.clip(scene.startsAt()).toLocalDate();
            for (Long partnerUserId : scene.partnerUserIds()) {
                daysByPartnerUserId.computeIfAbsent(partnerUserId, ignored -> new HashSet<>()).add(day);
                sceneCountByPartnerUserId.merge(partnerUserId, 1, Integer::sum);
                if (scene.type() == SceneType.DIALOGUE) {
                    talkedPartnerUserIds.add(partnerUserId);
                }
            }

            LocalDateTime endsAt = period.clip(scene.endsAt());
            if (lastEndsAt == null || endsAt.isAfter(lastEndsAt)) {
                lastEndsAt = endsAt;
            }
        }

        Map<Long, Meeting> meetingByPartnerUserId = new HashMap<>();
        daysByPartnerUserId.forEach((partnerUserId, days) -> meetingByPartnerUserId.put(
                partnerUserId, new Meeting(days.size(), sceneCountByPartnerUserId.get(partnerUserId))));

        return new SeasonRecapSceneSummary(
                minutes(secondsByType.get(SceneType.DIALOGUE)),
                minutes(secondsByType.get(SceneType.ACTION)),
                minutes(secondsByType.get(SceneType.MOVE)),
                bubbles,
                places(inPeriod, period),
                talkedPartnerUserIds,
                meetingByPartnerUserId,
                lastEndsAt
        );
    }

    public List<SeasonRecapData.MostMet> mostMet() {
        return meetingByPartnerUserId.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Long, Meeting>>comparingInt(entry -> entry.getValue().days()).reversed()
                        .thenComparing(Comparator.<Map.Entry<Long, Meeting>>comparingInt(entry -> entry.getValue().sceneCount()).reversed())
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_SIZE)
                .map(entry -> new SeasonRecapData.MostMet(entry.getKey(), entry.getValue().days(), entry.getValue().sceneCount()))
                .toList();
    }

    private static List<SeasonRecapData.Place> places(List<SeasonRecapScene> chronological, SeasonRecapPeriod period) {
        List<Visit> visits = new ArrayList<>();
        Visit current = null;

        for (SeasonRecapScene scene : chronological) {
            if (scene.type() == SceneType.MOVE) {
                continue;
            }

            String key = placeKey(scene);
            if (key == null) {
                continue;
            }

            long seconds = period.overlapSeconds(scene.startsAt(), scene.endsAt());
            if (current != null && current.key.equals(key)) {
                current.seconds += seconds;
                current.name = nameOf(scene);
                continue;
            }

            current = new Visit(key, scene.placeCode(), nameOf(scene), isHome(scene), seconds);
            visits.add(current);
        }

        Map<String, PlaceTotal> totalByKey = new LinkedHashMap<>();
        for (Visit visit : visits) {
            if (visit.home) {
                continue;
            }

            PlaceTotal total = totalByKey.computeIfAbsent(visit.key, ignored -> new PlaceTotal(visit.placeCode));
            total.visitCount++;
            total.seconds += visit.seconds;
            total.name = visit.name;
        }

        return totalByKey.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, PlaceTotal>>comparingInt(entry -> entry.getValue().visitCount).reversed()
                        .thenComparing(Comparator.<Map.Entry<String, PlaceTotal>>comparingLong(entry -> entry.getValue().seconds).reversed())
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_SIZE)
                .map(entry -> new SeasonRecapData.Place(
                        entry.getValue().placeCode,
                        entry.getValue().name,
                        entry.getValue().visitCount,
                        minutes(entry.getValue().seconds)))
                .toList();
    }

    private static String placeKey(SeasonRecapScene scene) {
        if (scene.placeCode() != null && !scene.placeCode().isBlank()) {
            return "code:" + scene.placeCode();
        }
        if (scene.place() != null && !scene.place().isBlank()) {
            return "name:" + scene.place();
        }
        return null;
    }

    private static String nameOf(SeasonRecapScene scene) {
        return scene.place() != null && !scene.place().isBlank() ? scene.place() : scene.placeCode();
    }

    private static boolean isHome(SeasonRecapScene scene) {
        return HOME_PLACE_CODE.equals(scene.placeCode()) || HOME_PLACE_NAME.equals(scene.place());
    }

    private static int minutes(Long seconds) {
        return seconds == null ? 0 : (int) (seconds / 60);
    }

    private static final class Visit {

        private final String key;
        private final String placeCode;
        private String name;
        private final boolean home;
        private long seconds;

        private Visit(String key, String placeCode, String name, boolean home, long seconds) {
            this.key = key;
            this.placeCode = placeCode;
            this.name = name;
            this.home = home;
            this.seconds = seconds;
        }
    }

    private static final class PlaceTotal {

        private final String placeCode;
        private String name;
        private int visitCount;
        private long seconds;

        private PlaceTotal(String placeCode) {
            this.placeCode = placeCode;
        }
    }
}
