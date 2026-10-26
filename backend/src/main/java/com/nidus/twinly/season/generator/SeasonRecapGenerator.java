package com.nidus.twinly.season.generator;

import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.SceneRepository.SceneRecapProjection;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository.PairMatchRateProjection;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.scene.StoredSceneBubbleLine;
import com.nidus.twinly.common.scene.StoredSceneLine;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.people.repository.TwinViewRepository;
import com.nidus.twinly.relationship.domain.Intimacy;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.season.domain.SeasonRecapData;
import com.nidus.twinly.season.domain.SeasonRecapPeriod;
import com.nidus.twinly.season.domain.SeasonRecapRanking;
import com.nidus.twinly.season.domain.SeasonRecapScene;
import com.nidus.twinly.season.domain.SeasonRecapSceneSummary;
import com.nidus.twinly.season.domain.SeasonRecapTimeSlot;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonParticipationRepository.RecapReceiverProjection;
import com.nidus.twinly.season.repository.SeasonRecapRepository;
import com.nidus.twinly.season.repository.SeasonRecapRepository.HourlyActionCountProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonRecapGenerator {

    private static final int TOP_SIZE = 3;
    private static final int MIN_COMPARED_COUNT = 3;
    private static final TypeReference<List<StoredSceneLine>> LINES_TYPE = new TypeReference<>() {
    };

    private final SeasonParticipationRepository seasonParticipationRepository;
    private final SeasonRecapRepository seasonRecapRepository;
    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final TwinViewRepository twinViewRepository;
    private final BalanceGameAnswerRepository balanceGameAnswerRepository;
    private final IntimacyReader intimacyReader;
    private final ObjectMapper objectMapper;

    public Map<Long, SeasonRecapData> generate(Long seasonId, SeasonRecapPeriod period) {
        List<RecapReceiverProjection> receivers = seasonParticipationRepository.findAllRecapReceivers(
                seasonId, period.endedAt(), period.kstStartedAt(), period.kstEndedAt());
        if (receivers.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> profileViewersByUserId = twinViewRepository
                .countDistinctProfileViewersByViewedAtRange(period.startedAt(), period.endedAt()).stream()
                .collect(Collectors.toMap(TwinViewRepository.ViewerCountProjection::getTargetUserId,
                        projection -> projection.getViewerCount().intValue()));
        Map<Long, Map<SeasonRecapTimeSlot, Integer>> actionCountsByUserId = actionCountsByUserId(period);
        Map<Long, List<SeasonRecapData.BestMatch>> bestMatchesByUserId = bestMatchesByUserId(seasonId, period);

        List<UserStats> stats = new ArrayList<>();
        for (RecapReceiverProjection receiver : receivers) {
            stats.add(userStats(receiver, period,
                    profileViewersByUserId.getOrDefault(receiver.getUserId(), 0),
                    actionCountsByUserId.getOrDefault(receiver.getUserId(), Map.of()),
                    bestMatchesByUserId.getOrDefault(receiver.getUserId(), List.of())));
        }

        SeasonRecapRanking dialogueRanking = ranking(stats, stat -> stat.summary.dialogueMinutes());
        SeasonRecapRanking actionRanking = ranking(stats, stat -> stat.summary.actionMinutes());
        SeasonRecapRanking moveRanking = ranking(stats, stat -> stat.summary.moveMinutes());
        SeasonRecapRanking bubblesRanking = ranking(stats, stat -> stat.summary.bubbles());
        SeasonRecapRanking talkedRanking = ranking(stats, stat -> stat.summary.talkedPartnerUserIds().size());
        SeasonRecapRanking friendsRanking = ranking(stats, UserStats::friends);
        SeasonRecapRanking profileViewersRanking = ranking(stats, UserStats::profileViewers);

        Map<Long, SeasonRecapData> dataByUserId = new LinkedHashMap<>();
        for (UserStats stat : stats) {
            dataByUserId.put(stat.userId, new SeasonRecapData(
                    period.startedAt(),
                    period.endedAt(),
                    stats.size(),
                    stat.togetherDays,
                    SeasonRecapData.Ranked.of(stat.summary.dialogueMinutes(), dialogueRanking),
                    SeasonRecapData.Ranked.of(stat.summary.actionMinutes(), actionRanking),
                    SeasonRecapData.Ranked.of(stat.summary.moveMinutes(), moveRanking),
                    SeasonRecapData.Ranked.of(stat.summary.bubbles(), bubblesRanking),
                    stat.summary.places(),
                    SeasonRecapData.Ranked.of(stat.summary.talkedPartnerUserIds().size(), talkedRanking),
                    SeasonRecapData.Ranked.of(stat.friends, friendsRanking),
                    stat.summary.mostMet(),
                    stat.closest,
                    stat.bestMatches,
                    SeasonRecapData.Ranked.of(stat.profileViewers, profileViewersRanking),
                    SeasonRecapData.ActivityType.of(stat.actionCounts)
            ));
        }

        return dataByUserId;
    }

    private UserStats userStats(RecapReceiverProjection receiver, SeasonRecapPeriod period, int profileViewers,
                                Map<SeasonRecapTimeSlot, Integer> actionCounts, List<SeasonRecapData.BestMatch> bestMatches) {
        Long userId = receiver.getUserId();
        SeasonRecapSceneSummary summary = SeasonRecapSceneSummary.of(scenes(userId, period), period);

        Map<Long, Intimacy> intimacyByPartnerUserId = intimacyReader.readAllAsOf(
                userId, List.copyOf(summary.meetingByPartnerUserId().keySet()), period.endedAt());

        int friends = (int) summary.talkedPartnerUserIds().stream()
                .filter(partnerUserId -> intimacyByPartnerUserId.getOrDefault(partnerUserId, Intimacy.ZERO).value()
                        >= RelationshipType.FRIEND.minIntimacy())
                .count();

        return new UserStats(
                userId,
                togetherDays(receiver.getParticipatedInAt(), summary.lastEndsAt(), period),
                summary,
                friends,
                closest(summary, intimacyByPartnerUserId),
                bestMatches,
                profileViewers,
                actionCounts
        );
    }

    private List<SeasonRecapScene> scenes(Long userId, SeasonRecapPeriod period) {
        List<SceneRecapProjection> rows = sceneRepository.findAllRecapByUserIdAndOverlapping(
                userId, period.kstStartedAt(), period.kstEndedAt());
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Set<Long>> partnerUserIdsBySceneId = scenePartnerRepository
                .findAllBySceneIdIn(rows.stream().map(SceneRecapProjection::getId).toList()).stream()
                .filter(partner -> !partner.getUserId().equals(userId))
                .collect(Collectors.groupingBy(ScenePartner::getSceneId,
                        Collectors.mapping(ScenePartner::getUserId, Collectors.toSet())));

        return rows.stream()
                .map(row -> new SeasonRecapScene(
                        row.getId(),
                        row.getType(),
                        row.getStartsAt(),
                        row.getEndsAt(),
                        row.getPlace(),
                        row.getPlaceCode(),
                        partnerUserIdsBySceneId.getOrDefault(row.getId(), Set.of()),
                        myBubbleCount(userId, row, period)))
                .toList();
    }

    private int myBubbleCount(Long userId, SceneRecapProjection row, SeasonRecapPeriod period) {
        if (row.getLines() == null) {
            return 0;
        }

        try {
            return (int) objectMapper.readValue(row.getLines(), LINES_TYPE).stream()
                    .filter(line -> line instanceof StoredSceneBubbleLine bubble
                            && userId.equals(bubble.userId())
                            && (bubble.occursAt() == null || period.contains(bubble.occursAt())))
                    .count();
        } catch (JacksonException e) {
            WarnLog.log(log, "리캡 집계 중 장면 대사를 읽지 못해 말풍선 수에서 뺍니다", e,
                    field("userId", userId), field("sceneId", row.getId()));
            return 0;
        }
    }

    private int togetherDays(Instant participatedInAt, LocalDateTime lastEndsAt, SeasonRecapPeriod period) {
        LocalDate from = period.clip(participatedInAt == null
                ? period.kstStartedAt()
                : KstTimes.toLocalDateTime(participatedInAt)).toLocalDate();
        LocalDate to = (lastEndsAt == null ? period.kstStartedAt() : lastEndsAt).toLocalDate();

        return (int) Math.max(1, ChronoUnit.DAYS.between(from, to) + 1);
    }

    private List<SeasonRecapData.Closest> closest(SeasonRecapSceneSummary summary, Map<Long, Intimacy> intimacyByPartnerUserId) {
        return summary.meetingByPartnerUserId().entrySet().stream()
                .map(entry -> {
                    Intimacy intimacy = intimacyByPartnerUserId.getOrDefault(entry.getKey(), Intimacy.ZERO);
                    return new ClosestCandidate(entry.getKey(), intimacy.value() - intimacy.game(), entry.getValue().days());
                })
                .filter(candidate -> candidate.twinIntimacy > 0)
                .sorted(Comparator.comparingInt(ClosestCandidate::twinIntimacy).reversed()
                        .thenComparing(Comparator.comparingInt(ClosestCandidate::days).reversed())
                        .thenComparing(ClosestCandidate::userId))
                .limit(TOP_SIZE)
                .map(candidate -> new SeasonRecapData.Closest(candidate.userId, candidate.twinIntimacy))
                .toList();
    }

    private Map<Long, Map<SeasonRecapTimeSlot, Integer>> actionCountsByUserId(SeasonRecapPeriod period) {
        Map<Long, Map<SeasonRecapTimeSlot, Integer>> countsByUserId = new HashMap<>();

        for (HourlyActionCountProjection row : seasonRecapRepository.countActionsByUserIdAndKstHour(period.startedAt(), period.endedAt())) {
            countsByUserId.computeIfAbsent(row.getUserId(), ignored -> new EnumMap<>(SeasonRecapTimeSlot.class))
                    .merge(SeasonRecapTimeSlot.of(row.getKstHour().intValue()), row.getCount().intValue(), Integer::sum);
        }

        return countsByUserId;
    }

    private Map<Long, List<SeasonRecapData.BestMatch>> bestMatchesByUserId(Long seasonId, SeasonRecapPeriod period) {
        return balanceGameAnswerRepository
                .countMatchRatesBySeasonIdAndRoundStartsAtRange(seasonId, period.startedAt(), period.endedAt()).stream()
                .filter(row -> row.getComparedCount() >= MIN_COMPARED_COUNT)
                .collect(Collectors.groupingBy(PairMatchRateProjection::getUserId, Collectors.collectingAndThen(
                        Collectors.toList(), SeasonRecapGenerator::topMatches)));
    }

    private static List<SeasonRecapData.BestMatch> topMatches(List<PairMatchRateProjection> rows) {
        return rows.stream()
                .map(row -> new SeasonRecapData.BestMatch(
                        row.getPartnerUserId(),
                        Math.round(row.getMatchedCount() * 1000.0 / row.getComparedCount()) / 10.0,
                        row.getComparedCount().intValue(),
                        row.getMatchedCount().intValue()))
                .sorted(Comparator.comparingDouble(SeasonRecapData.BestMatch::matchRate).reversed()
                        .thenComparing(Comparator.comparingInt(SeasonRecapData.BestMatch::comparedCount).reversed())
                        .thenComparing(SeasonRecapData.BestMatch::userId))
                .limit(TOP_SIZE)
                .toList();
    }

    private static SeasonRecapRanking ranking(List<UserStats> stats, ToIntFunction<UserStats> value) {
        return SeasonRecapRanking.of(stats.stream().map(value::applyAsInt).toList());
    }

    private record UserStats(
            Long userId,
            int togetherDays,
            SeasonRecapSceneSummary summary,
            int friends,
            List<SeasonRecapData.Closest> closest,
            List<SeasonRecapData.BestMatch> bestMatches,
            int profileViewers,
            Map<SeasonRecapTimeSlot, Integer> actionCounts
    ) {
    }

    private record ClosestCandidate(
            Long userId,
            int twinIntimacy,
            int days
    ) {
    }
}
