package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.aws.AwsProperties;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.onboarding.dto.command.*;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfileNicknameCheckResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoCommitResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoPresignResult;
import com.nidus.twinly.onboarding.entity.PersonaElement;
import com.nidus.twinly.onboarding.entity.SurveyAnswer;
import com.nidus.twinly.onboarding.repository.PersonaElementRepository;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final Set<String> ALLOWED_PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int PROFILE_PHOTO_MAX_BYTES = 10 * 1024 * 1024;
    private static final int PROFILE_PHOTO_PRESIGN_EXPIRES_IN_SECONDS = 300;

    private final AnonSessionRepository anonSessionRepository;
    private final PersonaElementRepository personaElementRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final UserRepository userRepository;

    private final SurveyLoader surveyLoader;

    private final AwsProperties awsProperties;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

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
        if (command.experience() != null) {
            anonSession.changeExperience(command.experience());
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
            PersonaElement personaElement = PersonaElement.create(answer.getAnonSessionId(), question.dimension(), question.traitFor(answer.getOptionName()));
            personaElementRepository.save(personaElement);
        }
    }

    @Transactional
    public void interests(Long anonSessionId, OnboardingInterestsCommand command) {
        if (command.interests() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        for (String interest : command.interests()) {
            personaElementRepository.save(PersonaElement.create(anonSessionId, PersonaDimension.INTERESTS, interest));
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

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(awsProperties.s3().bucket())
                .key(key)
                .contentType(command.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PROFILE_PHOTO_PRESIGN_EXPIRES_IN_SECONDS))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new OnboardingProfilePhotoPresignResult(
                presignedRequest.url().toString(),
                key,
                "PUT",
                new RequiredHeaders(command.contentType()),
                PROFILE_PHOTO_MAX_BYTES,
                PROFILE_PHOTO_PRESIGN_EXPIRES_IN_SECONDS
        );
    }

    @Transactional
    public OnboardingProfilePhotoCommitResult profilePhotoCommit(Long anonSessionId, OnboardingProfilePhotoCommitCommand command) {
        if (command.key() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        String expectedPrefix = "profile-photos/%d/".formatted(anonSessionId);
        if (!command.key().startsWith(expectedPrefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 세션의 key가 아닙니다: " + command.key());
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(awsProperties.s3().bucket())
                    .key(command.key())
                    .build());
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드가 완료되지 않은 key입니다: " + command.key());
        }

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        anonSession.changePhotoKey(command.key());

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(awsProperties.s3().bucket())
                .key(command.key())
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getObjectRequest)
                .build();

        String photoUrl = s3Presigner.presignGetObject(presignRequest).url().toString();

        return new OnboardingProfilePhotoCommitResult(photoUrl);
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
        if (command.nickname() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        boolean isAvailable = !userRepository.existsByNickname(command.nickname())
                && !anonSessionRepository.existsByNickname(command.nickname());

        return new OnboardingProfileNicknameCheckResult(isAvailable);
    }

    @Transactional
    public void profileNickname(Long anonSessionId, OnboardingProfileNicknameCommand command) {
        if (command.nickname() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        if (userRepository.existsByNickname(command.nickname())
                || anonSessionRepository.existsByNickname(command.nickname())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다: " + command.nickname());
        }

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        anonSession.changeNickname(command.nickname());
    }
}
