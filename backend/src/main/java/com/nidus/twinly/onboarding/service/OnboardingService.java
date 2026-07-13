package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontProperties;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.presign.*;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final Set<String> FORBIDDEN_NICKNAME_WORDS = Set.of(
            "admin", "관리자", "운영자", "공지"
    );

    private final PresignService presignService;
    private final PhotoCommitService photoCommitService;

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
            anonSession.changeGender(command.gender());
        }
        if (command.affiliation() != null) {
            anonSession.changeAffiliation(command.affiliation());
        }
        if (command.affiliationNumber() != null) {
            anonSession.changeAffiliationNumber(command.affiliationNumber());
        }
        if (command.birthDate() != null) {
            anonSession.changeBirthDate(command.birthDate().toString());
        }
    }

    public String surveyQuestions() throws IOException {
        ClassPathResource resource = new ClassPathResource("survey/survey_v1_mixed.json");
        String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

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
        List<SurveyAnswer> answers = surveyAnswerRepository.findAllByAnonSessionId(anonSessionId);

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
        PhotoPresignResult presign = presignService.presignPhoto(anonSessionId, command.contentType(), PhotoType.PROFILE);

        return new OnboardingProfilePhotoPresignResult(presign.uploadUrl(), presign.key(), presign.method(), presign.requiredHeaders(), presign.maxBytes(), presign.expiresAt());
    }

    @Transactional
    public OnboardingProfilePhotoCommitResult profilePhotoCommit(Long anonSessionId, OnboardingProfilePhotoCommitCommand command) {
        if (command.key() == null || command.position() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        String photoUrl = photoCommitService.commitProfilePhoto(anonSessionId, command.key());

        PhotoPosInfo position = command.position();
        anonSessionPhotoRepository.findByAnonSessionIdAndType(anonSessionId, PhotoType.PROFILE)
                .ifPresentOrElse(
                        photo -> photo.changePhoto(command.key(),
                                position.startPos().x(), position.startPos().y(), position.width(), position.height()),
                        () -> anonSessionPhotoRepository.save(AnonSessionPhoto.create(anonSessionId, PhotoType.PROFILE, command.key(),
                                position.startPos().x(), position.startPos().y(), position.width(), position.height()))
                );

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
