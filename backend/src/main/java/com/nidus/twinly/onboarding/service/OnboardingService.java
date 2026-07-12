package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontProperties;
import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.PhotoType;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.onboarding.dto.command.*;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfileNicknameCheckResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoCommitResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoPresignResult;
import com.nidus.twinly.onboarding.entity.SurveyAnswer;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;

import java.io.IOException;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final Set<String> ALLOWED_PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int PROFILE_PHOTO_MAX_BYTES = 10 * 1024 * 1024;
    private static final int PROFILE_PHOTO_PRESIGN_EXPIRES_IN_SECONDS = 300;

    private static final Set<String> FORBIDDEN_NICKNAME_WORDS = Set.of(
            "admin", "관리자", "운영자", "공지"
    );

    private final S3Service s3Service;

    private final AnonSessionRepository anonSessionRepository;
    private final AnonSessionPhotoRepository anonSessionPhotoRepository;
    private final AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final UserRepository userRepository;

    private final SurveyLoader surveyLoader;

    private final CloudFrontUtilities cloudFrontUtilities;
    private final PrivateKey cloudFrontPrivateKey;
    private final CloudFrontProperties cloudFrontProperties;


    @Transactional
    public void basicInfo(Long anonSessionId, OnboardingBasicInfoCommand command) {
        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        if (command.familyName() != null) {
            anonSession.changeFamilyName(command.familyName());
        }
        if (command.givenName() != null) {
            anonSession.changeGivenName(command.givenName());
        }
        if (command.gender() != null) {
            anonSession.changeGender(Gender.valueOf(command.gender()));
        }
        if (command.affiliation() != null) {
            anonSession.changeAffiliation(command.affiliation());
        }
        if (command.affiliationNumber() != null) {
            anonSession.changeAffiliationNumber(command.affiliationNumber());
        }
        if (command.birthDate() != null) {
            anonSession.changeBirthDate(command.birthDate());
        }
    }

    public String surveyQuestions() throws IOException {
        ClassPathResource resource = new ClassPathResource("survey/survey_v1_mixed.json");
        String json = new String(resource.getInputStream().readAllBytes());

        return json;
    }

    @Transactional
    public void surveyAnswer(Long anonSessionId, OnboardingSurveyAnswerCommand command) {
        if (command.answer() == null || command.answer().qId() == null || command.answer().optionName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다");
        }

        Integer qId = command.answer().qId();
        SurveyOptionName answerValue = command.answer().optionName();

        SurveyQuestion question = surveyLoader.getQuestion(qId);

        if (question == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 질문입니다: " + qId);
        }

        surveyAnswerRepository.findByAnonSessionIdAndQId(anonSessionId, qId)
                .ifPresentOrElse(
                        sa -> sa.changeOptionName(answerValue),
                        () -> surveyAnswerRepository.save(SurveyAnswer.create(anonSessionId, qId, answerValue))
                );

        if (qId.equals(surveyLoader.lastKey())) {
            saveAllSurveyAnswer(anonSessionId);
        }
    }

    @Transactional
    public void saveAllSurveyAnswer(Long anonSessionId) {
        List<SurveyAnswer> answers = surveyAnswerRepository.findByAnonSessionId(anonSessionId);

        for (SurveyAnswer answer : answers) {
            SurveyQuestion question = surveyLoader.getQuestion(answer.getQId());
            AnonSessionPersonaElement personaElement = AnonSessionPersonaElement.create(answer.getAnonSessionId(), question.dimension(), question.traitFor(answer.getOptionName()));
            anonSessionPersonaElementRepository.save(personaElement);
        }
    }

    @Transactional
    public void interests(Long anonSessionId, OnboardingInterestsCommand command) {
        if (command.interests() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        for (String interest : command.interests()) {
            anonSessionPersonaElementRepository.save(AnonSessionPersonaElement.create(anonSessionId, PersonaDimension.INTERESTS, interest));
        }
    }

    public OnboardingProfilePhotoPresignResult profilePhotoPresign(Long anonSessionId, OnboardingProfilePhotoPresignCommand command) {
        if (command.contentType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        if (!ALLOWED_PHOTO_CONTENT_TYPES.contains(command.contentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다: " + command.contentType());
        }

        String key = "profile-photos/%d/%s".formatted(anonSessionId, UUID.randomUUID());

        String presignedUrl = s3Service.presignPut(key, command.contentType(), Duration.ofSeconds(PROFILE_PHOTO_PRESIGN_EXPIRES_IN_SECONDS));

        return new OnboardingProfilePhotoPresignResult(
                presignedUrl,
                key,
                "PUT",
                new RequiredHeaders(command.contentType()),
                PROFILE_PHOTO_MAX_BYTES,
                PROFILE_PHOTO_PRESIGN_EXPIRES_IN_SECONDS
        );
    }

    @Transactional
    public OnboardingProfilePhotoCommitResult profilePhotoCommit(Long anonSessionId, OnboardingProfilePhotoCommitCommand command) {
        if (command.key() == null || command.position() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        String expectedPrefix = "profile-photos/%d/".formatted(anonSessionId);
        if (!command.key().startsWith(expectedPrefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 세션의 key가 아닙니다: " + command.key());
        }

        if (!s3Service.exists(command.key())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드가 완료되지 않은 key입니다: " + command.key());
        }

        anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        PhotoPosInfo position = command.position();
        anonSessionPhotoRepository.findByAnonSessionIdAndType(anonSessionId, PhotoType.PROFILE)
                .ifPresentOrElse(
                        photo -> photo.changePhoto(PhotoType.PROFILE, command.key(),
                                position.startPos().x(), position.startPos().y(), position.width(), position.height()),
                        () -> anonSessionPhotoRepository.save(AnonSessionPhoto.create(anonSessionId, PhotoType.PROFILE, command.key(),
                                position.startPos().x(), position.startPos().y(), position.width(), position.height()))
                );

        String resourceUrl = "https://%s/%s".formatted(cloudFrontProperties.domain(), command.key());

        CannedSignerRequest cannedRequest = CannedSignerRequest.builder()
                .resourceUrl(resourceUrl)
                .privateKey(cloudFrontPrivateKey)
                .keyPairId(cloudFrontProperties.keyPairId())
                .expirationDate(Instant.now().plus(Duration.ofMinutes(10)))
                .build();

        String photoUrl = cloudFrontUtilities.getSignedUrlWithCannedPolicy(cannedRequest).url();

        return new OnboardingProfilePhotoCommitResult(photoUrl, position);
    }

    @Transactional
    public void height(Long anonSessionId, OnboardingHeightCommand command) {
        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        if (command.height() != null) {
            anonSession.changeHeight(String.valueOf(command.height()));
        }
    }

    public OnboardingProfileNicknameCheckResult profileNicknameCheck(Long anonSessionId, OnboardingProfileNicknameCheckCommand command) {
        String nickname = validateAndNormalizeNickname(command.nickname());

        boolean isAvailable = !userRepository.existsByNickname(nickname)
                && !anonSessionRepository.existsByNickname(nickname);

        return new OnboardingProfileNicknameCheckResult(isAvailable);
    }

    @Transactional
    public void profileNickname(Long anonSessionId, OnboardingProfileNicknameCommand command) {
        String nickname = validateAndNormalizeNickname(command.nickname());

        if (userRepository.existsByNickname(nickname)
                || anonSessionRepository.existsByNickname(nickname)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다: " + nickname);
        }

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        anonSession.changeNickname(nickname);
    }

    private String validateAndNormalizeNickname(String nickname) {
        if (nickname == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        String trimmed = nickname.trim();

        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임은 공백일 수 없습니다.");
        }

        String normalized = trimmed.toLowerCase();
        boolean containsForbiddenWord = FORBIDDEN_NICKNAME_WORDS.stream()
                .anyMatch(normalized::contains);

        if (containsForbiddenWord) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용할 수 없는 닉네임입니다.");
        }

        return trimmed;
    }
}
