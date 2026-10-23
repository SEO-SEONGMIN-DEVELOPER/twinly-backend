package com.nidus.twinly.people.service;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.people.dto.result.PeoplePreviewResult;
import com.nidus.twinly.people.dto.result.PeoplePreviewV2PhotoResult;
import com.nidus.twinly.people.dto.result.PeoplePreviewV2Result;
import com.nidus.twinly.people.dto.result.PeoplePreviewsResult;
import com.nidus.twinly.people.dto.result.PeoplePreviewsV2Result;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonParticipationRepository.PreviewCandidateProjection;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PeoplePreviewService {

    private static final int MAX_PREVIEWS = 10;
    private static final int MAX_OPPOSITE_GENDER_PREVIEWS = 7;
    private static final int REF_LENGTH = 12;
    private static final String REF_DOMAIN = "people-preview";
    private static final String DISCLOSURE_POLICY_IDENTIFIER = "thirdPartyRealIdentityDisclosure";
    private static final String UNMASKED_PREVIEW_MINIMUM_VERSION = "1.2";

    private final CurrentSeasonReader currentSeasonReader;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final ProfileThumbnailService profileThumbnailService;
    private final CloudFrontService cloudFrontService;
    private final BlindIndexHasher blindIndexHasher;
    private final ConsentReader consentReader;

    public PeoplePreviewsResult previews(Long userId) {
        List<PreviewTarget> targets = targets(userId, currentSeasonReader.read().getId());
        if (targets.isEmpty()) {
            return new PeoplePreviewsResult(List.of());
        }

        Map<Long, String> maskedPhotoUrlByUserId = maskedPhotoUrlByUserId(profilePhotoByUserId(userIdsOf(targets)).values());

        List<PeoplePreviewResult> previews = targets.stream()
                .map(target -> new PeoplePreviewResult(
                        target.ref(),
                        target.user().getGender(),
                        target.user().shortOrganization(),
                        target.user().shortBirthYear(),
                        maskedPhotoUrlByUserId.get(target.user().getId())
                ))
                .toList();

        return new PeoplePreviewsResult(previews);
    }

    public PeoplePreviewsV2Result previewsV2(Long userId) {
        Long seasonId = currentSeasonReader.read().getId();

        List<PreviewTarget> targets = targets(userId, seasonId);
        if (targets.isEmpty()) {
            return new PeoplePreviewsV2Result(List.of());
        }

        List<Long> targetUserIds = userIdsOf(targets);
        Set<Long> unmaskedUserIds = seasonParticipationRepository.existsByUserIdAndSeasonId(userId, seasonId)
                ? consentReader.filterAgreedAtLeast(targetUserIds, DISCLOSURE_POLICY_IDENTIFIER, UNMASKED_PREVIEW_MINIMUM_VERSION)
                : Set.of();

        Map<Long, Photo> photoByUserId = profilePhotoByUserId(targetUserIds);
        Map<Long, String> maskedPhotoUrlByUserId = maskedPhotoUrlByUserId(photoByUserId.values().stream()
                .filter(photo -> !unmaskedUserIds.contains(photo.getUserId()))
                .toList());

        List<PeoplePreviewV2Result> previews = targets.stream()
                .map(target -> {
                    User user = target.user();
                    boolean unmasked = unmaskedUserIds.contains(user.getId());
                    Photo photo = photoByUserId.get(user.getId());

                    return new PeoplePreviewV2Result(
                            target.ref(),
                            user.getGender(),
                            user.shortOrganization(),
                            user.shortBirthYear(),
                            unmasked ? user.getNickname() : null,
                            unmasked && photo != null
                                    ? new PeoplePreviewV2PhotoResult(cloudFrontService.getSignedUrl(photo.getKey()), photo.position())
                                    : null,
                            unmasked ? null : maskedPhotoUrlByUserId.get(user.getId())
                    );
                })
                .toList();

        return new PeoplePreviewsV2Result(previews);
    }

    private List<PreviewTarget> targets(Long userId, Long seasonId) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDate today = KstTimes.today();
        List<PreviewCandidateProjection> candidates = seasonParticipationRepository.findAllPreviewCandidates(
                userId, seasonId, KstTimes.toInstant(today.atStartOfDay()), DISCLOSURE_POLICY_IDENTIFIER);

        Map<Long, String> refByUserId = candidates.stream()
                .collect(Collectors.toMap(PreviewCandidateProjection::getUserId, candidate -> ref(userId, today, candidate.getUserId())));
        Comparator<Long> dailyOrder = Comparator.<Long, String>comparing(refByUserId::get)
                .thenComparing(Comparator.naturalOrder());

        List<Long> pickedUserIds = pick(candidates, me.getGender(), dailyOrder);
        if (pickedUserIds.isEmpty()) {
            return List.of();
        }

        Map<Long, User> userById = userRepository.findAllById(pickedUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return pickedUserIds.stream()
                .filter(userById::containsKey)
                .map(pickedUserId -> new PreviewTarget(refByUserId.get(pickedUserId), userById.get(pickedUserId)))
                .toList();
    }

    private List<Long> pick(List<PreviewCandidateProjection> candidates, Gender myGender, Comparator<Long> dailyOrder) {
        if (myGender == null) {
            return candidates.stream()
                    .map(PreviewCandidateProjection::getUserId)
                    .sorted(dailyOrder)
                    .limit(MAX_PREVIEWS)
                    .toList();
        }

        Gender oppositeGender = myGender == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        Map<Boolean, List<Long>> userIdsByOpposite = candidates.stream()
                .collect(Collectors.partitioningBy(
                        candidate -> candidate.getGender() == oppositeGender,
                        Collectors.mapping(PreviewCandidateProjection::getUserId, Collectors.toList())));
        List<Long> oppositeUserIds = userIdsByOpposite.get(true);
        List<Long> otherUserIds = userIdsByOpposite.get(false);

        int otherCount = Math.min(otherUserIds.size(), MAX_PREVIEWS - Math.min(oppositeUserIds.size(), MAX_OPPOSITE_GENDER_PREVIEWS));
        int oppositeCount = Math.min(oppositeUserIds.size(), MAX_PREVIEWS - otherCount);

        return Stream.concat(
                        oppositeUserIds.stream().sorted(dailyOrder).limit(oppositeCount),
                        otherUserIds.stream().sorted(dailyOrder).limit(otherCount))
                .sorted(dailyOrder)
                .toList();
    }

    private String ref(Long userId, LocalDate date, Long candidateUserId) {
        byte[] hash = Base64.getDecoder().decode(blindIndexHasher.hash("%s:%d:%s:%d".formatted(REF_DOMAIN, userId, date, candidateUserId)));
        return HexFormat.of().formatHex(hash).substring(0, REF_LENGTH);
    }

    private List<Long> userIdsOf(List<PreviewTarget> targets) {
        return targets.stream().map(target -> target.user().getId()).toList();
    }

    private Map<Long, Photo> profilePhotoByUserId(List<Long> userIds) {
        return photoRepository.findAllByUserIdInAndType(userIds, PhotoType.PROFILE).stream()
                .collect(Collectors.toMap(Photo::getUserId, Function.identity()));
    }

    private Map<Long, String> maskedPhotoUrlByUserId(Collection<Photo> photos) {
        Map<Long, String> photoUrlByUserId = new HashMap<>();

        for (Photo photo : photos) {
            String previewKey = photo.getPreviewKey() != null ? photo.getPreviewKey() : createPreview(photo);
            if (previewKey != null) {
                photoUrlByUserId.put(photo.getUserId(), cloudFrontService.getSignedUrl(previewKey));
            }
        }

        return photoUrlByUserId;
    }

    private String createPreview(Photo photo) {
        String previewKey = profileThumbnailService.generatePreview(photo.getKey(), photo.position());
        if (previewKey == null) {
            return null;
        }

        return photoRepository.assignPreviewKey(photo.getId(), photo.getKey(), previewKey) == 1 ? previewKey : null;
    }

    private record PreviewTarget(String ref, User user) {
    }
}
