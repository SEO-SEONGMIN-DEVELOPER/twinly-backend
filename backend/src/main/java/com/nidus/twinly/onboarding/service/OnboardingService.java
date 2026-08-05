package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionAgreement;
import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import com.nidus.twinly.anon.repository.AnonSessionAgreementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.presign.PhotoCommitService;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.presign.PresignService;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import com.nidus.twinly.onboarding.dto.command.*;
import com.nidus.twinly.onboarding.dto.result.OnboardingAffiliationsResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfileNicknameCheckResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoCommitResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoPresignResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingSchoolsItemResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingSchoolsResult;
import com.nidus.twinly.onboarding.entity.SurveyAnswer;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.school.entity.School;
import com.nidus.twinly.school.entity.SchoolAffiliation;
import com.nidus.twinly.school.entity.SchoolDomain;
import com.nidus.twinly.school.repository.SchoolAffiliationRepository;
import com.nidus.twinly.school.repository.SchoolDomainRepository;
import com.nidus.twinly.school.repository.SchoolRepository;
import com.nidus.twinly.school.service.SchoolCatalog;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private static final Set<String> FORBIDDEN_NICKNAME_WORDS = Set.of(
            "admin", "관리자", "운영자", "공지"
    );

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_-]{2,20}$");

    private final PresignService presignService;
    private final PhotoCommitService photoCommitService;
    private final SchoolCatalog schoolCatalog;

    private final AnonSessionRepository anonSessionRepository;
    private final AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolDomainRepository schoolDomainRepository;
    private final SchoolAffiliationRepository schoolAffiliationRepository;
    private final AnonSessionPhotoRepository anonSessionPhotoRepository;
    private final AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;
    private final AnonSessionAgreementRepository anonSessionAgreementRepository;
    private final PolicyCatalog policyCatalog;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final UserRepository userRepository;

    private final SurveyLoader surveyLoader;

    @Transactional
    public void basicInfo(AnonSessionSnapshot anonSessionSnapshot, OnboardingBasicInfoCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();
        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ANON_SESSION));

        anonSession.changeFamilyName(command.familyName());
        anonSession.changeGivenName(command.givenName());
        anonSession.changeGender(command.gender());
        anonSession.changeAffiliationNumber(command.affiliationNumber());
        anonSession.changeBirthDate(command.birthDate().toString());
    }

    public List<SurveyQuestion> surveyQuestions() {
        return surveyLoader.getAllQuestions();
    }

    @Transactional
    public void surveyAnswer(AnonSessionSnapshot anonSessionSnapshot, OnboardingSurveyAnswerCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();
        Integer qId = command.answer().qId();
        SurveyOptionName answerValue = command.answer().optionName();

        SurveyQuestion question = surveyLoader.getQuestion(qId);

        if (question == null) {
            throw new BusinessException(ErrorCode.SURVEY_QUESTION_NOT_FOUND, "존재하지 않는 질문입니다: " + qId);
        }

        surveyAnswerRepository.findByAnonSessionIdAndQuestionId(anonSessionId, qId)
                .ifPresentOrElse(
                        sa -> sa.changeOptionName(answerValue),
                        () -> surveyAnswerRepository.save(SurveyAnswer.create(anonSessionId, qId, answerValue))
                );

        if (surveyLoader.isLastQuestion(qId)) {
            saveAllSurveyAnswer(anonSessionId);
        }
    }

    private void saveAllSurveyAnswer(Long anonSessionId) {
        List<SurveyAnswer> answers = surveyAnswerRepository.findAllByAnonSessionId(anonSessionId);

        List<AnonSessionPersonaElement> personaElements = answers.stream()
                .map(answer -> {
                    SurveyQuestion question = surveyLoader.getQuestion(answer.getQuestionId());
                    return AnonSessionPersonaElement.create(anonSessionId, question.dimension(), question.traitFor(answer.getOptionName()));
                })
                .toList();

        Set<PersonaDimension> dimensions = personaElements.stream()
                .map(AnonSessionPersonaElement::getDimension)
                .collect(Collectors.toSet());

        if (!dimensions.isEmpty()) {
            anonSessionPersonaElementRepository.deleteByAnonSessionIdAndDimensionIn(anonSessionId, dimensions);
        }

        anonSessionPersonaElementRepository.saveAll(personaElements);
    }

    @Transactional
    public void interests(AnonSessionSnapshot anonSessionSnapshot, OnboardingInterestsCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();

        anonSessionPersonaElementRepository.deleteByAnonSessionIdAndDimensionIn(anonSessionId, Set.of(PersonaDimension.INTERESTS));

        for (String interest : command.interests()) {
            anonSessionPersonaElementRepository.save(AnonSessionPersonaElement.create(anonSessionId, PersonaDimension.INTERESTS, interest));
        }
    }

    public OnboardingProfilePhotoPresignResult profilePhotoPresign(AnonSessionSnapshot anonSessionSnapshot, OnboardingProfilePhotoPresignCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();
        PhotoPresignResult presign = presignService.presignPhoto(anonSessionId, command.contentType(), PhotoType.PROFILE);

        return new OnboardingProfilePhotoPresignResult(presign.uploadUrl(), presign.key(), presign.method(), presign.requiredHeaders(), presign.maxBytes(), presign.expiresAt());
    }

    @Transactional
    public OnboardingProfilePhotoCommitResult profilePhotoCommit(AnonSessionSnapshot anonSessionSnapshot, OnboardingProfilePhotoCommitCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();
        String photoUrl = photoCommitService.commitProfilePhoto(anonSessionId, command.key()).photoUrl();

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

    public OnboardingProfileNicknameCheckResult profileNicknameCheck(AnonSessionSnapshot anonSessionSnapshot, OnboardingProfileNicknameCheckCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();
        String nickname = validateAndNormalizeNickname(command.nickname());

        boolean isAvailable = !userRepository.existsByNickname(nickname)
                && !anonSessionRepository.existsByNicknameAndIdNot(nickname, anonSessionId);

        return new OnboardingProfileNicknameCheckResult(isAvailable);
    }

    @Transactional
    public void profileNickname(AnonSessionSnapshot anonSessionSnapshot, OnboardingProfileNicknameCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();
        String nickname = validateAndNormalizeNickname(command.nickname());

        if (userRepository.existsByNickname(nickname)
                || anonSessionRepository.existsByNicknameAndIdNot(nickname, anonSessionId)) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_USED, "이미 사용 중인 닉네임입니다: " + nickname);
        }

        AnonSession anonSession = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ANON_SESSION));

        anonSession.changeNickname(nickname);

        try {
            anonSessionRepository.saveAndFlush(anonSession);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_USED, "이미 사용 중인 닉네임입니다: " + nickname);
        }
    }

    @Transactional
    public void grantConsents(AnonSessionSnapshot anonSessionSnapshot, OnboardingGrantConsentsCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();

        List<String> policyNameIdentifiers = command.grants().stream().map(grant -> grant.policyId()).toList();

        Map<PolicyKey, PolicySummary> policyByKey = policyCatalog.loadByKey(policyNameIdentifiers);

        Set<Long> alreadyAgreedPolicyIds = anonSessionAgreementRepository.findAllByAnonSessionIdAndRevokedAtIsNull(anonSessionId).stream()
                .map(AnonSessionAgreement::getPolicyId)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        List<AnonSessionAgreement> agreements = command.grants().stream()
                .map(grant -> {
                    PolicySummary policy = policyByKey.get(new PolicyKey(grant.policyId(), grant.version()));
                    if (policy == null) {
                        throw new BusinessException(ErrorCode.POLICY_NOT_FOUND);
                    }
                    return policy;
                })
                .filter(policy -> !alreadyAgreedPolicyIds.contains(policy.getId()))
                .map(policy -> AnonSessionAgreement.create(anonSessionId, policy.getId(), now))
                .toList();

        anonSessionAgreementRepository.saveAll(agreements);
    }

    @Transactional
    public void revokeConsents(AnonSessionSnapshot anonSessionSnapshot, OnboardingRevokeConsentsCommand command) {
        Long anonSessionId = anonSessionSnapshot.id();

        List<String> policyNameIdentifiers = command.grants().stream().map(grant -> grant.policyId()).toList();

        Map<PolicyKey, PolicySummary> policyByKey = policyCatalog.loadByKey(policyNameIdentifiers);

        List<PolicySummary> policies = command.grants().stream()
                .map(grant -> {
                    PolicySummary policy = policyByKey.get(new PolicyKey(grant.policyId(), grant.version()));
                    if (policy == null) {
                        throw new BusinessException(ErrorCode.POLICY_NOT_FOUND);
                    }
                    return policy;
                })
                .toList();

        if (policies.stream().anyMatch(policy -> Boolean.TRUE.equals(policy.getIsRequired()))) {
            throw new BusinessException(ErrorCode.REQUIRED_POLICY_REVOKE_DENIED);
        }

        List<Long> policyIdsToRevoke = policies.stream().map(PolicySummary::getId).toList();
        if (!policyIdsToRevoke.isEmpty()) {
            anonSessionAgreementRepository.revokeWithPreviousVersionsByAnonSessionIdAndPolicyIdIn(anonSessionId, policyIdsToRevoke);
        }
    }

    private String validateAndNormalizeNickname(String nickname) {
        String trimmed = nickname.trim();

        if (!NICKNAME_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME, "닉네임은 2~20자의 한글·영문·숫자·(_-)만 사용할 수 있습니다: " + trimmed);
        }

        String normalized = trimmed.toLowerCase();
        boolean containsForbiddenWord = FORBIDDEN_NICKNAME_WORDS.stream()
                .anyMatch(normalized::contains);

        if (containsForbiddenWord) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME);
        }

        return trimmed;
    }

    public OnboardingSchoolsResult schools() {
        Map<Long, List<String>> domainsBySchoolId = schoolDomainRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        SchoolDomain::getSchoolId,
                        Collectors.mapping(SchoolDomain::getDomain, Collectors.toList())
                ));

        List<OnboardingSchoolsItemResult> schools = schoolRepository.findAllByOrderByNameAsc().stream()
                .map(school -> new OnboardingSchoolsItemResult(
                        school.getName(),
                        domainsBySchoolId.getOrDefault(school.getId(), List.of())
                ))
                .toList();

        return new OnboardingSchoolsResult(schools);
    }

    public OnboardingAffiliationsResult affiliations(AnonSessionSnapshot anonSessionSnapshot) {
        School school = schoolCatalog.findByEmail(verifiedEmail(anonSessionSnapshot.id()));

        List<String> affiliations = schoolAffiliationRepository.findAllBySchoolIdOrderByNameAsc(school.getId()).stream()
                .map(SchoolAffiliation::getName)
                .toList();

        return new OnboardingAffiliationsResult(affiliations);
    }

    @Transactional
    public void affiliation(AnonSessionSnapshot anonSessionSnapshot, OnboardingAffiliationCommand command) {
        AnonSession anonSession = anonSessionRepository.findById(anonSessionSnapshot.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ANON_SESSION));

        anonSession.changeAffiliation(command.affiliation().trim());
    }

    private String verifiedEmail(Long anonSessionId) {
        return anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(anonSessionId, VerificationType.EMAIL)
                .filter(session -> session.getVerifiedAt() != null)
                .map(AnonSessionVerificationSession::getContact)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_NOT_COMPLETED));
    }
}
