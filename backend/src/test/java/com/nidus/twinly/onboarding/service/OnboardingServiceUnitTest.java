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
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.presign.PhotoCommitResult;
import com.nidus.twinly.common.presign.PhotoCommitService;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.presign.PresignService;
import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.common.survey.SurveyAnswerInput;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOption;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import com.nidus.twinly.onboarding.dto.command.OnboardingAffiliationCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingBasicInfoCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingGrantConsentsCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingGrantConsentsItemCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingInterestsCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingProfileNicknameCheckCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingProfileNicknameCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingProfilePhotoCommitCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingProfilePhotoPresignCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingRevokeConsentsCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingRevokeConsentsItemCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingSurveyAnswerCommand;
import com.nidus.twinly.onboarding.dto.result.OnboardingAffiliationsResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingOrganizationsItemResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingOrganizationsResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfileNicknameCheckResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoCommitResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoPresignResult;
import com.nidus.twinly.onboarding.entity.SurveyAnswer;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.entity.OrganizationAffiliation;
import com.nidus.twinly.organization.entity.OrganizationDomain;
import com.nidus.twinly.organization.repository.OrganizationAffiliationRepository;
import com.nidus.twinly.organization.repository.OrganizationDomainRepository;
import com.nidus.twinly.organization.repository.OrganizationRepository;
import com.nidus.twinly.organization.service.OrganizationCatalog;
import com.nidus.twinly.user.repository.UserRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceUnitTest {

    private static final Long ANON_SESSION_ID = 1L;
    private static final AnonSessionSnapshot ANON_SESSION = new AnonSessionSnapshot(
            ANON_SESSION_ID,
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            Instant.parse("2999-01-01T00:00:00Z"),
            "닉네임",
            "홍",
            "길동",
            Gender.MALE,
            "트윈리대학교",
            "2024001",
            "2000-01-01",
            "01012345678",
            "phoneHash",
            "test@test.com",
            "emailHash",
            Instant.parse("2026-01-01T00:00:00Z")
    );

    @Mock
    PresignService presignService;

    @Mock
    PhotoCommitService photoCommitService;

    @Mock
    AnonSessionRepository anonSessionRepository;

    @Mock
    AnonSessionPhotoRepository anonSessionPhotoRepository;

    @Mock
    AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;

    @Mock
    AnonSessionAgreementRepository anonSessionAgreementRepository;

    @Mock
    PolicyCatalog policyCatalog;

    @Mock
    SurveyAnswerRepository surveyAnswerRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    SurveyLoader surveyLoader;

    @Mock
    OrganizationCatalog organizationCatalog;

    @Mock
    OrganizationRepository organizationRepository;

    @Mock
    OrganizationDomainRepository organizationDomainRepository;

    @Mock
    OrganizationAffiliationRepository organizationAffiliationRepository;

    @Mock
    AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;

    @InjectMocks
    OnboardingService onboardingService;

    // ---------- basic-info ----------

    @Test
    @DisplayName("기본 정보 입력 시 익명 세션의 이름·성별·학번·생년월일이 갱신된다")
    void basicInfo_updates_anon_session() {
        // given: 아직 아무 정보도 없는 익명 세션
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plusSeconds(3600));
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));

        // when: 기본 정보 입력
        onboardingService.basicInfo(ANON_SESSION, new OnboardingBasicInfoCommand(
                "홍", "길동", Gender.MALE, "2024001", LocalDate.of(2000, 1, 1)));

        // then: 세션 엔티티의 각 필드가 갱신되고 생년월일은 문자열로 저장됨
        assertThat(anonSession.getFamilyName()).isEqualTo("홍");
        assertThat(anonSession.getGivenName()).isEqualTo("길동");
        assertThat(anonSession.getGender()).isEqualTo(Gender.MALE);
        assertThat(anonSession.getAffiliationNumber()).isEqualTo("2024001");
        assertThat(anonSession.getBirthDate()).isEqualTo("2000-01-01");
    }

    @Test
    @DisplayName("기본 정보 입력은 학과를 건드리지 않는다")
    void basicInfo_does_not_touch_affiliation() {
        // given: 학과가 아직 비어 있는 익명 세션
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plusSeconds(3600));
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));

        // when: 기본 정보 입력
        onboardingService.basicInfo(ANON_SESSION, new OnboardingBasicInfoCommand(
                "홍", "길동", Gender.MALE, "2024001", LocalDate.of(2000, 1, 1)));

        // then: 학과는 별도 단계에서 채워지므로 여전히 비어 있음
        assertThat(anonSession.getAffiliation()).isNull();
    }

    @Test
    @DisplayName("기본 정보 입력 시 익명 세션이 없으면 INVALID_ANON_SESSION 예외가 발생한다")
    void basicInfo_when_session_not_found_throws() {
        // given: 세션 조회 결과 없음
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.empty());

        // when & then: INVALID_ANON_SESSION 예외 발생
        assertThatThrownBy(() -> onboardingService.basicInfo(ANON_SESSION, new OnboardingBasicInfoCommand(
                "홍", "길동", Gender.MALE, "2024001", LocalDate.of(2000, 1, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ANON_SESSION);
    }

    // ---------- 학교/학과 ----------

    @Test
    @DisplayName("학교 목록 조회는 저장된 학교의 이름과 이메일 도메인을 그대로 반환한다")
    void organizations_returns_name_and_domains() {
        // given: 가입 가능한 학교 1곳이 도메인 2개로 등록되어 있음
        given(organizationRepository.findAllByOrderByNameAsc()).willReturn(List.of(organization(1L, "트윈리대학교")));
        given(organizationDomainRepository.findAll())
                .willReturn(List.of(organizationDomain(1L, "nidus.ac.kr"), organizationDomain(1L, "grad.nidus.ac.kr")));

        // when: 학교 목록 조회
        OnboardingOrganizationsResult result = onboardingService.organizations();

        // then: 앱이 도메인을 자동 입력할 수 있도록 이름과 도메인 전체가 함께 나감
        assertThat(result.organizations()).containsExactly(
                new OnboardingOrganizationsItemResult("트윈리대학교", List.of("nidus.ac.kr", "grad.nidus.ac.kr")));
    }

    @Test
    @DisplayName("학과 목록은 인증 완료된 이메일이 속한 학교의 학과만 반환한다")
    void affiliations_returns_affiliations_of_verified_organization() {
        // given: 이메일 인증이 끝난 세션과, 그 도메인에 해당하는 학교의 학과 2개
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.of(verifiedEmailSession("student@nidus.ac.kr")));
        given(organizationCatalog.findByEmail("student@nidus.ac.kr")).willReturn(organization(1L, "트윈리대학교"));
        given(organizationAffiliationRepository.findAllByOrganizationIdOrderByNameAsc(1L))
                .willReturn(List.of(organizationAffiliation("경영학과"), organizationAffiliation("컴퓨터공학과")));

        // when: 학과 목록 조회
        OnboardingAffiliationsResult result = onboardingService.affiliations(ANON_SESSION);

        // then: 요청 파라미터 없이 서버가 판별한 학교의 학과가 이름만 담겨 나감
        assertThat(result.affiliations()).containsExactly("경영학과", "컴퓨터공학과");
    }

    @Test
    @DisplayName("이메일 인증을 마치지 않은 세션이 학과 목록을 조회하면 EMAIL_VERIFICATION_NOT_COMPLETED 예외가 발생한다")
    void affiliations_when_email_not_verified_throws() {
        // given: 인증번호는 받았지만 아직 확인하지 않은 세션
        AnonSessionVerificationSession notVerified = AnonSessionVerificationSession.create(
                VerificationType.EMAIL, ANON_SESSION_ID, "student@nidus.ac.kr", "123456", Instant.now().plusSeconds(60));
        given(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL))
                .willReturn(Optional.of(notVerified));

        // when & then: 학교를 특정할 수 없으므로 거절
        assertThatThrownBy(() -> onboardingService.affiliations(ANON_SESSION))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_COMPLETED);
    }

    @Test
    @DisplayName("학과 입력은 목록에 없는 값이어도 저장하고 앞뒤 공백을 제거한다")
    void affiliation_saves_free_text() {
        // given: 학과가 비어 있는 익명 세션
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plusSeconds(3600));
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));

        // when: 목록에 없는 신설 학과를 자유 입력
        onboardingService.affiliation(ANON_SESSION, new OnboardingAffiliationCommand("  신설학과 "));

        // then: 목록 일치 검증 없이 저장되고 공백은 제거됨
        assertThat(anonSession.getAffiliation()).isEqualTo("신설학과");
    }

    @Test
    @DisplayName("학과 입력 시 익명 세션이 없으면 INVALID_ANON_SESSION 예외가 발생한다")
    void affiliation_when_session_not_found_throws() {
        // given: 세션 조회 결과 없음
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.empty());

        // when & then: INVALID_ANON_SESSION 예외 발생
        assertThatThrownBy(() -> onboardingService.affiliation(ANON_SESSION, new OnboardingAffiliationCommand("컴퓨터공학과")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ANON_SESSION);
    }

    private Organization organization(Long id, String name) {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "id", id);
        ReflectionTestUtils.setField(organization, "name", name);
        return organization;
    }

    private OrganizationDomain organizationDomain(Long organizationId, String domain) {
        OrganizationDomain organizationDomain = BeanUtils.instantiateClass(OrganizationDomain.class);
        ReflectionTestUtils.setField(organizationDomain, "organizationId", organizationId);
        ReflectionTestUtils.setField(organizationDomain, "domain", domain);
        return organizationDomain;
    }

    private OrganizationAffiliation organizationAffiliation(String name) {
        OrganizationAffiliation affiliation = BeanUtils.instantiateClass(OrganizationAffiliation.class);
        ReflectionTestUtils.setField(affiliation, "name", name);
        return affiliation;
    }

    private AnonSessionVerificationSession verifiedEmailSession(String email) {
        AnonSessionVerificationSession session = AnonSessionVerificationSession.create(
                VerificationType.EMAIL, ANON_SESSION_ID, email, "123456", Instant.now().plusSeconds(60));
        session.verify();
        return session;
    }

    // ---------- survey ----------

    @Test
    @DisplayName("설문 문항 조회는 로더가 가진 전체 문항을 그대로 반환한다")
    void surveyQuestions_returns_all_questions() {
        // given: 로더가 문항 1개를 보유
        SurveyQuestion question = question(8);
        given(surveyLoader.getAllQuestions()).willReturn(List.of(question));

        // when: 설문 문항 조회
        List<SurveyQuestion> questions = onboardingService.surveyQuestions();

        // then: 로더의 문항이 그대로 반환됨
        assertThat(questions).containsExactly(question);
    }

    @Test
    @DisplayName("존재하지 않는 설문 문항에 답하면 SURVEY_QUESTION_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void surveyAnswer_when_question_not_found_throws() {
        // given: 로더에 해당 qId 문항이 없음
        given(surveyLoader.getQuestion(999)).willReturn(null);

        // when & then: SURVEY_QUESTION_NOT_FOUND 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> onboardingService.surveyAnswer(ANON_SESSION,
                new OnboardingSurveyAnswerCommand(new SurveyAnswerInput(999, SurveyOptionName.A))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SURVEY_QUESTION_NOT_FOUND);

        then(surveyAnswerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("처음 답한 문항이면 새 SurveyAnswer를 저장하고 페르소나 요소는 만들지 않는다")
    void surveyAnswer_first_time_saves_new_answer() {
        // given: 존재하는 문항이고 기존 답변이 없으며 마지막 문항도 아님
        given(surveyLoader.getQuestion(8)).willReturn(question(8));
        given(surveyAnswerRepository.findByAnonSessionIdAndQuestionId(ANON_SESSION_ID, 8)).willReturn(Optional.empty());
        given(surveyLoader.isLastQuestion(8)).willReturn(false);

        // when: 설문 답변 저장
        onboardingService.surveyAnswer(ANON_SESSION,
                new OnboardingSurveyAnswerCommand(new SurveyAnswerInput(8, SurveyOptionName.A)));

        // then: 세션/문항/선택지로 새 답변이 저장되고 페르소나 요소는 저장되지 않음
        ArgumentCaptor<SurveyAnswer> captor = ArgumentCaptor.forClass(SurveyAnswer.class);
        then(surveyAnswerRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(captor.getValue().getQuestionId()).isEqualTo(8);
        assertThat(captor.getValue().getOptionName()).isEqualTo(SurveyOptionName.A);
        then(anonSessionPersonaElementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 답한 문항이면 기존 답변의 선택지만 바꾸고 새로 저장하지 않는다")
    void surveyAnswer_existing_answer_is_updated() {
        // given: 같은 문항에 대한 기존 답변(A)이 존재하고 마지막 문항은 아님
        SurveyAnswer existing = SurveyAnswer.create(ANON_SESSION_ID, 8, SurveyOptionName.A);
        given(surveyLoader.getQuestion(8)).willReturn(question(8));
        given(surveyAnswerRepository.findByAnonSessionIdAndQuestionId(ANON_SESSION_ID, 8)).willReturn(Optional.of(existing));
        given(surveyLoader.isLastQuestion(8)).willReturn(false);

        // when: 같은 문항에 B로 다시 답변
        onboardingService.surveyAnswer(ANON_SESSION,
                new OnboardingSurveyAnswerCommand(new SurveyAnswerInput(8, SurveyOptionName.B)));

        // then: 기존 답변만 갱신되고 저장은 호출되지 않음
        assertThat(existing.getOptionName()).isEqualTo(SurveyOptionName.B);
        then(surveyAnswerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("마지막 문항에 답하면 저장된 모든 답변을 페르소나 요소(차원+특성)로 변환해 저장한다")
    void surveyAnswer_on_last_question_creates_persona_elements() {
        // given: 마지막 문항(23)에 답하고, 세션에 답변 1건이 저장되어 있음
        given(surveyLoader.getQuestion(23)).willReturn(question(23));
        given(surveyAnswerRepository.findByAnonSessionIdAndQuestionId(ANON_SESSION_ID, 23)).willReturn(Optional.empty());
        given(surveyLoader.isLastQuestion(23)).willReturn(true);
        given(surveyAnswerRepository.findAllByAnonSessionId(ANON_SESSION_ID))
                .willReturn(List.of(SurveyAnswer.create(ANON_SESSION_ID, 23, SurveyOptionName.B)));

        // when: 마지막 문항에 답변
        onboardingService.surveyAnswer(ANON_SESSION,
                new OnboardingSurveyAnswerCommand(new SurveyAnswerInput(23, SurveyOptionName.B)));

        // then: 설문이 만드는 차원을 먼저 지우고(재답변 시 중복 누적 방지) 변환 결과를 저장한다
        then(anonSessionPersonaElementRepository).should()
                .deleteByAnonSessionIdAndDimensionIn(ANON_SESSION_ID, Set.of(PersonaDimension.OPENNESS));

        ArgumentCaptor<List<AnonSessionPersonaElement>> captor = ArgumentCaptor.forClass(List.class);
        then(anonSessionPersonaElementRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(element -> {
            assertThat(element.getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
            assertThat(element.getDimension()).isEqualTo(PersonaDimension.OPENNESS);
            assertThat(element.getExplanation()).isEqualTo("B 특성");
        });
    }

    // ---------- interests ----------

    @Test
    @DisplayName("관심사는 각각 INTEREST 차원의 페르소나 요소로 저장된다")
    void interests_saves_each_interest_as_persona_element() {
        // when: 관심사 2건 저장
        onboardingService.interests(ANON_SESSION, new OnboardingInterestsCommand(List.of("등산", "독서")));

        // then: 관심사마다 INTEREST 차원의 페르소나 요소가 저장됨
        ArgumentCaptor<AnonSessionPersonaElement> captor = ArgumentCaptor.forClass(AnonSessionPersonaElement.class);
        then(anonSessionPersonaElementRepository).should(times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(element -> {
                    assertThat(element.getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
                    assertThat(element.getDimension()).isEqualTo(PersonaDimension.INTEREST);
                })
                .extracting(AnonSessionPersonaElement::getExplanation)
                .containsExactly("등산", "독서");
    }

    @Test
    @DisplayName("관심사가 빈 목록이면 아무것도 저장하지 않는다")
    void interests_with_empty_list_saves_nothing() {
        // when: 빈 관심사 목록으로 호출
        onboardingService.interests(ANON_SESSION, new OnboardingInterestsCommand(List.of()));

        // then: 저장이 일어나지 않음
        then(anonSessionPersonaElementRepository).should(never()).save(any());
    }

    // ---------- profile photo ----------

    @Test
    @DisplayName("프로필 사진 presign은 익명 세션 id와 PROFILE 타입으로 presign을 위임하고 결과를 그대로 매핑한다")
    void profilePhotoPresign_delegates_and_maps() {
        // given: presign 서비스가 업로드 정보를 반환
        Instant expiresAt = Instant.parse("2026-07-26T00:05:00Z");
        given(presignService.presignPhoto(ANON_SESSION_ID, "image/jpeg", PhotoType.PROFILE))
                .willReturn(new PhotoPresignResult("https://s3.example.com/upload", "profile/1/abc", "PUT",
                        new RequiredHeaders("image/jpeg"), 10485760, expiresAt));

        // when: presign 요청
        OnboardingProfilePhotoPresignResult result = onboardingService.profilePhotoPresign(
                ANON_SESSION, new OnboardingProfilePhotoPresignCommand("image/jpeg"));

        // then: presign 결과가 그대로 매핑됨
        assertThat(result.uploadUrl()).isEqualTo("https://s3.example.com/upload");
        assertThat(result.key()).isEqualTo("profile/1/abc");
        assertThat(result.method()).isEqualTo("PUT");
        assertThat(result.requiredHeaders().contentType()).isEqualTo("image/jpeg");
        assertThat(result.maxBytes()).isEqualTo(10485760);
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("프로필 사진 commit 시 기존 사진이 없으면 key와 위치로 새 사진을 저장한다")
    void profilePhotoCommit_first_time_saves_photo() {
        // given: commit이 CDN URL을 반환하고 기존 프로필 사진은 없음
        given(photoCommitService.commitProfilePhoto(ANON_SESSION_ID, "profile/1/abc"))
                .willReturn(new PhotoCommitResult("https://cdn.example.com/profile/1/abc", 1024L));
        given(anonSessionPhotoRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, PhotoType.PROFILE))
                .willReturn(Optional.empty());

        // when: 프로필 사진 commit
        PhotoPosInfo position = new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 300, 400);
        OnboardingProfilePhotoCommitResult result = onboardingService.profilePhotoCommit(
                ANON_SESSION, new OnboardingProfilePhotoCommitCommand("profile/1/abc", position));

        // then: 새 사진이 key·좌표·크기와 함께 저장되고 CDN URL이 반환됨
        ArgumentCaptor<AnonSessionPhoto> captor = ArgumentCaptor.forClass(AnonSessionPhoto.class);
        then(anonSessionPhotoRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(captor.getValue().getType()).isEqualTo(PhotoType.PROFILE);
        assertThat(captor.getValue().getKey()).isEqualTo("profile/1/abc");
        assertThat(captor.getValue().getXPos()).isEqualTo(10);
        assertThat(captor.getValue().getYPos()).isEqualTo(20);
        assertThat(captor.getValue().getWidth()).isEqualTo(300);
        assertThat(captor.getValue().getHeight()).isEqualTo(400);
        assertThat(result.photoUrl()).isEqualTo("https://cdn.example.com/profile/1/abc");
        assertThat(result.position()).isEqualTo(position);
    }

    @Test
    @DisplayName("프로필 사진 commit 시 기존 사진이 있으면 새로 저장하지 않고 기존 행을 갱신한다")
    void profilePhotoCommit_existing_photo_is_updated() {
        // given: 이미 프로필 사진이 존재
        AnonSessionPhoto existing = AnonSessionPhoto.create(ANON_SESSION_ID, PhotoType.PROFILE, "profile/1/old", 0, 0, 100, 100);
        given(photoCommitService.commitProfilePhoto(ANON_SESSION_ID, "profile/1/new"))
                .willReturn(new PhotoCommitResult("https://cdn.example.com/profile/1/new", 1024L));
        given(anonSessionPhotoRepository.findByAnonSessionIdAndType(ANON_SESSION_ID, PhotoType.PROFILE))
                .willReturn(Optional.of(existing));

        // when: 새 key로 다시 commit
        PhotoPosInfo position = new PhotoPosInfo(new PhotoPosInfo.StartPos(5, 6), 200, 300);
        onboardingService.profilePhotoCommit(ANON_SESSION,
                new OnboardingProfilePhotoCommitCommand("profile/1/new", position));

        // then: 기존 사진이 갱신되고 저장은 호출되지 않음
        assertThat(existing.getKey()).isEqualTo("profile/1/new");
        assertThat(existing.getXPos()).isEqualTo(5);
        assertThat(existing.getYPos()).isEqualTo(6);
        assertThat(existing.getWidth()).isEqualTo(200);
        assertThat(existing.getHeight()).isEqualTo(300);
        then(anonSessionPhotoRepository).should(never()).save(any());
    }

    // ---------- nickname ----------

    @Test
    @DisplayName("닉네임 중복 확인은 유저·익명 세션 모두에 없으면 사용 가능으로 응답한다")
    void profileNicknameCheck_available() {
        // given: 유저와 다른 익명 세션 어디에도 같은 닉네임이 없음
        given(userRepository.existsByNickname("twinly")).willReturn(false);
        given(anonSessionRepository.existsByNicknameAndIdNot("twinly", ANON_SESSION_ID)).willReturn(false);

        // when: 닉네임 중복 확인
        OnboardingProfileNicknameCheckResult result = onboardingService.profileNicknameCheck(
                ANON_SESSION, new OnboardingProfileNicknameCheckCommand("twinly"));

        // then: 사용 가능
        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("닉네임 중복 확인은 자기 세션이 이미 쓰고 있는 닉네임이면 사용 가능으로 응답한다")
    void profileNicknameCheck_own_nickname_is_available() {
        // given: 자기 자신을 제외한 중복 검사에서는 걸리지 않음
        given(userRepository.existsByNickname("twinly")).willReturn(false);
        given(anonSessionRepository.existsByNicknameAndIdNot("twinly", ANON_SESSION_ID)).willReturn(false);

        // when: 자기가 이미 설정한 닉네임으로 중복 확인
        OnboardingProfileNicknameCheckResult result = onboardingService.profileNicknameCheck(
                ANON_SESSION, new OnboardingProfileNicknameCheckCommand("twinly"));

        // then: 사용 가능 (자기 닉네임에 자기가 막히지 않는다)
        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("닉네임 중복 확인은 이미 가입한 유저가 쓰고 있으면 사용 불가로 응답한다")
    void profileNicknameCheck_taken_by_user() {
        // given: 이미 유저가 사용 중인 닉네임
        given(userRepository.existsByNickname("twinly")).willReturn(true);

        // when: 닉네임 중복 확인
        OnboardingProfileNicknameCheckResult result = onboardingService.profileNicknameCheck(
                ANON_SESSION, new OnboardingProfileNicknameCheckCommand("twinly"));

        // then: 사용 불가
        assertThat(result.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("금지어가 포함된 닉네임은 대소문자와 무관하게 INVALID_NICKNAME 예외가 발생한다")
    void profileNicknameCheck_with_forbidden_word_throws() {
        // when & then: 대문자가 섞인 금지어(Admin)도 INVALID_NICKNAME으로 거절
        assertThatThrownBy(() -> onboardingService.profileNicknameCheck(
                ANON_SESSION, new OnboardingProfileNicknameCheckCommand("Admin전용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_NICKNAME);

        then(userRepository).should(never()).existsByNickname(any());
    }

    @Test
    @DisplayName("닉네임 설정은 앞뒤 공백을 제거한 값으로 익명 세션에 반영한다")
    void profileNickname_trims_and_updates_session() {
        // given: 중복이 없고 익명 세션이 존재
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plusSeconds(3600));
        given(userRepository.existsByNickname("twinly")).willReturn(false);
        given(anonSessionRepository.existsByNicknameAndIdNot("twinly", ANON_SESSION_ID)).willReturn(false);
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));

        // when: 앞뒤 공백이 있는 닉네임으로 설정
        onboardingService.profileNickname(ANON_SESSION, new OnboardingProfileNicknameCommand("  twinly  "));

        // then: 공백이 제거된 닉네임이 세션에 반영됨
        assertThat(anonSession.getNickname()).isEqualTo("twinly");
    }

    @Test
    @DisplayName("자기 세션이 이미 쓰고 있는 닉네임으로 다시 설정해도 성공한다 (PUT 멱등)")
    void profileNickname_same_nickname_twice_is_idempotent() {
        // given: 자기 자신을 제외한 중복 검사에서는 걸리지 않음
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plusSeconds(3600));
        anonSession.changeNickname("twinly");
        given(userRepository.existsByNickname("twinly")).willReturn(false);
        given(anonSessionRepository.existsByNicknameAndIdNot("twinly", ANON_SESSION_ID)).willReturn(false);
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));

        // when: 같은 닉네임으로 재요청
        onboardingService.profileNickname(ANON_SESSION, new OnboardingProfileNicknameCommand("twinly"));

        // then: 예외 없이 같은 값이 유지됨
        assertThat(anonSession.getNickname()).isEqualTo("twinly");
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임으로 설정하면 NICKNAME_ALREADY_USED 예외가 발생하고 세션을 조회하지 않는다")
    void profileNickname_when_already_used_throws() {
        // given: 다른 익명 세션이 이미 사용 중인 닉네임
        given(userRepository.existsByNickname("twinly")).willReturn(false);
        given(anonSessionRepository.existsByNicknameAndIdNot("twinly", ANON_SESSION_ID)).willReturn(true);

        // when & then: NICKNAME_ALREADY_USED 예외 발생 + 세션 조회 없음
        assertThatThrownBy(() -> onboardingService.profileNickname(
                ANON_SESSION, new OnboardingProfileNicknameCommand("twinly")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_USED);

        then(anonSessionRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("검사를 통과한 뒤 다른 요청이 같은 닉네임을 선점하면 NICKNAME_ALREADY_USED 예외로 변환된다")
    void profileNickname_when_lost_race_throws_already_used() {
        // given: 검사 시점에는 비어 있었지만, 저장 시점에 유니크 제약이 걸리는 상황
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plusSeconds(3600));
        given(userRepository.existsByNickname("twinly")).willReturn(false);
        given(anonSessionRepository.existsByNicknameAndIdNot("twinly", ANON_SESSION_ID)).willReturn(false);
        given(anonSessionRepository.findById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));
        given(anonSessionRepository.saveAndFlush(anonSession))
                .willThrow(new DataIntegrityViolationException("uk_anon_sessions_nickname"));

        // when & then: 500이 아니라 도메인 에러(409)로 나간다
        assertThatThrownBy(() -> onboardingService.profileNickname(
                ANON_SESSION, new OnboardingProfileNicknameCommand("twinly")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_USED);
    }

    // ---------- consents ----------

    @Test
    @DisplayName("동의 등록은 policyId+version으로 찾은 정책 id로 동의 이력을 저장한다")
    void grantConsents_saves_agreements() {
        // given: terms_of_service v1 정책이 존재하고, 기존 동의 이력은 없음
        PolicySummary policy = mock(PolicySummary.class);
        given(policy.getId()).willReturn(10L);
        given(policyCatalog.loadByKey(List.of("terms_of_service")))
                .willReturn(Map.of(new PolicyKey("terms_of_service", "1"), policy));
        given(anonSessionAgreementRepository.findAllByAnonSessionIdAndRevokedAtIsNull(ANON_SESSION_ID))
                .willReturn(List.of());

        // when: 동의 등록
        onboardingService.grantConsents(ANON_SESSION, new OnboardingGrantConsentsCommand(
                List.of(new OnboardingGrantConsentsItemCommand("terms_of_service", "1"))));

        // then: 해당 정책 id로 동의 이력이 저장됨
        ArgumentCaptor<List<AnonSessionAgreement>> captor = ArgumentCaptor.captor();
        then(anonSessionAgreementRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(captor.getValue().get(0).getPolicyId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("이미 동의한 정책은 동의 이력을 중복 저장하지 않는다")
    void grantConsents_skips_already_agreed_policy() {
        // given: 이미 policyId=10에 동의한 이력이 있음
        PolicySummary policy = mock(PolicySummary.class);
        given(policy.getId()).willReturn(10L);
        given(policyCatalog.loadByKey(List.of("terms_of_service")))
                .willReturn(Map.of(new PolicyKey("terms_of_service", "1"), policy));
        given(anonSessionAgreementRepository.findAllByAnonSessionIdAndRevokedAtIsNull(ANON_SESSION_ID))
                .willReturn(List.of(AnonSessionAgreement.create(ANON_SESSION_ID, 10L, Instant.now())));

        // when: 같은 정책에 다시 동의
        onboardingService.grantConsents(ANON_SESSION, new OnboardingGrantConsentsCommand(
                List.of(new OnboardingGrantConsentsItemCommand("terms_of_service", "1"))));

        // then: 저장 대상이 비어 있음 (멱등)
        ArgumentCaptor<List<AnonSessionAgreement>> captor = ArgumentCaptor.captor();
        then(anonSessionAgreementRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 정책·버전으로 동의하면 POLICY_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void grantConsents_when_policy_not_found_throws() {
        // given: 카탈로그에 해당 정책 버전이 없음
        given(policyCatalog.loadByKey(List.of("terms_of_service"))).willReturn(Map.of());
        given(anonSessionAgreementRepository.findAllByAnonSessionIdAndRevokedAtIsNull(ANON_SESSION_ID))
                .willReturn(List.of());

        // when & then: POLICY_NOT_FOUND 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> onboardingService.grantConsents(ANON_SESSION, new OnboardingGrantConsentsCommand(
                List.of(new OnboardingGrantConsentsItemCommand("terms_of_service", "99")))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);

        then(anonSessionAgreementRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("동의 철회는 선택 정책이면 이전 버전까지 포함해 철회를 위임한다")
    void revokeConsents_revokes_optional_policy() {
        // given: 선택(비필수) 정책 marketing v2
        PolicySummary policy = mock(PolicySummary.class);
        given(policy.getId()).willReturn(20L);
        given(policy.getIsRequired()).willReturn(false);
        given(policyCatalog.loadByKey(List.of("marketing")))
                .willReturn(Map.of(new PolicyKey("marketing", "2"), policy));

        // when: 동의 철회
        onboardingService.revokeConsents(ANON_SESSION, new OnboardingRevokeConsentsCommand(
                List.of(new OnboardingRevokeConsentsItemCommand("marketing", "2"))));

        // then: 해당 정책 id로 철회 쿼리에 위임
        then(anonSessionAgreementRepository).should()
                .revokeWithPreviousVersionsByAnonSessionIdAndPolicyIdIn(ANON_SESSION_ID, List.of(20L));
    }

    @Test
    @DisplayName("필수 정책을 철회하려 하면 REQUIRED_POLICY_REVOKE_DENIED 예외가 발생하고 철회하지 않는다")
    void revokeConsents_required_policy_throws() {
        // given: 필수 정책 terms_of_service v1
        PolicySummary policy = mock(PolicySummary.class);
        given(policy.getIsRequired()).willReturn(true);
        given(policyCatalog.loadByKey(List.of("terms_of_service")))
                .willReturn(Map.of(new PolicyKey("terms_of_service", "1"), policy));

        // when & then: REQUIRED_POLICY_REVOKE_DENIED 예외 발생 + 철회 쿼리 호출 안 함
        assertThatThrownBy(() -> onboardingService.revokeConsents(ANON_SESSION, new OnboardingRevokeConsentsCommand(
                List.of(new OnboardingRevokeConsentsItemCommand("terms_of_service", "1")))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_POLICY_REVOKE_DENIED);

        then(anonSessionAgreementRepository).should(never())
                .revokeWithPreviousVersionsByAnonSessionIdAndPolicyIdIn(anyLong(), anyList());
    }

    @Test
    @DisplayName("존재하지 않는 정책을 철회 요청하면 POLICY_NOT_FOUND 예외가 발생한다 (등록 API와 대칭)")
    void revokeConsents_unknown_policy_throws() {
        // given: 카탈로그에 해당 정책 버전이 없음
        given(policyCatalog.loadByKey(List.of("unknown"))).willReturn(Map.of());

        // when & then: 마스터 데이터에 없는 정책이므로 조용히 무시하지 않고 404로 거절
        assertThatThrownBy(() -> onboardingService.revokeConsents(ANON_SESSION,
                new OnboardingRevokeConsentsCommand(List.of(new OnboardingRevokeConsentsItemCommand("unknown", "1")))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);

        then(anonSessionAgreementRepository).should(never())
                .revokeWithPreviousVersionsByAnonSessionIdAndPolicyIdIn(anyLong(), anyList());
    }

    private SurveyQuestion question(Integer id) {
        return new SurveyQuestion(id, PersonaDimension.OPENNESS, "시나리오", Map.of(
                SurveyOptionName.A, new SurveyOption("A 라벨", "A 특성"),
                SurveyOptionName.B, new SurveyOption("B 라벨", "B 특성")));
    }

    @Test
    @DisplayName("닉네임이 길이(2~20자)나 허용 문자 규칙을 벗어나면 INVALID_NICKNAME 예외가 발생한다")
    void profileNickname_when_violates_policy_throws() {
        // given: 규칙을 벗어나는 닉네임들 (한 글자 / 21자 / 허용되지 않는 문자)
        List<String> invalid = List.of("a", "a".repeat(21), "twin ly", "twin!ly");

        // when & then: 모두 INVALID_NICKNAME으로 거절되고 중복 검사까지 가지 않는다
        for (String nickname : invalid) {
            assertThatThrownBy(() -> onboardingService.profileNickname(
                    ANON_SESSION, new OnboardingProfileNicknameCommand(nickname)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_NICKNAME);
        }
        then(anonSessionRepository).should(never()).findById(anyLong());
    }
}
