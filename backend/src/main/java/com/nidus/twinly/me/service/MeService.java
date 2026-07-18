package com.nidus.twinly.me.service;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.presign.PhotoCommitService;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.presign.PresignService;
import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.result.MeProfileEditViewResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoCommitResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoPresignResult;
import com.nidus.twinly.me.dto.result.MeWithdrawResult;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

/*
 * [멘토링 피드백 반영 완료]
 * JPA에서 업데이트된 정보가 분실되는 상황 발생 -> 그 컬럼만 바꾸게 SQL 짜기
 */

@Service
@RequiredArgsConstructor
public class MeService {

    private static final Duration WITHDRAWAL_RECOVERABLE_PERIOD = Duration.ofDays(15);

    private final PresignService presignService;
    private final PhotoCommitService photoCommitService;
    private final CloudFrontService cloudFrontService;

    private final BlindIndexHasher blindIndexHasher;

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;

    public MeProfilePhotoPresignResult profilePhotoPresign(Long userId, MeProfilePhotoPresignCommand command) {
        PhotoPresignResult presign = presignService.presignPhoto(userId, command.contentType(), PhotoType.PROFILE);

        return new MeProfilePhotoPresignResult(presign.uploadUrl(), presign.key(), presign.method(), presign.requiredHeaders(), presign.maxBytes(), presign.expiresAt());
    }

    public MeProfilePhotoCommitResult profilePhotoCommit(Long userId, MeProfilePhotoCommitCommand command) {
        if (command.key() == null || command.position() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        String photoUrl = photoCommitService.commitProfilePhoto(userId, command.key());

        PhotoPosInfo position = command.position();
        photoRepository.findByUserIdAndType(userId, PhotoType.PROFILE)
                .ifPresentOrElse(
                        photo -> photo.changePhoto(command.key(),
                                position.startPos().x(), position.startPos().y(), position.width(), position.height()),
                        () -> photoRepository.save(Photo.create(userId, PhotoType.PROFILE, command.key(),
                                position.startPos().x(), position.startPos().y(), position.width(), position.height(), Instant.now()))
                );

        return new MeProfilePhotoCommitResult(photoUrl, position);
    }

    @Transactional
    public MeWithdrawResult withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        if (user.getWithdrawalRequestedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 탈퇴 신청이 된 상태입니다.");
        }

        user.requestWithdrawal(WITHDRAWAL_RECOVERABLE_PERIOD);

        return new MeWithdrawResult(user.getWithdrawalScheduledAt());
    }

    public MeProfileEditViewResult profileEditView(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        String profilePhotoUrl = photoRepository.findByUserIdAndType(userId, PhotoType.PROFILE)
                .map(photo -> cloudFrontService.getSignedUrl(photo.getKey()))
                .orElse(null);

        return new MeProfileEditViewResult(
                user.getId(),
                user.getFamilyName(),
                user.getGivenName(),
                user.getAffiliation(),
                user.getAffiliationNumber(),
                user.getBirthDate(),
                profilePhotoUrl
        );
    }

    @Transactional
    public void profile(Long userId, MeProfileCommand command) {
        if (command.affiliation() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        user.changeAffiliation(command.affiliation(), blindIndexHasher.hash(command.affiliation()));
    }

    @Transactional
    public void restore(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        if (user.getWithdrawalRequestedAt() == null) {
            return;
        }

        if (user.getWithdrawalRequestedAt().plus(WITHDRAWAL_RECOVERABLE_PERIOD).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "복구 가능 기간이 지났습니다.");
        }

        user.cancelWithdrawal();
    }
}