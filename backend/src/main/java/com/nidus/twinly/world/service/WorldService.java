package com.nidus.twinly.world.service;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository.PartnerFirstUpdateProjection;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.world.domain.TrackWindow;
import com.nidus.twinly.world.dto.result.WorldSectorsResult;
import com.nidus.twinly.world.entity.WorldTrack;
import com.nidus.twinly.world.reader.WorldTrackSlice;
import com.nidus.twinly.world.reader.WorldTrackWindowReader;
import com.nidus.twinly.world.repository.WorldTrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorldService {

    public static final String ANONYMOUS_DISPLAY_NAME = "누군가의 트윈";
    private static final LocalTime ROUND_START = LocalTime.of(6, 0);
    private static final String NARRATION = "narr";
    private static final String EXTRA_REF_PREFIX = "extra:";

    private final WorldTrackRepository worldTrackRepository;
    private final UserRepository userRepository;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final CurrentSeasonReader currentSeasonReader;
    private final RelationshipRepository relationshipRepository;
    private final BlockRepository blockRepository;
    private final SceneRepository sceneRepository;
    private final WorldTrackWindowReader worldTrackWindowReader;
    private final WorldTrackMerger worldTrackMerger;
    private final WorldTrackPublisher worldTrackPublisher;
    private final ObjectMapper objectMapper;

    public WorldSectorsResult sectors(Long userId, String sectorCode, LocalDate date, String rawFrom, String rawTo) {
        TrackWindow window = TrackWindow.of(date, rawFrom, rawTo);

        if (seasonParticipationRepository.findByUserIdAndSeasonId(userId, currentSeasonReader.read().getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.WORLD_PARTICIPATION_REQUIRED);
        }

        User viewer = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (viewer.getPoolNumber() == null) {
            throw new BusinessException(ErrorCode.WORLD_TRACK_NOT_FOUND);
        }

        WorldTrackSlice publicTrack = readTrack(viewer.getPoolNumber(), date, sectorCode, WorldTrack.PUBLIC_VIEWER_USER_ID, window);
        WorldTrackSlice personalTrack = readTrack(viewer.getPoolNumber(), date, sectorCode, userId, window);
        if (publicTrack == null && personalTrack == null) {
            throw new BusinessException(ErrorCode.WORLD_TRACK_NOT_FOUND);
        }

        String viewerRef = toUserRef(userId);
        Set<String> speakerRefs = publicTrack != null && personalTrack != null ? speakerExtraRefs(userId, date) : Set.of();
        WorldTrackView view = worldTrackMerger.merge(publicTrack, personalTrack, viewerRef, speakerRefs, window);

        Map<Long, WorldActorProfile> profileByUserId = profiles(viewer, userIdsOf(view), date);
        worldTrackPublisher.publish(view,
                ref -> Optional.ofNullable(toUserId(ref)).map(profileByUserId::get).orElse(anonymousProfile()),
                ref -> ref);

        return new WorldSectorsResult(
                view.poolNumber(),
                date,
                sectorCode,
                view.layoutVersion(),
                OffsetDateTime.now(KstTimes.ZONE),
                viewerRef,
                view.actors(),
                view.talks(),
                view.gestures(),
                view.objects(),
                view.events(),
                view.items()
        );
    }

    public WorldTrackSlice readTrack(Integer poolNumber, LocalDate date, String sectorCode, long viewerUserId, TrackWindow window) {
        return worldTrackRepository.findByPoolNumberAndDateAndSectorCodeAndViewerUserId(poolNumber, date, sectorCode, viewerUserId)
                .map(track -> worldTrackWindowReader.read(track.getBody(), window))
                .orElse(null);
    }

    public static Set<Long> userIdsOf(WorldTrackView view) {
        return view.actors().stream()
                .map(actor -> toUserId(actor.path("ref").asString()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static WorldActorProfile anonymousProfile() {
        return new WorldActorProfile(ANONYMOUS_DISPLAY_NAME, null);
    }

    public static boolean isWithdrawn(User user) {
        return user.getWithdrawalRequestedAt() != null || user.getDeletedAt() != null;
    }

    public static String genderOf(User user) {
        return user.getGender() == null ? null : user.getGender().name().toLowerCase(Locale.ROOT);
    }

    public static String toUserRef(Long userId) {
        return WorldTrackPublisher.USER_REF_PREFIX + userId;
    }

    public static Long toUserId(String ref) {
        if (ref == null || !ref.startsWith(WorldTrackPublisher.USER_REF_PREFIX)) {
            return null;
        }

        try {
            return Long.valueOf(ref.substring(WorldTrackPublisher.USER_REF_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Long, WorldActorProfile> profiles(User viewer, Set<Long> userIds, LocalDate date) {
        LocalDateTime roundStart = date.atTime(ROUND_START);
        Set<Long> relationshipUserIds = relationshipRepository.findAllPartnerFirstUpdatesByUserId(
                        viewer.getId(), currentSeasonReader.scopeOf(date).seasonId(), currentSeasonReader.scopeOf(date).startDate()).stream()
                .filter(partner -> partner.getFirstUpdateTime().isBefore(roundStart))
                .map(PartnerFirstUpdateProjection::getPartnerUserId)
                .collect(Collectors.toSet());

        Set<Long> blockedUserIds = new HashSet<>();
        blockRepository.findAllByUserId(viewer.getId()).stream().map(Block::getBlockedUserId).forEach(blockedUserIds::add);
        blockRepository.findAllByUserIdInAndBlockedUserId(List.copyOf(userIds), viewer.getId()).stream().map(Block::getUserId).forEach(blockedUserIds::add);

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> profile(user, viewer, relationshipUserIds, blockedUserIds), (a, b) -> a));
    }

    private WorldActorProfile profile(User user, User viewer, Set<Long> relationshipUserIds, Set<Long> blockedUserIds) {
        if (isWithdrawn(user)) {
            return anonymousProfile();
        }

        boolean named = user.getId().equals(viewer.getId())
                || (relationshipUserIds.contains(user.getId()) && !blockedUserIds.contains(user.getId()));
        String nickname = user.displayNickname();
        String displayName = named && nickname != null && !nickname.isBlank() ? nickname : ANONYMOUS_DISPLAY_NAME;

        return new WorldActorProfile(displayName, genderOf(user));
    }

    private Set<String> speakerExtraRefs(Long userId, LocalDate date) {
        Set<String> refs = new HashSet<>();
        for (Scene scene : sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(userId, date)) {
            if (scene.getLines() == null) {
                continue;
            }

            try {
                for (JsonNode line : objectMapper.readTree(scene.getLines())) {
                    String speaker = line.path("speaker").asString();
                    if (NARRATION.equals(line.path("t").asString()) && speaker.startsWith(EXTRA_REF_PREFIX)) {
                        refs.add(speaker);
                    }
                }
            } catch (JacksonException e) {
                WarnLog.log(log, "씬 대사를 읽지 못해 말 건 엑스트라에서 뺍니다.", e, field("sceneId", scene.getId()));
            }
        }
        return refs;
    }
}
