package com.nidus.twinly.people.service;

import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.people.dto.result.*;
import com.nidus.twinly.people.entity.Encounter;
import com.nidus.twinly.people.entity.EncounterPreference;
import com.nidus.twinly.people.repository.EncounterPreferenceRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeopleService {

    private static final int DEFAULT_LIMIT = 20;

    private final RelationshipRepository relationshipRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final EncounterRepository encounterRepository;
    private final EncounterPreferenceRepository encounterPreferenceRepository;
    private final DisclosureAgreementRepository disclosureAgreementRepository;
    private final BlockRepository blockRepository;

    private final CloudFrontService cloudFrontService;

    public PeopleResult people(Long userId, Long cursor, Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;

        List<Long> fetched = relationshipRepository.findPartnerUserIdsByUserId(userId, cursor, effectiveLimit + 1);

        boolean hasMore = fetched.size() > effectiveLimit;
        List<Long> partnerUserIds = hasMore ? fetched.subList(0, effectiveLimit) : fetched;

        if (partnerUserIds.isEmpty()) {
            return new PeopleResult(List.of(), new PeoplePageResult(null, false));
        }

        Map<Long, User> userByPartnerUserId = userRepository.findAllById(partnerUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, String> photoUrlByPartnerUserId = photoRepository.findAllByUserIdInAndType(partnerUserIds, PhotoType.PROFILE).stream()
                .collect(Collectors.toMap(Photo::getUserId, photo -> cloudFrontService.getSignedUrl(photo.getKey())));

        Map<Long, Integer> intimacyByPartnerUserId = relationshipRepository.findLatestByUserIdAndPartnerUserIdIn(userId, partnerUserIds).stream()
                .collect(Collectors.toMap(Relationship::getPartnerUserId, Relationship::getIntimacy));

        List<Match> matches = matchRepository.findAllByUserIdAndPartnerUserIdIn(userId, partnerUserIds);
        Map<Long, Long> matchIdByPartnerUserId = matches.stream()
                .collect(Collectors.toMap(match -> partnerUserIdOf(match.getUserAId(), match.getUserBId(), userId), Match::getId));
        Map<Long, Long> roomIdByMatchId = chatRoomRepository.findAllByMatchIdIn(matches.stream().map(Match::getId).toList()).stream()
                .filter(room -> room.getClosedAt() == null)
                .collect(Collectors.toMap(ChatRoom::getMatchId, ChatRoom::getId));

        Map<Long, Integer> sceneCountByPartnerUserId = scenePartnerRepository.countScenesByUserIdAndPartnerUserIdIn(userId, partnerUserIds).stream()
                .collect(Collectors.toMap(ScenePartnerRepository.SceneCountProjection::getPartnerUserId, projection -> projection.getCount().intValue()));

        List<Encounter> encounters = encounterRepository.findAllByUserIdAndPartnerUserIdIn(userId, partnerUserIds);
        Map<Long, Long> encounterIdByPartnerUserId = encounters.stream()
                .collect(Collectors.toMap(encounter -> partnerUserIdOf(encounter.getUserAId(), encounter.getUserBId(), userId), Encounter::getId));
        Map<Long, Boolean> favoritedByEncounterId = encounterPreferenceRepository.findAllByEncounterIdInAndUserId(
                        encounters.stream().map(Encounter::getId).toList(), userId).stream()
                .collect(Collectors.toMap(EncounterPreference::getEncounterId, EncounterPreference::getIsFavorited));

        List<PeopleItemResult> people = partnerUserIds.stream()
                .map(partnerUserId -> {
                    User user = userByPartnerUserId.get(partnerUserId);
                    Integer intimacy = intimacyByPartnerUserId.getOrDefault(partnerUserId, 0);

                    Long matchId = matchIdByPartnerUserId.get(partnerUserId);
                    Long chatRoomId = matchId != null ? roomIdByMatchId.get(matchId) : null;

                    Long encounterId = encounterIdByPartnerUserId.get(partnerUserId);
                    boolean isFavorited = encounterId != null && Boolean.TRUE.equals(favoritedByEncounterId.get(encounterId));

                    return new PeopleItemResult(
                            partnerUserId,
                            user != null ? user.getFamilyName() + user.getGivenName() : null,
                            photoUrlByPartnerUserId.get(partnerUserId),
                            user != null ? user.getAvatarPaletteColor() : null,
                            intimacy,
                            RelationshipType.fromIntimacy(intimacy),
                            RelationshipSpecificType.fromIntimacy(intimacy),
                            sceneCountByPartnerUserId.getOrDefault(partnerUserId, 0),
                            chatRoomId,
                            isFavorited,
                            false
                    );
                })
                .toList();

        Long nextCursor = hasMore ? partnerUserIds.get(partnerUserIds.size() - 1) : null;

        return new PeopleResult(people, new PeoplePageResult(nextCursor, hasMore));
    }

    public PeopleProfileResult profile(Long userId, Long partnerUserId) {
        User partner = userRepository.findById(partnerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        int intimacy = relationshipRepository.findLatestByUserIdAndPartnerUserId(userId, partnerUserId)
                .map(Relationship::getIntimacy)
                .orElse(0);

        boolean isFavorited = encounterRepository.findByUserAIdAndUserBId(Math.min(userId, partnerUserId), Math.max(userId, partnerUserId))
                .flatMap(encounter -> encounterPreferenceRepository.findByEncounterIdAndUserId(encounter.getId(), userId))
                .map(EncounterPreference::getIsFavorited)
                .orElse(false);

        boolean isBlocked = blockRepository.existsByUserIdAndBlockedUserId(userId, partnerUserId);

        String profilePhotoUrl = photoRepository.findByUserIdAndType(partnerUserId, PhotoType.PROFILE)
                .map(photo -> cloudFrontService.getSignedUrl(photo.getKey()))
                .orElse(null);

        Set<DisclosureField> disclosedFields = disclosureAgreementRepository.findAllByUserId(partnerUserId).stream()
                .map(DisclosureAgreement::getField)
                .collect(Collectors.toSet());

        PeopleProfileDisclosedFieldsResult disclosed = new PeopleProfileDisclosedFieldsResult(
                disclosedFields.contains(DisclosureField.AFFILIATION) ? partner.getAffiliation() : null,
                disclosedFields.contains(DisclosureField.BIRTH_DATE) ? partner.getBirthDate() : null
        );

        return new PeopleProfileResult(
                partnerUserId,
                partner.getFamilyName() + partner.getGivenName(),
                profilePhotoUrl,
                partner.getAvatarPaletteColor(),
                intimacy,
                RelationshipType.fromIntimacy(intimacy),
                RelationshipSpecificType.fromIntimacy(intimacy),
                isFavorited,
                false,
                disclosed,
                partner.getDeletedAt() != null,
                isBlocked
        );
    }

    private Long partnerUserIdOf(Long userAId, Long userBId, Long userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }
}
