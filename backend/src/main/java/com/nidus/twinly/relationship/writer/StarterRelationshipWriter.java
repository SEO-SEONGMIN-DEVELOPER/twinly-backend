package com.nidus.twinly.relationship.writer;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonParticipationRepository.StarterCandidateProjection;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class StarterRelationshipWriter {

    private static final int STARTER_COUNT = 2;
    private static final Duration STARTER_REVEAL_INTERVAL = Duration.ofDays(1);
    private static final int STARTER_INTIMACY = 0;
    private static final String STARTER_PARTNER_MODEL = "아직 알게된 점이 없습니다.";

    private final UserRepository userRepository;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final RelationshipRepository relationshipRepository;
    private final EncounterRepository encounterRepository;
    private final AppNotificationScheduleRepository appNotificationScheduleRepository;
    private final CurrentSeasonReader currentSeasonReader;

    @Transactional
    public void connect(Long userId, Long seasonId, Instant participatedAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDate participatedDate = KstTimes.toLocalDateTime(participatedAt).toLocalDate();
        if (user.getPoolNumber() == null
                || relationshipRepository.existsInSeasonByUserId(userId, seasonId, currentSeasonReader.startDateOf(participatedDate))) {
            return;
        }

        List<Long> partnerUserIds = pick(
                seasonParticipationRepository.findAllStarterCandidates(userId, seasonId, user.getPoolNumber()), user.getGender());

        if (partnerUserIds.isEmpty()) {
            InfoLog.log(log, "같은 풀에 시작 인연 후보가 없어 연결하지 않았습니다.", field("userId", userId), field("poolNumber", user.getPoolNumber()));
            return;
        }

        LocalDate date = participatedDate.minusDays(1);

        List<Relationship> starters = IntStream.range(0, partnerUserIds.size())
                .boxed()
                .flatMap(index -> {
                    Long partnerUserId = partnerUserIds.get(index);
                    Instant revealedAt = participatedAt.plus(STARTER_REVEAL_INTERVAL.multipliedBy(index));

                    return Stream.of(
                            starter(userId, partnerUserId, date, revealedAt, seasonId),
                            starter(partnerUserId, userId, date, revealedAt, seasonId));
                })
                .toList();

        relationshipRepository.saveAll(starters);

        starters.forEach(starter -> appNotificationScheduleRepository.insertIfAbsent(
                starter.getUserId(), starter.getPartnerUserId(), AppNotificationScheduleType.STARTER_REVEAL.name(), starter.getDate(), starter.getIntimacyAsOf()));

        partnerUserIds.forEach(partnerUserId -> encounterRepository.upsert(
                Math.min(userId, partnerUserId), Math.max(userId, partnerUserId)));

        InfoLog.log(log, "시즌 첫 참가 유저에게 같은 풀의 시작 인연을 연결했습니다.", field("userId", userId), field("partnerUserIds", partnerUserIds));
    }

    private List<Long> pick(List<StarterCandidateProjection> candidates, Gender myGender) {
        Map<Boolean, List<Long>> userIdsByOpposite = candidates.stream()
                .collect(Collectors.partitioningBy(
                        candidate -> myGender != null && candidate.getGender() != null && candidate.getGender() != myGender,
                        Collectors.mapping(StarterCandidateProjection::getUserId, Collectors.toList())));
        List<Long> oppositeUserIds = new ArrayList<>(userIdsByOpposite.get(true));
        List<Long> otherUserIds = new ArrayList<>(userIdsByOpposite.get(false));
        Collections.shuffle(oppositeUserIds);
        Collections.shuffle(otherUserIds);

        return Stream.concat(oppositeUserIds.stream(), otherUserIds.stream())
                .limit(STARTER_COUNT)
                .toList();
    }

    private Relationship starter(Long userId, Long partnerUserId, LocalDate date, Instant revealedAt, Long seasonId) {
        return Relationship.create(userId, date, Relationship.STARTER_VERSION, partnerUserId, STARTER_INTIMACY, STARTER_PARTNER_MODEL, KstTimes.toLocalDateTime(revealedAt), revealedAt, seasonId);
    }
}
