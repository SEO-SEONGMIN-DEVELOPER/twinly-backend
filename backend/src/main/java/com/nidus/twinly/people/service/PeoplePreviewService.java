package com.nidus.twinly.people.service;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.people.dto.result.PeoplePreviewResult;
import com.nidus.twinly.people.dto.result.PeoplePreviewsResult;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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

    private final CurrentSeasonReader currentSeasonReader;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final ProfileThumbnailService profileThumbnailService;
    private final CloudFrontService cloudFrontService;
    private final BlindIndexHasher blindIndexHasher;

    public PeoplePreviewsResult previews(Long userId) {
        Long seasonId = currentSeasonReader.read().getId();

        User me = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDate today = KstTimes.today();
        List<PreviewCandidateProjection> candidates = seasonParticipationRepository.findAllPreviewCandidates(
                userId, seasonId, KstTimes.toInstant(today.atStartOfDay()));

        Map<Long, String> refByUserId = candidates.stream()
                .collect(Collectors.toMap(PreviewCandidateProjection::getUserId, candidate -> ref(userId, today, candidate.getUserId())));
        Comparator<Long> dailyOrder = Comparator.<Long, String>comparing(refByUserId::get)
                .thenComparing(Comparator.naturalOrder());

        List<Long> pickedUserIds = pick(candidates, me.getGender(), dailyOrder);
        if (pickedUserIds.isEmpty()) {
            return new PeoplePreviewsResult(List.of());
        }

        Map<Long, User> userById = userRepository.findAllById(pickedUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> photoUrlByUserId = photoUrlByUserId(pickedUserIds);

        List<PeoplePreviewResult> previews = pickedUserIds.stream()
                .filter(userById::containsKey)
                .map(pickedUserId -> {
                    User user = userById.get(pickedUserId);

                    return new PeoplePreviewResult(
                            refByUserId.get(pickedUserId),
                            user.getGender(),
                            user.shortOrganization(),
                            user.shortBirthYear(),
                            photoUrlByUserId.get(pickedUserId)
                    );
                })
                .toList();

        return new PeoplePreviewsResult(previews);
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

    private Map<Long, String> photoUrlByUserId(List<Long> userIds) {
        Map<Long, String> photoUrlByUserId = new HashMap<>();

        for (Photo photo : photoRepository.findAllByUserIdInAndType(userIds, PhotoType.PROFILE)) {
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
}
