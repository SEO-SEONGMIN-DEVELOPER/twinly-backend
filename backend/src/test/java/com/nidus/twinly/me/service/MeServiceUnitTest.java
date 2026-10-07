package com.nidus.twinly.me.service;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersion;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.feedback.FeedbackOption;
import com.nidus.twinly.common.feedback.FeedbackOptionLoader;
import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.persona.PersonalityType;
import com.nidus.twinly.common.persona.PersonalityTypeCalculator;
import com.nidus.twinly.common.persona.PersonalityTypeLoader;
import com.nidus.twinly.common.persona.PersonalityTypePart;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
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
import com.nidus.twinly.common.tendency.TendencyLoader;
import com.nidus.twinly.common.tendency.TendencyOption;
import com.nidus.twinly.common.tendency.TendencyQuestion;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
import com.nidus.twinly.support.TestPolicySummary;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import com.nidus.twinly.legal.service.PolicyUrlResolver;
import com.nidus.twinly.me.domain.HesitationDuration;
import com.nidus.twinly.me.domain.HesitationStatus;
import com.nidus.twinly.me.dto.command.MeAppNotificationsReadAllCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileNicknameCommand;
import com.nidus.twinly.me.dto.command.MeCheckProfileNicknameCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileVisibilitySettingCommand;
import com.nidus.twinly.me.dto.command.MeChangePushNotificationsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsItemCommand;
import com.nidus.twinly.me.dto.command.MeHesitationsAnswerCommand;
import com.nidus.twinly.me.dto.command.MeInterestsCommand;
import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsItemCommand;
import com.nidus.twinly.me.dto.command.MeSendFeedbackCommand;
import com.nidus.twinly.me.dto.command.MeSubmitTendencyAnswerCommand;
import com.nidus.twinly.me.dto.command.MeSurveyAnswerCommand;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsChatTargetResult;
import com.nidus.twinly.me.dto.result.MeCheckProfileNicknameResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsProfileTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsResult;
import com.nidus.twinly.me.dto.result.MeConsentsResult;
import com.nidus.twinly.me.dto.result.MeFeedbackOptionsItemResult;
import com.nidus.twinly.me.dto.result.MeFeedbackOptionsResult;
import com.nidus.twinly.me.dto.result.MeHesitationsResult;
import com.nidus.twinly.me.dto.result.MePersonalityTypePartResult;
import com.nidus.twinly.me.dto.result.MePersonalityTypeResult;
import com.nidus.twinly.me.dto.result.MeProfileEditViewResult;
import com.nidus.twinly.me.dto.result.MeProfileResult;
import com.nidus.twinly.me.dto.result.MeProfileV2Result;
import com.nidus.twinly.me.dto.result.MeProfilePhotoCommitResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoPresignResult;
import com.nidus.twinly.me.dto.result.MeProfileVisibilitySettingsResult;
import com.nidus.twinly.me.dto.result.MePurchasesResult;
import com.nidus.twinly.me.dto.result.MePushNotificationsResult;
import com.nidus.twinly.me.dto.result.MeStatusPersonaResult;
import com.nidus.twinly.me.dto.result.MeStatusResult;
import com.nidus.twinly.me.dto.result.MeTendencyQuestionsItemResult;
import com.nidus.twinly.me.dto.result.MeTendencyQuestionsOptionResult;
import com.nidus.twinly.me.dto.result.MeTendencyQuestionsResult;
import com.nidus.twinly.me.dto.result.MeWithdrawResult;
import com.nidus.twinly.me.event.FeedbackSentEvent;
import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationChannel;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.entity.NotificationSetting;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.notification.repository.NotificationSettingRepository;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.entity.Report;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.domain.Intimacy;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.report.repository.ReportRepository;
import com.nidus.twinly.season.writer.SeasonParticipationWriter;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.UserFeedback;
import com.nidus.twinly.user.entity.UserFeedbackOption;
import com.nidus.twinly.user.entity.UserSurveyAnswer;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserFeedbackOptionRepository;
import com.nidus.twinly.user.repository.UserFeedbackRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import com.nidus.twinly.user.repository.UserTendencyAnswerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MeServiceUnitTest {

    private static final Long ME = 1L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    PresignService presignService;

    @Mock
    PhotoCommitService photoCommitService;

    @Mock
    ProfileThumbnailService profileThumbnailService;

    @Mock
    CloudFrontService cloudFrontService;

    @Mock
    BlindIndexHasher blindIndexHasher;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    AnonSessionRepository anonSessionRepository;

    @Mock
    PolicyNameRepository policyNameRepository;

    @Mock
    AgreementRepository agreementRepository;

    @Mock
    NotificationSettingRepository notificationSettingRepository;

    @Mock
    DisclosureAgreementRepository disclosureAgreementRepository;

    @Mock
    AppNotificationFeedRepository appNotificationFeedRepository;

    @Mock
    ReportRepository reportRepository;

    @Mock
    QuestionRepository questionRepository;

    @Mock
    PolicyCatalog policyCatalog;

    @Mock
    PolicyUrlResolver policyUrlResolver;

    @Mock
    SeasonParticipationWriter seasonParticipationWriter;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    EncounterRepository encounterRepository;

    @Mock
    IntimacyReader intimacyReader;

    @Mock
    UserSurveyAnswerRepository userSurveyAnswerRepository;

    @Mock
    SurveyLoader surveyLoader;

    @Mock
    PersonalityTypeCalculator personalityTypeCalculator;

    @Mock
    PersonalityTypeLoader personalityTypeLoader;

    @Mock
    UserTendencyAnswerRepository userTendencyAnswerRepository;

    @Mock
    TendencyLoader tendencyLoader;

    @Mock
    UserFeedbackRepository userFeedbackRepository;

    @Mock
    UserFeedbackOptionRepository userFeedbackOptionRepository;

    @Mock
    FeedbackOptionLoader feedbackOptionLoader;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    MeService meService;

    // ---------------------------------------------------------------- 프로필 사진

    @Test
    @DisplayName("프로필 사진 presign은 PROFILE 타입으로 presign 서비스에 위임하고 결과를 그대로 변환한다")
    void profilePhotoPresign_delegates_and_maps() {
        // given: presign 서비스가 업로드 정보를 반환
        Instant expiresAt = Instant.now().plusSeconds(300);
        given(presignService.presignPhoto(ME, "image/png", PhotoType.PROFILE))
                .willReturn(new PhotoPresignResult("https://s3/upload", "profile/1/uuid", "PUT",
                        new RequiredHeaders("image/png"), 10485760, expiresAt));

        // when: presign 요청
        MeProfilePhotoPresignResult result = meService.profilePhotoPresign(ME, new MeProfilePhotoPresignCommand("image/png"));

        // then: presign 결과가 그대로 매핑됨
        assertThat(result.uploadUrl()).isEqualTo("https://s3/upload");
        assertThat(result.key()).isEqualTo("profile/1/uuid");
        assertThat(result.method()).isEqualTo("PUT");
        assertThat(result.requiredHeaders().contentType()).isEqualTo("image/png");
        assertThat(result.maxBytes()).isEqualTo(10485760);
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("프로필 사진 commit 시 기존 사진이 있으면 새로 저장하지 않고 key·위치만 변경한다")
    void profilePhotoCommit_updates_existing_photo() {
        // given: 이미 프로필 사진이 등록된 상태
        given(photoCommitService.commitProfilePhoto(ME, "profile/1/new")).willReturn(new PhotoCommitResult("https://cdn/new.jpg", 1024L));
        Photo photo = Photo.create(ME, PhotoType.PROFILE, "profile/1/old", 0, 0, 100, 100, Instant.now());
        given(photoRepository.findByUserIdAndType(ME, PhotoType.PROFILE)).willReturn(Optional.of(photo));

        // when: 새 key로 commit
        PhotoPosInfo position = new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 300, 400);
        MeProfilePhotoCommitResult result = meService.profilePhotoCommit(ME, new MeProfilePhotoCommitCommand("profile/1/new", position));

        // then: 기존 엔티티만 갱신되고 저장은 일어나지 않음
        assertThat(result.photoUrl()).isEqualTo("https://cdn/new.jpg");
        assertThat(result.position()).isEqualTo(position);
        assertThat(photo.getKey()).isEqualTo("profile/1/new");
        assertThat(photo.getXPos()).isEqualTo(10);
        assertThat(photo.getYPos()).isEqualTo(20);
        assertThat(photo.getWidth()).isEqualTo(300);
        assertThat(photo.getHeight()).isEqualTo(400);
        then(photoRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("프로필 사진 commit 시 기존 사진이 없으면 PROFILE 타입 사진을 새로 저장한다")
    void profilePhotoCommit_saves_new_photo() {
        // given: 등록된 프로필 사진이 없는 상태
        given(photoCommitService.commitProfilePhoto(ME, "profile/1/new")).willReturn(new PhotoCommitResult("https://cdn/new.jpg", 1024L));
        given(photoRepository.findByUserIdAndType(ME, PhotoType.PROFILE)).willReturn(Optional.empty());

        // when: commit
        PhotoPosInfo position = new PhotoPosInfo(new PhotoPosInfo.StartPos(5, 6), 200, 300);
        meService.profilePhotoCommit(ME, new MeProfilePhotoCommitCommand("profile/1/new", position));

        // then: userId·PROFILE·key·위치로 새 Photo 저장
        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        then(photoRepository).should().save(captor.capture());
        Photo saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(ME);
        assertThat(saved.getType()).isEqualTo(PhotoType.PROFILE);
        assertThat(saved.getKey()).isEqualTo("profile/1/new");
        assertThat(saved.getXPos()).isEqualTo(5);
        assertThat(saved.getYPos()).isEqualTo(6);
        assertThat(saved.getWidth()).isEqualTo(200);
        assertThat(saved.getHeight()).isEqualTo(300);
    }

    // ---------------------------------------------------------------- 탈퇴 / 복구

    @Test
    @DisplayName("탈퇴 신청 시 유저가 없으면 USER_NOT_FOUND 예외가 발생한다")
    void withdraw_user_not_found_throws() {
        // given: 유저가 존재하지 않음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> meService.withdraw(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴 신청 멱등: 이미 신청된 유저가 다시 신청하면 기존 예정 시각을 그대로 반환한다")
    void withdraw_already_requested_is_idempotent() {
        // given: 하루 전에 탈퇴를 신청해 예정 시각이 이미 정해진 유저
        User user = user();
        Instant scheduledAt = Instant.now().plus(Duration.ofDays(14));
        ReflectionTestUtils.setField(user, "withdrawalRequestedAt", Instant.now().minus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(user, "withdrawalScheduledAt", scheduledAt);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));

        // when: 같은 유저가 탈퇴를 다시 신청
        MeWithdrawResult result = meService.withdraw(ME);

        // then: 예외 없이 기존 예정 시각이 반환되고, 신청 시각도 갱신되지 않는다
        assertThat(result.recoverableUntil()).isEqualTo(scheduledAt);
        assertThat(user.getWithdrawalScheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    @DisplayName("탈퇴 신청 시 신청 시각과 15일 뒤 예정 시각이 기록되고 복구 마감 시각을 반환한다")
    void withdraw_success_sets_schedule() {
        // given: 탈퇴 이력이 없는 유저
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));

        // when: 탈퇴 신청
        MeWithdrawResult result = meService.withdraw(ME);

        // then: 신청 시각 기록 + 15일 뒤가 복구 마감 시각
        assertThat(user.getWithdrawalRequestedAt()).isNotNull();
        assertThat(result.recoverableUntil()).isEqualTo(user.getWithdrawalScheduledAt());
        assertThat(Duration.between(user.getWithdrawalRequestedAt(), result.recoverableUntil()))
                .isEqualTo(Duration.ofDays(15));
    }

    @Test
    @DisplayName("탈퇴 신청하지 않은 유저가 복구를 요청하면 아무 변화 없이 통과한다 (멱등)")
    void restore_when_not_requested_is_noop() {
        // given: 탈퇴 신청 이력이 없는 유저
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));

        // when: 복구 요청
        meService.restore(ME);

        // then: 상태 변화 없음
        assertThat(user.getWithdrawalRequestedAt()).isNull();
    }

    @Test
    @DisplayName("복구 가능 기간(15일)이 지난 뒤 복구를 요청하면 WITHDRAWAL_RECOVERY_EXPIRED 예외가 발생한다")
    void restore_after_period_throws() {
        // given: 16일 전에 탈퇴 신청한 유저
        User user = user();
        Instant requestedAt = Instant.now().minus(Duration.ofDays(16));
        ReflectionTestUtils.setField(user, "withdrawalRequestedAt", requestedAt);
        ReflectionTestUtils.setField(user, "withdrawalScheduledAt", requestedAt.plus(Duration.ofDays(15)));
        given(userRepository.findById(ME)).willReturn(Optional.of(user));

        // when & then: WITHDRAWAL_RECOVERY_EXPIRED 예외 발생 + 신청 시각 유지
        assertThatThrownBy(() -> meService.restore(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_RECOVERY_EXPIRED);
        assertThat(user.getWithdrawalRequestedAt()).isEqualTo(requestedAt);
    }

    @Test
    @DisplayName("복구 가능 기간 내에 복구를 요청하면 탈퇴 신청 시각이 초기화된다")
    void restore_within_period_cancels_withdrawal() {
        // given: 하루 전에 탈퇴 신청한 유저
        User user = user();
        Instant requestedAt = Instant.now().minus(Duration.ofDays(1));
        ReflectionTestUtils.setField(user, "withdrawalRequestedAt", requestedAt);
        ReflectionTestUtils.setField(user, "withdrawalScheduledAt", requestedAt.plus(Duration.ofDays(15)));
        given(userRepository.findById(ME)).willReturn(Optional.of(user));

        // when: 복구 요청
        meService.restore(ME);

        // then: 탈퇴 신청 시각이 null로 초기화
        assertThat(user.getWithdrawalRequestedAt()).isNull();
    }

    @Test
    @DisplayName("복구한 뒤 내 상태를 조회하면 복구 마감 시각이 남아 있지 않다")
    void restore_clears_recoverable_until() {
        // given: 하루 전에 탈퇴 신청한 유저
        User user = user();
        Instant requestedAt = Instant.now().minus(Duration.ofDays(1));
        ReflectionTestUtils.setField(user, "withdrawalRequestedAt", requestedAt);
        ReflectionTestUtils.setField(user, "withdrawalScheduledAt", requestedAt.plus(Duration.ofDays(15)));
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(reportRepository.findAllByReportedUserIdAndStatus(ME, ReportStatus.RESOLVED)).willReturn(List.of());

        // when: 복구 후 내 상태 조회
        meService.restore(ME);
        MeStatusResult result = meService.status(ME);

        // then: 탈퇴 여부와 복구 마감 시각이 함께 초기화
        assertThat(result.withdrawal().isDeleted()).isFalse();
        assertThat(result.withdrawal().recoverableUntil()).isNull();
    }

    // ---------------------------------------------------------------- 프로필

    @Test
    @DisplayName("프로필 수정 화면 조회 시 프로필 사진이 있으면 CloudFront 서명 URL과 크롭 위치를 함께 반환한다")
    void profileEditView_with_photo() {
        // given: 유저와 프로필 사진이 모두 존재
        User user = user();
        ReflectionTestUtils.setField(user, "id", ME);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        Photo photo = Photo.create(ME, PhotoType.PROFILE, "profile/1/key", 10, 20, 100, 200, Instant.now());
        given(photoRepository.findByUserIdAndType(ME, PhotoType.PROFILE)).willReturn(Optional.of(photo));
        given(cloudFrontService.getSignedUrl("profile/1/key")).willReturn("https://cdn/signed.jpg");
        given(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(ME, PersonaDimension.INTEREST)).willReturn(List.of(
                personaElement(PersonaDimension.INTEREST, "등산"),
                personaElement(PersonaDimension.INTEREST, "영화")));

        // when: 프로필 수정 화면 조회
        MeProfileEditViewResult result = meService.profileEditView(ME);

        // then: 유저 정보 + 서명 URL 반환
        assertThat(result.userId()).isEqualTo(ME);
        assertThat(result.nickname()).isEqualTo("nick");
        assertThat(result.familyName()).isEqualTo("홍");
        assertThat(result.givenName()).isEqualTo("길동");
        assertThat(result.affiliation()).isEqualTo("니두스");
        assertThat(result.affiliationNumber()).isEqualTo("2020123");
        assertThat(result.birthDate()).isEqualTo("2000-01-01");
        assertThat(result.profilePhoto().photoUrl()).isEqualTo("https://cdn/signed.jpg");
        assertThat(result.profilePhoto().position())
                .isEqualTo(new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200));
        assertThat(result.interests()).containsExactly("등산", "영화");
    }

    @Test
    @DisplayName("프로필 수정 화면 조회 시 프로필 사진이 없으면 profilePhoto는 null이다")
    void profileEditView_without_photo() {
        // given: 유저는 있으나 프로필 사진이 없음
        User user = user();
        ReflectionTestUtils.setField(user, "id", ME);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(photoRepository.findByUserIdAndType(ME, PhotoType.PROFILE)).willReturn(Optional.empty());

        // when: 프로필 수정 화면 조회
        MeProfileEditViewResult result = meService.profileEditView(ME);

        // then: profilePhoto는 null이고 CloudFront는 호출되지 않음
        assertThat(result.profilePhoto()).isNull();
        then(cloudFrontService).should(never()).getSignedUrl(any());
    }

    @Test
    @DisplayName("프로필 수정 시 유저가 없으면 USER_NOT_FOUND 예외가 발생한다")
    void profile_user_not_found_throws() {
        // given: 유저가 존재하지 않음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> meService.profile(ME, new MeProfileCommand("새소속", List.of("등산"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("프로필 수정 시 소속과 함께 블라인드 인덱스 해시도 갱신한다")
    void profile_updates_affiliation_with_hash() {
        // given: 유저 존재 + 해시 생성기 스텁
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(blindIndexHasher.hash("새소속")).willReturn("newAffHash");

        // when: 소속 변경
        meService.profile(ME, new MeProfileCommand("새소속", List.of("등산")));

        // then: 소속과 해시가 함께 갱신됨
        assertThat(user.getAffiliation()).isEqualTo("새소속");
        assertThat(user.getAffiliationHash()).isEqualTo("newAffHash");
    }

    @Test
    @DisplayName("프로필 수정 시 기존 관심사를 모두 지우고 요청한 관심사를 순서대로 저장한다")
    void profile_replaces_interests() {
        // given: 유저 존재
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(blindIndexHasher.hash("새소속")).willReturn("newAffHash");

        // when: 관심사 2개로 수정
        meService.profile(ME, new MeProfileCommand("새소속", List.of("등산", "영화")));

        // then: INTEREST 차원 삭제 후 요청 순서대로 저장
        InOrder inOrder = inOrder(personaElementRepository);
        inOrder.verify(personaElementRepository).deleteByUserIdAndDimension(ME, PersonaDimension.INTEREST);
        ArgumentCaptor<PersonaElement> captor = ArgumentCaptor.forClass(PersonaElement.class);
        inOrder.verify(personaElementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PersonaElement::getUserId, PersonaElement::getDimension, PersonaElement::getExplanation)
                .containsExactly(
                        tuple(ME, PersonaDimension.INTEREST, "등산"),
                        tuple(ME, PersonaDimension.INTEREST, "영화"));
    }

    @Test
    @DisplayName("프로필 수정 시 관심사가 빈 배열이면 기존 관심사만 지우고 아무것도 저장하지 않는다")
    void profile_with_empty_interests_only_deletes() {
        // given: 유저 존재
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(blindIndexHasher.hash("새소속")).willReturn("newAffHash");

        // when: 빈 관심사로 수정
        meService.profile(ME, new MeProfileCommand("새소속", List.of()));

        // then: 삭제만 수행되고 저장은 없음
        then(personaElementRepository).should().deleteByUserIdAndDimension(ME, PersonaDimension.INTEREST);
        then(personaElementRepository).should(never()).save(any());
    }

    // ---------------------------------------------------------------- 약관 동의

    @Test
    @DisplayName("약관 동의 목록은 카탈로그가 고른 노출 버전 기준으로 동의 여부를 판정한다")
    void consents_uses_catalog_version_to_resolve_agreement() {
        // given: 카탈로그가 이용약관 v2·마케팅 v1을 노출 버전으로 돌려주고, 유저는 v2에 동의한 상태
        Instant now = Instant.now();
        PolicyName tos = policyName(1L, "서비스 이용약관", "terms_of_service");
        PolicyName marketing = policyName(2L, "마케팅 수신 동의", "marketing");
        given(policyNameRepository.findAllByIsDeprecatedFalseOrderByIdAsc()).willReturn(List.of(tos, marketing));

        PolicySummary tosV2 = policy(11L, 1L, "2", true, now.minus(Duration.ofDays(1)));
        PolicySummary marketingV1 = policy(20L, 2L, "1", false, now.minus(Duration.ofDays(5)));
        given(policyCatalog.loadLatestByPolicyNameId(List.of(1L, 2L)))
                .willReturn(Map.of(1L, tosV2, 2L, marketingV1));
        given(policyUrlResolver.resolve("terms_of_service")).willReturn("https://trytwinly.com/legal/terms_of_service/");
        given(policyUrlResolver.resolve("marketing")).willReturn("https://trytwinly.com/legal/marketing/");

        Instant agreedAt = now.minus(Duration.ofHours(3));
        given(agreementRepository.findAllByUserIdAndRevokedAtIsNull(ME))
                .willReturn(List.of(Agreement.create(ME, 11L, agreedAt)));

        // when: 약관 동의 목록 조회
        MeConsentsResult result = meService.consents(ME);

        // then: 이용약관은 v2(동의함), 마케팅은 v1(미동의)로 매핑
        assertThat(result.consents()).hasSize(2);
        assertThat(result.consents().get(0).policyId()).isEqualTo("terms_of_service");
        assertThat(result.consents().get(0).title()).isEqualTo("서비스 이용약관");
        assertThat(result.consents().get(0).version()).isEqualTo("2");
        assertThat(result.consents().get(0).url()).isEqualTo("https://trytwinly.com/legal/terms_of_service/");
        assertThat(result.consents().get(0).isRequired()).isTrue();
        assertThat(result.consents().get(0).isGranted()).isTrue();
        assertThat(result.consents().get(0).grantedAt()).isEqualTo(agreedAt);
        assertThat(result.consents().get(1).policyId()).isEqualTo("marketing");
        assertThat(result.consents().get(1).version()).isEqualTo("1");
        assertThat(result.consents().get(1).isGranted()).isFalse();
        assertThat(result.consents().get(1).grantedAt()).isNull();
    }

    @Test
    @DisplayName("약관 동의 시 카탈로그에 없는 policyId·version 조합이면 POLICY_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void grantConsents_unknown_policy_throws() {
        // given: 카탈로그에 해당 버전이 없음
        given(policyCatalog.loadByKey(List.of("terms_of_service"))).willReturn(Map.of());
        given(agreementRepository.findAllByUserIdAndRevokedAtIsNull(ME)).willReturn(List.of());

        // when & then: POLICY_NOT_FOUND 예외 발생 + 저장 안 함
        MeGrantConsentsCommand command = new MeGrantConsentsCommand(List.of(new MeGrantConsentsItemCommand("terms_of_service", "9")));
        assertThatThrownBy(() -> meService.grantConsents(ME, command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);
        then(agreementRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("약관 동의 시 이미 동의한 정책은 제외하고 새로 동의한 정책만 저장한다")
    void grantConsents_skips_already_agreed() {
        // given: 이용약관 v2는 이미 동의, 마케팅 v1은 미동의
        Instant now = Instant.now();
        PolicySummary tosV2 = policy(11L, 1L, "2", true, now.minus(Duration.ofDays(1)));
        PolicySummary marketingV1 = policy(20L, 2L, "1", false, now.minus(Duration.ofDays(5)));
        given(policyCatalog.loadByKey(List.of("terms_of_service", "marketing")))
                .willReturn(Map.of(new PolicyKey("terms_of_service", "2"), tosV2,
                        new PolicyKey("marketing", "1"), marketingV1));
        given(agreementRepository.findAllByUserIdAndRevokedAtIsNull(ME))
                .willReturn(List.of(Agreement.create(ME, 11L, now.minus(Duration.ofHours(1)))));

        // when: 두 약관에 동의 요청
        meService.grantConsents(ME, new MeGrantConsentsCommand(List.of(
                new MeGrantConsentsItemCommand("terms_of_service", "2"),
                new MeGrantConsentsItemCommand("marketing", "1"))));

        // then: 마케팅 정책에 대한 Agreement만 저장
        ArgumentCaptor<List<Agreement>> captor = ArgumentCaptor.forClass(List.class);
        then(agreementRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getUserId()).isEqualTo(ME);
        assertThat(captor.getValue().get(0).getPolicyId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("약관 동의 후 현재 시즌 참가 조건을 다시 확인한다 (결제 후 늦게 동의한 유저도 참여 중으로 이어진다)")
    void grantConsents_rechecks_season_participation() {
        // given: 실신원 동의 v1.1에 새로 동의
        PolicySummary disclosureV11 = policy(30L, 3L, "1.1", true, Instant.now().minus(Duration.ofDays(1)));
        given(policyCatalog.loadByKey(List.of("thirdPartyRealIdentityDisclosure")))
                .willReturn(Map.of(new PolicyKey("thirdPartyRealIdentityDisclosure", "1.1"), disclosureV11));
        given(agreementRepository.findAllByUserIdAndRevokedAtIsNull(ME)).willReturn(List.of());

        // when: 동의 요청
        meService.grantConsents(ME, new MeGrantConsentsCommand(List.of(
                new MeGrantConsentsItemCommand("thirdPartyRealIdentityDisclosure", "1.1"))));

        // then: 동의를 저장한 뒤 참가 조건 확인을 위임한다
        InOrder inOrder = inOrder(agreementRepository, seasonParticipationWriter);
        inOrder.verify(agreementRepository).saveAll(any());
        inOrder.verify(seasonParticipationWriter).participateInCurrentSeasonIfEligible(ME);
    }

    @Test
    @DisplayName("필수 약관을 철회하려 하면 REQUIRED_POLICY_REVOKE_DENIED 예외가 발생하고 철회 쿼리를 실행하지 않는다")
    void revokeConsents_required_policy_throws() {
        // given: 철회 대상에 필수 약관이 포함
        PolicySummary tosV2 = policy(11L, 1L, "2", true, Instant.now().minus(Duration.ofDays(1)));
        given(policyCatalog.loadByKey(List.of("terms_of_service")))
                .willReturn(Map.of(new PolicyKey("terms_of_service", "2"), tosV2));

        // when & then: REQUIRED_POLICY_REVOKE_DENIED 예외 발생 + 철회 쿼리 미실행
        MeRevokeConsentsCommand command = new MeRevokeConsentsCommand(List.of(new MeRevokeConsentsItemCommand("terms_of_service", "2")));
        assertThatThrownBy(() -> meService.revokeConsents(ME, command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_POLICY_REVOKE_DENIED);
        then(agreementRepository).should(never()).revokeWithPreviousVersionsByUserIdAndPolicyIdIn(anyLong(), anyList());
    }

    @Test
    @DisplayName("선택 약관만 철회하면 해당 정책 id로 이전 버전까지 철회하도록 위임한다")
    void revokeConsents_optional_policy_delegates() {
        // given: 철회 대상이 선택 약관 하나
        PolicySummary marketingV1 = policy(20L, 2L, "1", false, Instant.now().minus(Duration.ofDays(5)));
        given(policyCatalog.loadByKey(List.of("marketing")))
                .willReturn(Map.of(new PolicyKey("marketing", "1"), marketingV1));

        // when: 철회 요청
        meService.revokeConsents(ME, new MeRevokeConsentsCommand(List.of(new MeRevokeConsentsItemCommand("marketing", "1"))));

        // then: 해당 정책 id로 철회 위임
        then(agreementRepository).should().revokeWithPreviousVersionsByUserIdAndPolicyIdIn(ME, List.of(20L));
    }

    @Test
    @DisplayName("철회 대상 정책을 카탈로그에서 찾지 못하면 POLICY_NOT_FOUND 예외가 발생한다 (등록 API와 대칭)")
    void revokeConsents_unknown_policy_throws() {
        // given: 카탈로그에 해당 버전이 없음
        given(policyCatalog.loadByKey(List.of("marketing"))).willReturn(Map.of());

        // when & then: 마스터 데이터에 없는 정책이므로 조용히 무시하지 않고 404로 거절
        assertThatThrownBy(() -> meService.revokeConsents(ME,
                new MeRevokeConsentsCommand(List.of(new MeRevokeConsentsItemCommand("marketing", "9")))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);

        then(agreementRepository).should(never()).revokeWithPreviousVersionsByUserIdAndPolicyIdIn(anyLong(), anyList());
    }

    // ---------------------------------------------------------------- 푸시 알림 설정

    @Test
    @DisplayName("푸시 알림 설정 조회 시 저장된 설정이 없는 타입은 기본값 true로 채운다")
    void pushNotifications_defaults_to_true() {
        // given: EVENT만 off로 저장된 상태
        given(notificationSettingRepository.findAllByUserIdAndChannel(ME, NotificationChannel.PUSH))
                .willReturn(List.of(NotificationSetting.create(ME, NotificationChannel.PUSH, NotificationType.EVENT, false)));

        // when: 푸시 알림 설정 조회
        MePushNotificationsResult result = meService.pushNotifications(ME);

        // then: EVENT는 false, 나머지는 기본값 true
        assertThat(result.pushNotificationSettings().event()).isFalse();
        assertThat(result.pushNotificationSettings().chat()).isTrue();
        assertThat(result.pushNotificationSettings().marketing()).isTrue();
    }

    @Test
    @DisplayName("푸시 알림 설정 변경은 조회 없이 upsert 한 번으로 위임한다")
    void changePushNotifications_delegates_to_upsert() {
        // when: CHAT 설정을 off로 변경
        meService.changePushNotifications(ME, NotificationType.CHAT, new MeChangePushNotificationsCommand(false));

        // then: 조회-후-저장이 아니라 원자적 upsert 한 번 (동시 요청이 유니크 제약을 위반하지 않는다)
        then(notificationSettingRepository).should()
                .upsertEnabled(ME, NotificationChannel.PUSH.name(), NotificationType.CHAT.name(), false);
        then(notificationSettingRepository).should(never()).findByUserIdAndChannelAndType(any(), any(), any());
        then(notificationSettingRepository).should(never()).save(any());
    }

    // ---------------------------------------------------------------- 프로필 공개 설정

    @Test
    @DisplayName("프로필 공개 설정 조회 시 동의 기록이 있는 항목만 공개로 표시한다")
    void profileVisibility_maps_agreed_fields() {
        // given: AFFILIATION만 공개 동의된 상태
        given(disclosureAgreementRepository.findAllByUserId(ME))
                .willReturn(List.of(DisclosureAgreement.create(ME, DisclosureField.AFFILIATION)));

        // when: 공개 설정 조회
        MeProfileVisibilitySettingsResult result = meService.profileVisibilitySettings(ME);

        // then: AFFILIATION만 true
        assertThat(result.affiliationVisible()).isTrue();
        assertThat(result.affiliationNumberVisible()).isFalse();
    }

    @Test
    @DisplayName("프로필 항목을 공개로 바꾸면 조회 없이 upsert 한 번으로 위임한다")
    void changeProfileVisibility_visible_delegates_to_upsert() {
        // when: 공개로 변경
        meService.changeProfileVisibilitySetting(ME, DisclosureField.AFFILIATION, new MeChangeProfileVisibilitySettingCommand(true));

        // then: 이미 공개된 경우에도 같은 호출이라 재요청이 멱등하고 동시 요청도 안전하다
        then(disclosureAgreementRepository).should().upsert(ME, DisclosureField.AFFILIATION.name());
        then(disclosureAgreementRepository).should(never()).existsByUserIdAndField(any(), any());
        then(disclosureAgreementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("프로필 항목을 비공개로 바꾸면 공개 동의 기록을 삭제한다")
    void changeProfileVisibility_invisible_deletes() {
        // when: 비공개로 변경
        meService.changeProfileVisibilitySetting(ME, DisclosureField.AFFILIATION_NUMBER, new MeChangeProfileVisibilitySettingCommand(false));

        // then: 삭제 위임 + 존재 여부 조회는 하지 않음
        then(disclosureAgreementRepository).should().deleteByUserIdAndField(ME, DisclosureField.AFFILIATION_NUMBER);
        then(disclosureAgreementRepository).should(never()).existsByUserIdAndField(anyLong(), any());
    }

    // ---------------------------------------------------------------- 앱 알림

    @Test
    @DisplayName("앱 알림 피드 조회 시 limit이 없거나 0 이하면 기본값 20으로, unreadOnly가 null이면 false로 조회한다")
    void appNotificationsFeeds_uses_default_limit() {
        // given: 조회 결과가 없는 상태
        given(appNotificationFeedRepository.findAllByUserIdAndFilter(ME, false, null, 20)).willReturn(List.of());
        given(appNotificationFeedRepository.countByUserIdAndReadAtIsNull(ME)).willReturn(0);

        // when: 필터 없이 조회
        MeAppNotificationsFeedsResult result = meService.appNotificationsFeeds(ME, null, null, 0);

        // then: 기본 limit 20 · unreadOnly false · type null로 위임
        assertThat(result.unreadCount()).isZero();
        assertThat(result.appNotificationFeeds()).isEmpty();
        then(appNotificationFeedRepository).should().findAllByUserIdAndFilter(ME, false, null, 20);
    }

    @Test
    @DisplayName("앱 알림 피드는 targetKind에 따라 프로필/채팅 타깃으로 변환하고 readAt 유무로 읽음 여부를 계산한다")
    void appNotificationsFeeds_maps_targets_and_read_flag() {
        // given: 미읽음 프로필 알림 1건, 읽은 채팅 알림 1건
        AppNotificationFeed profileFeed = feed(10L, AppNotificationFeedType.FRIEND, "친구 요청", "본문1",
                AppNotificationFeedTargetType.PROFILE, 5L, null, null);
        AppNotificationFeed chatFeed = feed(11L, AppNotificationFeedType.MATCH, "채팅 시작", "본문2",
                AppNotificationFeedTargetType.CHAT, null, 77L, Instant.now());
        given(appNotificationFeedRepository.findAllByUserIdAndFilter(ME, true, "FRIEND", 5))
                .willReturn(List.of(profileFeed, chatFeed));
        given(appNotificationFeedRepository.countByUserIdAndReadAtIsNull(ME)).willReturn(1);

        // when: unreadOnly·type·limit을 지정해 조회
        MeAppNotificationsFeedsResult result = meService.appNotificationsFeeds(ME, true, AppNotificationFeedType.FRIEND, 5);

        // then: 타깃 종류별로 변환되고 읽음 여부가 계산됨
        assertThat(result.unreadCount()).isEqualTo(1);
        assertThat(result.appNotificationFeeds()).hasSize(2);
        assertThat(result.appNotificationFeeds().get(0).id()).isEqualTo(10L);
        assertThat(result.appNotificationFeeds().get(0).isRead()).isFalse();
        assertThat(result.appNotificationFeeds().get(0).target())
                .isEqualTo(new MeAppNotificationsFeedsProfileTargetResult("profile", 5L));
        assertThat(result.appNotificationFeeds().get(1).isRead()).isTrue();
        assertThat(result.appNotificationFeeds().get(1).target())
                .isEqualTo(new MeAppNotificationsFeedsChatTargetResult("chat", 77L));
    }

    @Test
    @DisplayName("앱 알림 읽음 처리 시 내 알림이 아니거나 없으면 APP_NOTIFICATION_NOT_FOUND 예외가 발생한다")
    void appNotificationsRead_not_found_throws() {
        // given: 내 알림으로 조회되지 않음
        given(appNotificationFeedRepository.findByIdAndUserId(10L, ME)).willReturn(Optional.empty());

        // when & then: APP_NOTIFICATION_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> meService.appNotificationsRead(ME, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APP_NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 읽은 앱 알림을 다시 읽음 처리하면 읽은 시각을 덮어쓰지 않는다 (멱등)")
    void appNotificationsRead_already_read_is_noop() {
        // given: 이미 읽음 처리된 알림
        Instant readAt = Instant.now().minus(Duration.ofHours(1));
        AppNotificationFeed feed = feed(10L, AppNotificationFeedType.FRIEND, "제목", "본문",
                AppNotificationFeedTargetType.PROFILE, 5L, null, readAt);
        given(appNotificationFeedRepository.findByIdAndUserId(10L, ME)).willReturn(Optional.of(feed));

        // when: 다시 읽음 처리
        meService.appNotificationsRead(ME, 10L);

        // then: 읽은 시각이 유지됨 (멱등)
        assertThat(feed.getReadAt()).isEqualTo(readAt);
    }

    @Test
    @DisplayName("미읽음 앱 알림을 읽음 처리하면 읽은 시각이 기록된다")
    void appNotificationsRead_marks_read() {
        // given: 아직 읽지 않은 알림
        AppNotificationFeed feed = feed(10L, AppNotificationFeedType.FRIEND, "제목", "본문",
                AppNotificationFeedTargetType.PROFILE, 5L, null, null);
        given(appNotificationFeedRepository.findByIdAndUserId(10L, ME)).willReturn(Optional.of(feed));

        // when: 읽음 처리
        meService.appNotificationsRead(ME, 10L);

        // then: 읽은 시각이 기록됨
        assertThat(feed.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("앱 알림 전체 읽음 처리는 마지막 알림 id 이하 범위를 일괄 갱신하도록 위임한다")
    void appNotificationsReadAll_delegates() {
        // when: 전체 읽음 처리
        meService.appNotificationsReadAll(ME, new MeAppNotificationsReadAllCommand(100L));

        // then: userId·lastAppNotificationId로 일괄 갱신 위임
        then(appNotificationFeedRepository).should().markAllReadByUserIdAndIdLessThanEqual(ME, 100L);
    }

    @Test
    @DisplayName("앱 알림 미읽음 개수는 리포지토리 집계 결과를 그대로 반환한다")
    void appNotificationsUnreadCount_returns_count() {
        // given: 미읽음 3건
        given(appNotificationFeedRepository.countByUserIdAndReadAtIsNull(ME)).willReturn(3);

        // when: 미읽음 개수 조회
        // then: 집계 결과 그대로 반환
        assertThat(meService.appNotificationsUnreadCount(ME).unreadCount()).isEqualTo(3);
    }

    // ---------------------------------------------------------------- 상태

    @Test
    @DisplayName("내 상태 조회 시 유저가 없으면 USER_NOT_FOUND 예외가 발생한다")
    void status_user_not_found_throws() {
        // given: 유저가 존재하지 않음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> meService.status(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("내 상태 조회 시 탈퇴 신청 여부와 처리 완료된 신고 사유를 중복 없이 반환한다")
    void status_maps_withdrawal_and_distinct_report_reasons() {
        // given: 탈퇴 신청된 유저 + 같은 사유의 처리 완료 신고 2건, 다른 사유 1건
        User user = user();
        Instant requestedAt = Instant.now().minus(Duration.ofDays(1));
        ReflectionTestUtils.setField(user, "withdrawalRequestedAt", requestedAt);
        ReflectionTestUtils.setField(user, "withdrawalScheduledAt", requestedAt.plus(Duration.ofDays(15)));
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(reportRepository.findAllByReportedUserIdAndStatus(ME, ReportStatus.RESOLVED))
                .willReturn(List.of(
                        Report.create(2L, ME, ReportReason.SPAM, "d1"),
                        Report.create(3L, ME, ReportReason.SPAM, "d2"),
                        Report.create(4L, ME, ReportReason.HARASSMENT, "d3")));

        // when: 내 상태 조회
        MeStatusResult result = meService.status(ME);

        // then: 탈퇴 신청 true + 복구 마감 시각 + 신고 사유는 중복 제거
        assertThat(result.withdrawal().isDeleted()).isTrue();
        assertThat(result.withdrawal().recoverableUntil()).isEqualTo(user.getWithdrawalScheduledAt());
        assertThat(result.report().isReported()).isTrue();
        assertThat(result.report().reasons()).containsExactly("SPAM", "HARASSMENT");
    }

    @Test
    @DisplayName("처리 완료된 신고가 없으면 isReported는 false이고 사유 목록은 비어 있다")
    void status_without_reports() {
        // given: 탈퇴 이력·신고 이력이 없는 유저
        given(userRepository.findById(ME)).willReturn(Optional.of(user()));
        given(reportRepository.findAllByReportedUserIdAndStatus(ME, ReportStatus.RESOLVED)).willReturn(List.of());

        // when: 내 상태 조회
        MeStatusResult result = meService.status(ME);

        // then: 탈퇴 false + 신고 false + 빈 사유 목록
        assertThat(result.withdrawal().isDeleted()).isFalse();
        assertThat(result.withdrawal().recoverableUntil()).isNull();
        assertThat(result.report().isReported()).isFalse();
        assertThat(result.report().reasons()).isEmpty();
    }

    @Test
    @DisplayName("내 상태 조회 시 현재 설문 전 문항에 답했고 관심사 요소와 AI 대화 종료 시각이 있으면 페르소나 입력 상태가 모두 true다")
    void status_persona_all_completed() {
        // given: AI 대화 종료 시각이 있는 유저, 문항 2개 설문에 모두 답함, 관심사 요소 존재
        User user = user();
        ReflectionTestUtils.setField(user, "aiChatCompletedAt", Instant.now());
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(reportRepository.findAllByReportedUserIdAndStatus(ME, ReportStatus.RESOLVED)).willReturn(List.of());
        given(userSurveyAnswerRepository.findAllByUserId(ME)).willReturn(List.of(
                UserSurveyAnswer.create(ME, 1, SurveyOptionName.A),
                UserSurveyAnswer.create(ME, 2, SurveyOptionName.B)));
        given(surveyLoader.getQuestion(1)).willReturn(surveyQuestion(1, PersonaDimension.OPENNESS));
        given(surveyLoader.getQuestion(2)).willReturn(surveyQuestion(2, PersonaDimension.EXTRAVERSION));
        given(surveyLoader.getAllQuestions()).willReturn(List.of(
                surveyQuestion(1, PersonaDimension.OPENNESS),
                surveyQuestion(2, PersonaDimension.EXTRAVERSION)));
        given(personaElementRepository.existsByUserIdAndDimension(ME, PersonaDimension.INTEREST)).willReturn(true);

        // when: 내 상태 조회
        MeStatusResult result = meService.status(ME);

        // then: 설문·관심사·AI 대화 모두 완료
        assertThat(result.persona()).isEqualTo(new MeStatusPersonaResult(true, true, true));
    }

    @Test
    @DisplayName("내 상태 조회 시 설문 답변·관심사 요소·AI 대화 종료 시각이 모두 없으면 페르소나 입력 상태가 모두 false다")
    void status_persona_nothing_completed() {
        // given: AI 대화 종료 시각이 없는 유저, 설문 답변·관심사 요소 없음
        given(userRepository.findById(ME)).willReturn(Optional.of(user()));
        given(reportRepository.findAllByReportedUserIdAndStatus(ME, ReportStatus.RESOLVED)).willReturn(List.of());
        given(userSurveyAnswerRepository.findAllByUserId(ME)).willReturn(List.of());
        given(surveyLoader.getAllQuestions()).willReturn(List.of(
                surveyQuestion(1, PersonaDimension.OPENNESS),
                surveyQuestion(2, PersonaDimension.EXTRAVERSION)));
        given(personaElementRepository.existsByUserIdAndDimension(ME, PersonaDimension.INTEREST)).willReturn(false);

        // when: 내 상태 조회
        MeStatusResult result = meService.status(ME);

        // then: 설문·관심사·AI 대화 모두 미완료
        assertThat(result.persona()).isEqualTo(new MeStatusPersonaResult(false, false, false));
    }

    @Test
    @DisplayName("내 상태 조회 시 설문 완료는 SURVEY_ANSWERS_INCOMPLETE와 같은 기준이라, 현재 설문에 없는 문항의 답변은 세지 않는다")
    void status_survey_ignores_answers_to_unknown_questions() {
        // given: 문항 2개 설문인데 답변은 마지막 문항(2)과 설문에서 빠진 문항(99)
        given(userRepository.findById(ME)).willReturn(Optional.of(user()));
        given(reportRepository.findAllByReportedUserIdAndStatus(ME, ReportStatus.RESOLVED)).willReturn(List.of());
        given(userSurveyAnswerRepository.findAllByUserId(ME)).willReturn(List.of(
                UserSurveyAnswer.create(ME, 99, SurveyOptionName.A),
                UserSurveyAnswer.create(ME, 2, SurveyOptionName.B)));
        given(surveyLoader.getQuestion(99)).willReturn(null);
        given(surveyLoader.getQuestion(2)).willReturn(surveyQuestion(2, PersonaDimension.EXTRAVERSION));
        given(surveyLoader.getAllQuestions()).willReturn(List.of(
                surveyQuestion(1, PersonaDimension.OPENNESS),
                surveyQuestion(2, PersonaDimension.EXTRAVERSION)));

        // when: 내 상태 조회
        MeStatusResult result = meService.status(ME);

        // then: 답변 행은 2개지만 유효 응답은 1개라 설문 미완료
        assertThat(result.persona().isSurveyCompleted()).isFalse();
    }

    // ---------------------------------------------------------------- 망설임

    @Test
    @DisplayName("망설임 목록에서 TODAY·UNANSWERED를 요청하면 오늘자 미답변 질문만 반환하고 date를 채운다")
    void hesitations_today_unanswered() {
        // given: 오늘자 질문 2건 (하나는 이미 답변)
        LocalDate today = LocalDate.now(KST);
        Question unanswered = question(7L, ME, today, null, false, List.of("A", "B"));
        Question answered = question(8L, ME, today, Instant.now(), false, List.of("A", "B"));
        given(questionRepository.findAllByUserIdAndTypeAndIsSkippedFalseAndDate(ME, QuestionType.PERSONA, today))
                .willReturn(List.of(unanswered, answered));

        // when: 오늘자 미답변 망설임 조회
        MeHesitationsResult result = meService.hesitations(ME, HesitationDuration.TODAY, HesitationStatus.UNANSWERED);

        // then: 미답변 질문 id만 + date는 오늘
        assertThat(result.date()).isEqualTo(today);
        assertThat(result.hesitationIds()).containsExactly(7L);
    }

    @Test
    @DisplayName("망설임 목록에서 ALL·ANSWERED를 요청하면 전체 기간의 답변 완료 질문만 반환하고 date는 null이다")
    void hesitations_all_answered() {
        // given: 전체 기간 질문 2건 (하나는 미답변)
        Question unanswered = question(7L, ME, LocalDate.now(KST).minusDays(3), null, false, List.of("A", "B"));
        Question answered = question(8L, ME, LocalDate.now(KST).minusDays(1), Instant.now(), false, List.of("A", "B"));
        given(questionRepository.findAllByUserIdAndTypeAndIsSkippedFalse(ME, QuestionType.PERSONA))
                .willReturn(List.of(unanswered, answered));

        // when: 전체 기간 답변 완료 망설임 조회
        MeHesitationsResult result = meService.hesitations(ME, HesitationDuration.ALL, HesitationStatus.ANSWERED);

        // then: 답변 완료 질문 id만 + date는 null
        assertThat(result.date()).isNull();
        assertThat(result.hesitationIds()).containsExactly(8L);
    }

    @Test
    @DisplayName("망설임 답변 시 질문이 없으면 HESITATION_NOT_FOUND 예외가 발생한다")
    void hesitationsAnswer_not_found_throws() {
        // given: 질문이 존재하지 않음
        given(questionRepository.findById(42L)).willReturn(Optional.empty());

        // when & then: HESITATION_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand("A", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HESITATION_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 망설임에 답변하면 NOT_HESITATION_OWNER 예외가 발생한다")
    void hesitationsAnswer_not_owner_throws() {
        // given: 다른 유저의 질문
        Question question = question(42L, 99L, LocalDate.now(KST), null, false, List.of("A", "B"));
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when & then: NOT_HESITATION_OWNER 예외 발생
        assertThatThrownBy(() -> meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand("A", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_HESITATION_OWNER);
    }

    @Test
    @DisplayName("이미 답변한 망설임에 다른 답을 보내면 HESITATION_ALREADY_HANDLED 예외가 발생한다")
    void hesitationsAnswer_already_answered_with_different_answer_throws() {
        // given: A로 이미 답변된 질문
        Question question = question(42L, ME, LocalDate.now(KST), Instant.now(), false, List.of("A", "B"));
        ReflectionTestUtils.setField(question, "choice", "A");
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when & then: 다른 답(B)은 재전송이 아니라 수정 시도이므로 거절된다
        assertThatThrownBy(() -> meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand("B", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HESITATION_ALREADY_HANDLED);
    }

    @Test
    @DisplayName("망설임 답변 멱등: 이미 답변한 것과 같은 답을 다시 보내면 예외 없이 통과한다")
    void hesitationsAnswer_same_answer_is_idempotent() {
        // given: A로 이미 답변된 질문
        Instant answeredAt = Instant.now().minus(Duration.ofMinutes(1));
        Question question = question(42L, ME, LocalDate.now(KST), answeredAt, false, List.of("A", "B"));
        ReflectionTestUtils.setField(question, "choice", "A");
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when: 같은 답(A)을 다시 전송 (응답 유실 후 재시도 상황)
        meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand("A", false));

        // then: 예외 없이 통과하고 답변 시각도 갱신되지 않는다
        assertThat(question.getChoice()).isEqualTo("A");
        assertThat(question.getAnsweredAt()).isEqualTo(answeredAt);
    }

    @Test
    @DisplayName("망설임 건너뛰기 멱등: 이미 건너뛴 망설임을 다시 건너뛰면 예외 없이 통과한다")
    void hesitationsAnswer_same_skip_is_idempotent() {
        // given: 이미 건너뛴 질문
        Question question = question(42L, ME, LocalDate.now(KST), null, true, List.of("A", "B"));
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when & then: 같은 건너뛰기 재전송은 예외 없이 통과한다
        meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand(null, true));
        assertThat(question.getIsSkipped()).isTrue();
    }

    @Test
    @DisplayName("이미 건너뛴 망설임에 답변하면 HESITATION_ALREADY_HANDLED 예외가 발생한다")
    void hesitationsAnswer_already_skipped_throws() {
        // given: 이미 건너뛴 질문
        Question question = question(42L, ME, LocalDate.now(KST), null, true, List.of("A", "B"));
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when & then: HESITATION_ALREADY_HANDLED 예외 발생
        assertThatThrownBy(() -> meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand("A", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HESITATION_ALREADY_HANDLED);
    }

    @Test
    @DisplayName("skipped=true로 답변하면 answer 검증 없이 건너뛴 상태로 표시한다")
    void hesitationsAnswer_skip() {
        // given: 미답변 질문
        Question question = question(42L, ME, LocalDate.now(KST), null, false, List.of("A", "B"));
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when: answer 없이 skipped=true로 답변
        meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand(null, true));

        // then: 건너뛴 상태 + 선택지는 기록되지 않음
        assertThat(question.getIsSkipped()).isTrue();
        assertThat(question.getChoice()).isNull();
        assertThat(question.getAnsweredAt()).isNull();
    }

    @Test
    @DisplayName("skipped=false인데 answer가 공백이면 HESITATION_ANSWER_EMPTY 예외가 발생한다")
    void hesitationsAnswer_blank_answer_throws() {
        // given: 미답변 질문
        Question question = question(42L, ME, LocalDate.now(KST), null, false, List.of("A", "B"));
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when & then: HESITATION_ANSWER_EMPTY 예외 발생
        assertThatThrownBy(() -> meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand("   ", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HESITATION_ANSWER_EMPTY);
    }

    @Test
    @DisplayName("선택지에 없는 답변을 보내면 HESITATION_ANSWER_NOT_IN_OPTIONS 예외가 발생한다")
    void hesitationsAnswer_not_in_options_throws() {
        // given: 선택지가 A/B인 미답변 질문
        Question question = question(42L, ME, LocalDate.now(KST), null, false, List.of("A", "B"));
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when & then: HESITATION_ANSWER_NOT_IN_OPTIONS 예외 발생
        assertThatThrownBy(() -> meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand("C", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HESITATION_ANSWER_NOT_IN_OPTIONS);
    }

    @Test
    @DisplayName("선택지에 있는 답변을 보내면 선택 값과 답변 시각이 기록된다")
    void hesitationsAnswer_success() {
        // given: 선택지가 A/B인 미답변 질문
        Question question = question(42L, ME, LocalDate.now(KST), null, false, List.of("A", "B"));
        given(questionRepository.findById(42L)).willReturn(Optional.of(question));

        // when: 선택지 A로 답변
        meService.hesitationsAnswer(ME, 42L, new MeHesitationsAnswerCommand("A", false));

        // then: 선택 값·답변 시각 기록 + 건너뜀 아님
        assertThat(question.getChoice()).isEqualTo("A");
        assertThat(question.getAnsweredAt()).isNotNull();
        assertThat(question.getIsSkipped()).isFalse();
    }

    // ---------------------------------------------------------------- 내 프로필 조회

    @Test
    @DisplayName("내 프로필은 성+이름과 서명된 사진, 페르소나 요약, 관심사, 만난 사람·친구 수를 함께 반환한다")
    void myProfile_success() {
        // given: 유저·사진·페르소나 요소·만난 사람 2명(친밀도 75/10)이 모두 존재
        User user = user();
        ReflectionTestUtils.setField(user, "id", ME);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));

        Photo photo = Photo.create(ME, PhotoType.PROFILE, "profile/1/key", 10, 20, 100, 200, Instant.now());
        given(photoRepository.findByUserIdAndType(ME, PhotoType.PROFILE)).willReturn(Optional.of(photo));
        given(cloudFrontService.getSignedUrl("profile/1/key")).willReturn("https://cdn/signed.jpg");

        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME)).willReturn(List.of(
                personaElement(PersonaDimension.OPENNESS, "새로운 걸 좋아한다"),
                personaElement(PersonaDimension.OPENNESS, "낯선 곳도 잘 간다"),
                personaElement(PersonaDimension.CONSCIENTIOUSNESS, "약속은 꼭 지킨다"),
                personaElement(PersonaDimension.EXTRAVERSION, "먼저 말을 건다"),
                personaElement(PersonaDimension.AGREEABLENESS, "잘 맞춰준다"),
                personaElement(PersonaDimension.INTEREST, "등산"),
                personaElement(PersonaDimension.INTEREST, "영화"),
                personaElement(PersonaDimension.SUMMARY, "주말마다 북한산에 오르며 사진으로 순간을 남기는 사람")));

        given(encounterRepository.findAllPartnerUserIdsByUserId(ME)).willReturn(List.of(10L, 20L));
        given(intimacyReader.readAll(eq(ME), eq(List.of(10L, 20L)), any(LocalDateTime.class)))
                .willReturn(Map.of(10L, new Intimacy(75, 0), 20L, new Intimacy(10, 0)));

        // when: 내 프로필 조회
        MeProfileResult result = meService.profile(ME);

        // then: 본인 화면이므로 성+이름, 페르소나는 AI 채팅 종료 시 만든 SUMMARY 문장, 지인은 친구 수에서 빠진다
        assertThat(result.userId()).isEqualTo(ME);
        assertThat(result.userName()).isEqualTo("홍길동");
        assertThat(result.profilePhoto().photoUrl()).isEqualTo("https://cdn/signed.jpg");
        assertThat(result.profilePhoto().position())
                .isEqualTo(new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200));
        assertThat(result.persona()).isEqualTo("주말마다 북한산에 오르며 사진으로 순간을 남기는 사람");
        assertThat(result.interests()).containsExactly("등산", "영화");
        assertThat(result.encounteredPeopleCount()).isEqualTo(2);
        assertThat(result.encounteredFriendCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("SUMMARY 요소가 없는 기존 유저는 차원별 첫 문장 3개를 이어 붙인 요약으로 대체한다")
    void myProfile_without_summary_falls_back_to_trait_join() {
        // given: AI 채팅 요약이 생기기 전에 가입한 유저라 SUMMARY 요소가 없음
        User user = user();
        ReflectionTestUtils.setField(user, "id", ME);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME)).willReturn(List.of(
                personaElement(PersonaDimension.OPENNESS, "새로운 걸 좋아한다"),
                personaElement(PersonaDimension.OPENNESS, "낯선 곳도 잘 간다"),
                personaElement(PersonaDimension.CONSCIENTIOUSNESS, "약속은 꼭 지킨다"),
                personaElement(PersonaDimension.EXTRAVERSION, "먼저 말을 건다"),
                personaElement(PersonaDimension.AGREEABLENESS, "잘 맞춰준다"),
                personaElement(PersonaDimension.DETAIL, "요즘 뭐 해?: 등산")));

        // when: 내 프로필 조회
        MeProfileResult result = meService.profile(ME);

        // then: 설문 차원의 첫 문장 3개까지만 이어 붙이고 DETAIL은 섞이지 않음
        assertThat(result.persona()).isEqualTo("새로운 걸 좋아한다, 약속은 꼭 지킨다, 먼저 말을 건다...");
    }

    @Test
    @DisplayName("내 프로필 조회 시 프로필 사진이 없으면 profilePhoto는 null이다")
    void myProfile_without_photo() {
        // given: 유저는 있으나 프로필 사진이 없음
        User user = user();
        ReflectionTestUtils.setField(user, "id", ME);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(photoRepository.findByUserIdAndType(ME, PhotoType.PROFILE)).willReturn(Optional.empty());

        // when: 내 프로필 조회
        MeProfileResult result = meService.profile(ME);

        // then: profilePhoto는 null이고 CloudFront는 호출되지 않음
        assertThat(result.profilePhoto()).isNull();
        then(cloudFrontService).should(never()).getSignedUrl(any());
    }

    @Test
    @DisplayName("페르소나 요소가 하나도 없으면 요약은 말줄임표만 남고 관심사는 빈 목록이다")
    void myProfile_without_persona_elements() {
        // given: 페르소나 요소가 전혀 없는 유저
        User user = user();
        ReflectionTestUtils.setField(user, "id", ME);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME)).willReturn(List.of());

        // when: 내 프로필 조회
        MeProfileResult result = meService.profile(ME);

        // then: 이어붙일 문장이 없어 접미사만 남고 관심사는 빈 목록
        assertThat(result.persona()).isEqualTo("...");
        assertThat(result.interests()).isEmpty();
    }

    @Test
    @DisplayName("만난 사람이 없으면 두 카운트 모두 0이고 관계 기록은 조회하지 않는다")
    void myProfile_without_encounters() {
        // given: 만난 적이 없는 유저
        User user = user();
        ReflectionTestUtils.setField(user, "id", ME);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(encounterRepository.findAllPartnerUserIdsByUserId(ME)).willReturn(List.of());

        // when: 내 프로필 조회
        MeProfileResult result = meService.profile(ME);

        // then: 카운트는 0이고 불필요한 관계 조회는 일어나지 않음
        assertThat(result.encounteredPeopleCount()).isZero();
        assertThat(result.encounteredFriendCount()).isZero();
        then(intimacyReader).should(never()).readAll(anyLong(), anyList(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("내 프로필 조회 시 유저가 없으면 USER_NOT_FOUND 예외가 발생한다")
    void myProfile_user_not_found_throws() {
        // given: 유저가 존재하지 않음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생 + 사진 조회로 넘어가지 않음
        assertThatThrownBy(() -> meService.profile(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(photoRepository).should(never()).findByUserIdAndType(anyLong(), any());
    }

    @Test
    @DisplayName("내 프로필 v2 는 v1 프로필에서 페르소나 요약만 빼고 나머지 값을 그대로 담는다")
    void myProfileV2_drops_persona() {
        // given: SUMMARY 요약과 관심사가 있고 만난 사람은 없는 유저
        User user = user();
        ReflectionTestUtils.setField(user, "id", ME);
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME)).willReturn(List.of(
                personaElement(PersonaDimension.INTEREST, "등산"),
                personaElement(PersonaDimension.SUMMARY, "주말마다 북한산에 오르며 사진으로 순간을 남기는 사람")));
        given(encounterRepository.findAllPartnerUserIdsByUserId(ME)).willReturn(List.of());

        // when: 내 프로필 v2 조회
        MeProfileV2Result result = meService.profileV2(ME);

        // then: 이름·관심사·카운트는 v1 과 같다 (persona 는 타입에서부터 없음)
        assertThat(result.userId()).isEqualTo(ME);
        assertThat(result.userName()).isEqualTo("홍길동");
        assertThat(result.profilePhoto()).isNull();
        assertThat(result.interests()).containsExactly("등산");
        assertThat(result.encounteredPeopleCount()).isZero();
        assertThat(result.encounteredFriendCount()).isZero();
    }

    // ---------------------------------------------------------------- 픽스처

    private PersonaElement personaElement(PersonaDimension dimension, String explanation) {
        return PersonaElement.create(ME, dimension, explanation, Instant.now());
    }

    // ---------------------------------------------------------------- 구매 상태

    @Test
    @DisplayName("구매 상태 조회는 유저의 RevenueCat 식별자를 반환한다")
    void purchases_returns_identifier() {
        // given: 유저가 존재
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));

        // when: 구매 상태 조회
        MePurchasesResult result = meService.purchases(ME);

        // then: SDK 로그인에 쓸 식별자를 반환
        assertThat(result.revenueCatUserId()).isEqualTo(user.getRevenueCatUserId());
    }

    @Test
    @DisplayName("구매 상태 조회 시 유저가 없으면 USER_NOT_FOUND 예외가 발생하고 동기화하지 않는다")
    void purchases_with_unknown_user_throws() {
        // given: 해당 유저가 존재하지 않음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생 + 동기화 시도 안 함
        assertThatThrownBy(() -> meService.purchases(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

    }

    private User user() {
        return User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash", null, null, null, null);
    }

    private PolicyName policyName(Long id, String name, String identifier) {
        PolicyName policyName = BeanUtils.instantiateClass(PolicyName.class);
        ReflectionTestUtils.setField(policyName, "id", id);
        ReflectionTestUtils.setField(policyName, "name", name);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "isDeprecated", false);
        return policyName;
    }

    private TestPolicySummary policy(Long id, Long policyNameId, String version, Boolean isRequired, Instant effectiveAt) {
        return new TestPolicySummary(id, policyNameId, version, isRequired, effectiveAt);
    }

    private AppNotificationFeed feed(Long id, AppNotificationFeedType type, String title, String body,
                                     AppNotificationFeedTargetType targetKind, Long targetUserId, Long targetChatRoomId,
                                     Instant readAt) {
        AppNotificationFeed feed = BeanUtils.instantiateClass(AppNotificationFeed.class);
        ReflectionTestUtils.setField(feed, "id", id);
        ReflectionTestUtils.setField(feed, "userId", ME);
        ReflectionTestUtils.setField(feed, "title", title);
        ReflectionTestUtils.setField(feed, "body", body);
        ReflectionTestUtils.setField(feed, "type", type);
        ReflectionTestUtils.setField(feed, "targetKind", targetKind);
        ReflectionTestUtils.setField(feed, "targetUserId", targetUserId);
        ReflectionTestUtils.setField(feed, "targetChatRoomId", targetChatRoomId);
        ReflectionTestUtils.setField(feed, "readAt", readAt);
        ReflectionTestUtils.setField(feed, "createdAt", Instant.now());
        return feed;
    }

    private Question question(Long id, Long userId, LocalDate date, Instant answeredAt, Boolean isSkipped, List<String> options) {
        Question question = BeanUtils.instantiateClass(Question.class);
        ReflectionTestUtils.setField(question, "id", id);
        ReflectionTestUtils.setField(question, "userId", userId);
        ReflectionTestUtils.setField(question, "date", date);
        ReflectionTestUtils.setField(question, "time", date.atTime(9, 0));
        ReflectionTestUtils.setField(question, "type", QuestionType.PERSONA);
        ReflectionTestUtils.setField(question, "text", "망설임 질문");
        ReflectionTestUtils.setField(question, "options", options);
        ReflectionTestUtils.setField(question, "answeredAt", answeredAt);
        ReflectionTestUtils.setField(question, "isSkipped", isSkipped);
        ReflectionTestUtils.setField(question, "createdAt", Instant.now());
        return question;
    }

    // ---------------------------------------------------------------- 설문

    @Test
    @DisplayName("존재하지 않는 설문 문항에 답하면 SURVEY_QUESTION_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void surveyAnswer_when_question_not_found_throws() {
        // given: 로더에 해당 qId 문항이 없음
        given(surveyLoader.getQuestion(999)).willReturn(null);

        // when & then: SURVEY_QUESTION_NOT_FOUND 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> meService.surveyAnswer(ME,
                new MeSurveyAnswerCommand(new SurveyAnswerInput(999, SurveyOptionName.A))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SURVEY_QUESTION_NOT_FOUND);

        then(userSurveyAnswerRepository).should(never()).upsert(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("마지막 문항이 아니면 유저/문항/선택지로 답변을 upsert하고 페르소나 요소는 만들지 않는다")
    void surveyAnswer_upserts_answer() {
        // given: 존재하는 문항이고 마지막 문항은 아님
        given(surveyLoader.getQuestion(1)).willReturn(surveyQuestion(1, PersonaDimension.OPENNESS));
        given(surveyLoader.isLastQuestion(1)).willReturn(false);

        // when: 설문 답변 저장
        meService.surveyAnswer(ME, new MeSurveyAnswerCommand(new SurveyAnswerInput(1, SurveyOptionName.A)));

        // then: 유저/문항/선택지 이름으로 upsert가 호출되고 페르소나 요소는 저장되지 않음
        then(userSurveyAnswerRepository).should().upsert(ME, 1, "A");
        then(personaElementRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("마지막 문항에 답했고 전 문항 응답이 모였으면 설문 차원을 지운 뒤 페르소나 요소로 변환해 저장한다")
    void surveyAnswer_on_last_question_with_all_answers_creates_persona_elements() {
        // given: 문항 2개 설문의 마지막 문항(2)에 답하고, 두 문항 답변이 모두 저장되어 있음
        SurveyQuestion first = surveyQuestion(1, PersonaDimension.OPENNESS);
        SurveyQuestion last = surveyQuestion(2, PersonaDimension.EXTRAVERSION);
        given(surveyLoader.getQuestion(1)).willReturn(first);
        given(surveyLoader.getQuestion(2)).willReturn(last);
        given(surveyLoader.isLastQuestion(2)).willReturn(true);
        given(userSurveyAnswerRepository.findAllByUserId(ME)).willReturn(List.of(
                UserSurveyAnswer.create(ME, 1, SurveyOptionName.A),
                UserSurveyAnswer.create(ME, 2, SurveyOptionName.B)));
        given(surveyLoader.getAllQuestions()).willReturn(List.of(first, last));

        // when: 마지막 문항에 답변
        meService.surveyAnswer(ME, new MeSurveyAnswerCommand(new SurveyAnswerInput(2, SurveyOptionName.B)));

        // then: 설문이 만드는 차원을 먼저 지우고(재답변 시 중복 누적 방지) 변환 결과를 저장한다
        then(personaElementRepository).should()
                .deleteByUserIdAndDimensionIn(ME, Set.of(PersonaDimension.OPENNESS, PersonaDimension.EXTRAVERSION));

        ArgumentCaptor<List<PersonaElement>> captor = ArgumentCaptor.forClass(List.class);
        then(personaElementRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(PersonaElement::getUserId, PersonaElement::getDimension, PersonaElement::getExplanation)
                .containsExactly(
                        tuple(ME, PersonaDimension.OPENNESS, "A 특성"),
                        tuple(ME, PersonaDimension.EXTRAVERSION, "B 특성"));
    }

    @Test
    @DisplayName("마지막 문항에 답했는데 응답하지 않은 문항이 있으면 SURVEY_ANSWERS_INCOMPLETE 예외가 발생하고 페르소나를 건드리지 않는다")
    void surveyAnswer_on_last_question_with_missing_answers_throws() {
        // given: 문항 2개 설문에서 마지막 문항(2) 답변만 저장되어 있음
        SurveyQuestion first = surveyQuestion(1, PersonaDimension.OPENNESS);
        SurveyQuestion last = surveyQuestion(2, PersonaDimension.EXTRAVERSION);
        given(surveyLoader.getQuestion(2)).willReturn(last);
        given(surveyLoader.isLastQuestion(2)).willReturn(true);
        given(userSurveyAnswerRepository.findAllByUserId(ME))
                .willReturn(List.of(UserSurveyAnswer.create(ME, 2, SurveyOptionName.B)));
        given(surveyLoader.getAllQuestions()).willReturn(List.of(first, last));

        // when & then: SURVEY_ANSWERS_INCOMPLETE 예외 발생 + 페르소나 요소 삭제/저장 안 함
        assertThatThrownBy(() -> meService.surveyAnswer(ME,
                new MeSurveyAnswerCommand(new SurveyAnswerInput(2, SurveyOptionName.B))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SURVEY_ANSWERS_INCOMPLETE);

        then(personaElementRepository).should(never()).deleteByUserIdAndDimensionIn(anyLong(), any());
        then(personaElementRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("현재 설문에 없는 문항의 답변은 응답 수에 세지 않는다")
    void surveyAnswer_on_last_question_ignores_answers_to_unknown_questions() {
        // given: 문항 2개 설문인데 저장된 답변은 마지막 문항(2)과 설문에서 빠진 문항(99)
        SurveyQuestion first = surveyQuestion(1, PersonaDimension.OPENNESS);
        SurveyQuestion last = surveyQuestion(2, PersonaDimension.EXTRAVERSION);
        given(surveyLoader.getQuestion(2)).willReturn(last);
        given(surveyLoader.getQuestion(99)).willReturn(null);
        given(surveyLoader.isLastQuestion(2)).willReturn(true);
        given(userSurveyAnswerRepository.findAllByUserId(ME)).willReturn(List.of(
                UserSurveyAnswer.create(ME, 99, SurveyOptionName.A),
                UserSurveyAnswer.create(ME, 2, SurveyOptionName.B)));
        given(surveyLoader.getAllQuestions()).willReturn(List.of(first, last));

        // when & then: 답변 행은 2개지만 유효 응답은 1개라 SURVEY_ANSWERS_INCOMPLETE 예외 발생
        assertThatThrownBy(() -> meService.surveyAnswer(ME,
                new MeSurveyAnswerCommand(new SurveyAnswerInput(2, SurveyOptionName.B))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SURVEY_ANSWERS_INCOMPLETE);

        then(personaElementRepository).should(never()).saveAll(any());
    }

    private SurveyQuestion surveyQuestion(Integer id, PersonaDimension dimension) {
        return new SurveyQuestion(id, dimension, null, "시나리오", Map.of(
                SurveyOptionName.A, new SurveyOption("A 라벨", "A 특성"),
                SurveyOptionName.B, new SurveyOption("B 라벨", "B 특성")));
    }

    // ---------------------------------------------------------------- 관심사

    @Test
    @DisplayName("관심사 선택 시 기존 관심사를 모두 지우고 요청한 관심사를 순서대로 저장한다")
    void interests_replaces_interests() {
        // when: 관심사 2개 선택
        meService.interests(ME, new MeInterestsCommand(List.of("등산", "영화")));

        // then: INTEREST 차원 삭제 후 요청 순서대로 저장
        InOrder inOrder = inOrder(personaElementRepository);
        inOrder.verify(personaElementRepository).deleteByUserIdAndDimension(ME, PersonaDimension.INTEREST);
        ArgumentCaptor<PersonaElement> captor = ArgumentCaptor.forClass(PersonaElement.class);
        inOrder.verify(personaElementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PersonaElement::getUserId, PersonaElement::getDimension, PersonaElement::getExplanation)
                .containsExactly(
                        tuple(ME, PersonaDimension.INTEREST, "등산"),
                        tuple(ME, PersonaDimension.INTEREST, "영화"));
    }

    @Test
    @DisplayName("관심사 선택 시 빈 배열이면 기존 관심사만 지우고 아무것도 저장하지 않는다")
    void interests_with_empty_list_only_deletes() {
        // when: 빈 관심사 목록으로 선택
        meService.interests(ME, new MeInterestsCommand(List.of()));

        // then: INTEREST 차원만 삭제되고 저장은 호출되지 않음
        then(personaElementRepository).should().deleteByUserIdAndDimension(ME, PersonaDimension.INTEREST);
        then(personaElementRepository).should(never()).save(any());
    }

    // ---------------------------------------------------------------- 성격 유형

    @Test
    @DisplayName("성격 유형 조회는 페르소나 문장을 저장 순서대로 계산기에 넘기고, 나온 코드의 이름·축 단어·형용사/명사 문구와 그림의 공개 URL을 반환한다")
    void personalityType_success() {
        // given: 페르소나 문장 2개, 계산기는 01110을, 로더는 탐구적인 수호자를, CloudFront 는 그림 key 의 공개 URL을 반환
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME)).willReturn(List.of(
                personaElement(PersonaDimension.EXTRAVERSION, "외향성 문장"),
                personaElement(PersonaDimension.INTEREST, "등산")));
        given(personalityTypeCalculator.calculate(List.of("외향성 문장", "등산"))).willReturn(Optional.of("01110"));
        given(personalityTypeLoader.get("01110"))
                .willReturn(new PersonalityType(
                        "01110",
                        "탐구적인 수호자",
                        List.of("차분", "탐험", "계획", "다정", "안정"),
                        new PersonalityTypePart("01", "탐구적인", "형용사 한 줄", "형용사 설명"),
                        new PersonalityTypePart("110", "수호자", "명사 한 줄", "명사 설명"),
                        "personality-types/v1/110.webp"));
        given(cloudFrontService.getPublicUrl("personality-types/v1/110.webp"))
                .willReturn("https://cdn.example/personality-types/v1/110.webp");

        // when: 성격 유형 조회
        MePersonalityTypeResult result = meService.personalityType(ME);

        // then: 로더가 찾은 이름·축 단어·형용사/명사의 한 줄 소개와 설명 + 서명 없는 공개 URL을 담음
        assertThat(result).isEqualTo(new MePersonalityTypeResult(
                "탐구적인 수호자",
                List.of("차분", "탐험", "계획", "다정", "안정"),
                new MePersonalityTypePartResult("형용사 한 줄", "형용사 설명"),
                new MePersonalityTypePartResult("명사 한 줄", "명사 설명"),
                "https://cdn.example/personality-types/v1/110.webp"));
        then(cloudFrontService).should(never()).getSignedUrl(anyString());
    }

    @Test
    @DisplayName("계산기가 유형을 정하지 못하면 PERSONA_NOT_FOUND 예외가 발생하고 문구를 찾지 않는다")
    void personalityType_without_type_throws_persona_not_found() {
        // given: 페르소나가 없어 계산기가 빈 값을 반환
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME)).willReturn(List.of());
        given(personalityTypeCalculator.calculate(List.of())).willReturn(Optional.empty());

        // when & then: PERSONA_NOT_FOUND 예외 발생 + 로더는 호출되지 않음
        assertThatThrownBy(() -> meService.personalityType(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PERSONA_NOT_FOUND);

        then(personalityTypeLoader).should(never()).get(anyString());
    }

    // ---------------------------------------------------------------- 성향 질문

    @Test
    @DisplayName("성향 문항 목록은 로더의 문항과 선택지를 순서 그대로 id·문구만 옮겨 담는다")
    void tendencyQuestions_maps_questions_in_order() {
        // given: 로더가 선택지 개수가 서로 다른 문항 2개를 반환
        given(tendencyLoader.getAllQuestions()).willReturn(List.of(
                new TendencyQuestion(2L, "두 번째로 적힌 id가 먼저 오는 문항", List.of(
                        new TendencyOption(1L, "가"),
                        new TendencyOption(2L, "나"))),
                new TendencyQuestion(1L, "선택지가 3개인 문항", List.of(
                        new TendencyOption(3L, "다"),
                        new TendencyOption(1L, "라"),
                        new TendencyOption(2L, "마")))));

        // when: 성향 문항 목록 조회
        MeTendencyQuestionsResult result = meService.tendencyQuestions();

        // then: id 크기로 재정렬하지 않고 로더가 준 순서대로 문항·선택지가 담김
        assertThat(result).isEqualTo(new MeTendencyQuestionsResult(List.of(
                new MeTendencyQuestionsItemResult(2L, "두 번째로 적힌 id가 먼저 오는 문항", List.of(
                        new MeTendencyQuestionsOptionResult(1L, "가"),
                        new MeTendencyQuestionsOptionResult(2L, "나"))),
                new MeTendencyQuestionsItemResult(1L, "선택지가 3개인 문항", List.of(
                        new MeTendencyQuestionsOptionResult(3L, "다"),
                        new MeTendencyQuestionsOptionResult(1L, "라"),
                        new MeTendencyQuestionsOptionResult(2L, "마"))))));
    }

    @Test
    @DisplayName("존재하지 않는 성향 문항에 답하면 TENDENCY_QUESTION_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void submitTendencyAnswer_when_question_not_found_throws() {
        // given: 로더에 해당 id 문항이 없음
        given(tendencyLoader.findQuestion(999L)).willReturn(Optional.empty());

        // when & then: TENDENCY_QUESTION_NOT_FOUND 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> meService.submitTendencyAnswer(ME, 999L, new MeSubmitTendencyAnswerCommand(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TENDENCY_QUESTION_NOT_FOUND);

        then(userTendencyAnswerRepository).should(never()).upsert(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("문항에 없는 선택지로 답하면 TENDENCY_OPTION_NOT_IN_QUESTION 예외가 발생하고 저장하지 않는다")
    void submitTendencyAnswer_when_option_not_in_question_throws() {
        // given: 문항 1에는 선택지 1, 2만 있음
        given(tendencyLoader.findQuestion(1L)).willReturn(Optional.of(tendencyQuestion(1L, 1L, 2L)));

        // when & then: 선택지 3으로 답하면 TENDENCY_OPTION_NOT_IN_QUESTION 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> meService.submitTendencyAnswer(ME, 1L, new MeSubmitTendencyAnswerCommand(3L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TENDENCY_OPTION_NOT_IN_QUESTION);

        then(userTendencyAnswerRepository).should(never()).upsert(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("문항에 있는 선택지로 답하면 유저·문항·선택지 id로 답변을 upsert한다")
    void submitTendencyAnswer_upserts_answer() {
        // given: 문항 1에 선택지 1, 2, 3이 있음
        given(tendencyLoader.findQuestion(1L)).willReturn(Optional.of(tendencyQuestion(1L, 1L, 2L, 3L)));

        // when: 마지막 선택지로 답변
        meService.submitTendencyAnswer(ME, 1L, new MeSubmitTendencyAnswerCommand(3L));

        // then: 유저·문항·선택지 id로 upsert가 한 번 호출됨
        then(userTendencyAnswerRepository).should().upsert(ME, 1L, 3L);
    }

    private TendencyQuestion tendencyQuestion(Long id, Long... optionIds) {
        List<TendencyOption> options = Stream.of(optionIds)
                .map(optionId -> new TendencyOption(optionId, "선택지 " + optionId))
                .toList();

        return new TendencyQuestion(id, "문항 " + id, options);
    }

    @Test
    @DisplayName("피드백 선택지 목록은 로더가 준 해당 type의 선택지를 순서 그대로 id·문구만 옮겨 담는다")
    void feedbackOptions_maps_options_in_order() {
        // given: 로더가 탈퇴 사유 선택지 3개를 id 순서와 다르게 반환
        given(feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL)).willReturn(List.of(
                new FeedbackOption(3L, "다"),
                new FeedbackOption(1L, "가"),
                new FeedbackOption(2L, "나")));

        // when: 탈퇴 사유 선택지 조회
        MeFeedbackOptionsResult result = meService.feedbackOptions(FeedbackType.WITHDRAWAL);

        // then: id 크기로 재정렬하지 않고 로더가 준 순서대로 담김
        assertThat(result).isEqualTo(new MeFeedbackOptionsResult(List.of(
                new MeFeedbackOptionsItemResult(3L, "다"),
                new MeFeedbackOptionsItemResult(1L, "가"),
                new MeFeedbackOptionsItemResult(2L, "나"))));
    }

    @Test
    @DisplayName("피드백 선택지가 비어 있으면 빈 목록을 반환한다")
    void feedbackOptions_returns_empty_when_no_options() {
        // given: 로더에 건의하기 선택지가 하나도 없음
        given(feedbackOptionLoader.getOptions(FeedbackType.SUGGESTION)).willReturn(List.of());

        // when: 건의하기 선택지 조회
        MeFeedbackOptionsResult result = meService.feedbackOptions(FeedbackType.SUGGESTION);

        // then: 빈 배열
        assertThat(result.options()).isEmpty();
    }

    @Test
    @DisplayName("건의하기를 보내면 앞뒤 공백을 지운 내용과 선택지, 앱 플랫폼·버전을 함께 저장한다")
    void sendFeedback_suggestion_saves_feedback() {
        // given: 건의하기 선택지는 4, 5, 6 + 저장하면 피드백 id 10이 매겨짐
        given(feedbackOptionLoader.getOptions(FeedbackType.SUGGESTION)).willReturn(feedbackOptions(4L, 5L, 6L));
        givenSavedFeedbackId(10L);

        // when: 선택지 4와 앞뒤 공백이 있는 내용으로 건의하기 전송
        meService.sendFeedback(ME, new MeSendFeedbackCommand(FeedbackType.SUGGESTION, List.of(4L), "  알림이 늦게 와요  "),
                AppPlatform.IOS, new AppVersion(1, 2, 3));

        // then: 유저·type·공백을 지운 내용·플랫폼·버전 문자열로 피드백 저장
        UserFeedback saved = capturedFeedback();
        assertThat(saved.getUserId()).isEqualTo(ME);
        assertThat(saved.getType()).isEqualTo(FeedbackType.SUGGESTION);
        assertThat(saved.getDetail()).isEqualTo("알림이 늦게 와요");
        assertThat(saved.getAppPlatform()).isEqualTo(AppPlatform.IOS);
        assertThat(saved.getAppVersion()).isEqualTo("1.2.3");

        // then: 저장된 피드백 id로 고른 선택지 행 저장
        assertThat(capturedFeedbackOptions())
                .extracting(UserFeedbackOption::getFeedbackId, UserFeedbackOption::getOptionId)
                .containsExactly(tuple(10L, 4L));
    }

    @Test
    @DisplayName("목록에 없는 id와 다른 type의 id는 버리고, 중복 id는 한 번만 저장한다")
    void sendFeedback_drops_unknown_and_duplicate_option_ids() {
        // given: 탈퇴 사유 선택지는 1, 2, 3
        given(feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL)).willReturn(feedbackOptions(1L, 2L, 3L));
        givenSavedFeedbackId(10L);

        // when: 중복된 2, 건의하기 id 4, 없는 id 99를 섞어 탈퇴 사유 전송
        meService.sendFeedback(ME, new MeSendFeedbackCommand(FeedbackType.WITHDRAWAL, List.of(2L, 4L, 99L, 2L, 1L), null),
                null, null);

        // then: 목록에 있는 id만 처음 나온 순서대로 한 번씩 저장
        assertThat(capturedFeedbackOptions())
                .extracting(UserFeedbackOption::getOptionId)
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("탈퇴 사유는 선택지가 있으면 내용 없이도 저장하고, 앱 헤더가 없으면 플랫폼·버전을 비워 둔다")
    void sendFeedback_withdrawal_with_options_saves_without_detail() {
        // given: 탈퇴 사유 선택지는 1, 2, 3
        given(feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL)).willReturn(feedbackOptions(1L, 2L, 3L));
        givenSavedFeedbackId(10L);

        // when: 선택지 1만 고르고 내용은 공백, 앱 헤더 없이 전송
        meService.sendFeedback(ME, new MeSendFeedbackCommand(FeedbackType.WITHDRAWAL, List.of(1L), "   "), null, null);

        // then: 선택지 1이 저장되고, 내용·플랫폼·버전은 null로 저장
        assertThat(capturedFeedbackOptions())
                .extracting(UserFeedbackOption::getOptionId)
                .containsExactly(1L);
        UserFeedback saved = capturedFeedback();
        assertThat(saved.getDetail()).isNull();
        assertThat(saved.getAppPlatform()).isNull();
        assertThat(saved.getAppVersion()).isNull();
    }

    @Test
    @DisplayName("탈퇴 사유에서 보낸 선택지가 모두 목록에 없어도 400 없이 빈 선택지로 저장한다")
    void sendFeedback_withdrawal_with_only_unknown_options_saves_empty() {
        // given: 탈퇴 사유 선택지는 1, 2, 3
        given(feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL)).willReturn(feedbackOptions(1L, 2L, 3L));
        givenSavedFeedbackId(10L);

        // when: 화면을 연 사이 내려간 선택지 99만 골라 내용 없이 전송
        meService.sendFeedback(ME, new MeSendFeedbackCommand(FeedbackType.WITHDRAWAL, List.of(99L), null), null, null);

        // then: 예외 없이 피드백은 저장되고 선택지 행은 없음
        assertThat(capturedFeedback().getDetail()).isNull();
        assertThat(capturedFeedbackOptions()).isEmpty();
    }

    @Test
    @DisplayName("탈퇴 사유는 선택지도 내용도 없어도 예외 없이 빈 피드백으로 저장한다")
    void sendFeedback_withdrawal_without_options_and_detail_saves_empty() {
        // given: 탈퇴 사유 선택지는 1, 2, 3
        given(feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL)).willReturn(feedbackOptions(1L, 2L, 3L));
        givenSavedFeedbackId(10L);

        // when: 빈 선택지 + 공백 내용으로 탈퇴 사유 전송
        meService.sendFeedback(ME, new MeSendFeedbackCommand(FeedbackType.WITHDRAWAL, List.of(), " "), null, null);

        // then: 내용 없는 피드백이 저장되고 선택지 행은 없음
        UserFeedback saved = capturedFeedback();
        assertThat(saved.getType()).isEqualTo(FeedbackType.WITHDRAWAL);
        assertThat(saved.getDetail()).isNull();
        assertThat(capturedFeedbackOptions()).isEmpty();
    }

    @Test
    @DisplayName("건의하기에서 내용이 없으면 선택지를 골랐어도 INVALID_REQUEST 예외가 발생하고 저장하지 않는다")
    void sendFeedback_suggestion_without_detail_throws() {
        // when & then: 선택지 4를 골랐지만 내용 null로 건의하기 전송 시 INVALID_REQUEST + 저장 안 함
        assertThatThrownBy(() -> meService.sendFeedback(ME, new MeSendFeedbackCommand(FeedbackType.SUGGESTION, List.of(4L), null), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        then(userFeedbackRepository).should(never()).save(any());
        then(userFeedbackOptionRepository).should(never()).saveAll(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("피드백을 저장하면 고른 선택지 문구와 저장된 내용으로 알림 이벤트를 발행한다")
    void sendFeedback_publishes_event_with_option_labels() {
        // given: 탈퇴 사유 선택지는 1, 2, 3 + 저장하면 피드백 id 10이 매겨짐
        given(feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL)).willReturn(feedbackOptions(1L, 2L, 3L));
        givenSavedFeedbackId(10L);

        // when: 없는 id 99와 중복된 3을 섞어 앞뒤 공백이 있는 내용으로 탈퇴 사유 전송
        meService.sendFeedback(ME, new MeSendFeedbackCommand(FeedbackType.WITHDRAWAL, List.of(3L, 99L, 1L, 3L), "  매칭이 별로예요 "),
                AppPlatform.ANDROID, new AppVersion(1, 2, 3));

        // then: 저장된 선택지 순서대로 문구가 담기고, 내용은 공백을 지운 값으로 발행
        ArgumentCaptor<FeedbackSentEvent> captor = ArgumentCaptor.forClass(FeedbackSentEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new FeedbackSentEvent(
                10L, ME, FeedbackType.WITHDRAWAL, List.of("선택지 3", "선택지 1"), "매칭이 별로예요", AppPlatform.ANDROID, "1.2.3"));
    }

    private List<FeedbackOption> feedbackOptions(Long... optionIds) {
        return Stream.of(optionIds)
                .map(optionId -> new FeedbackOption(optionId, "선택지 " + optionId))
                .toList();
    }

    private void givenSavedFeedbackId(Long feedbackId) {
        given(userFeedbackRepository.save(any(UserFeedback.class))).willAnswer(invocation -> {
            UserFeedback feedback = invocation.getArgument(0);
            ReflectionTestUtils.setField(feedback, "id", feedbackId);
            return feedback;
        });
    }

    private UserFeedback capturedFeedback() {
        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        then(userFeedbackRepository).should().save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<UserFeedbackOption> capturedFeedbackOptions() {
        ArgumentCaptor<List<UserFeedbackOption>> captor = ArgumentCaptor.forClass(List.class);
        then(userFeedbackOptionRepository).should().saveAll(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- 닉네임 수정

    @Test
    @DisplayName("닉네임 수정은 유저 닉네임을 바꾸고 즉시 flush한다")
    void changeProfileNickname_updates_user() {
        // given: 유저가 존재하고 다른 유저·온보딩 세션 어디에도 같은 닉네임이 없음
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("트윈이", ME)).willReturn(false);
        given(anonSessionRepository.existsByNickname("트윈이")).willReturn(false);

        // when: 규칙에 맞는 닉네임으로 수정
        meService.changeProfileNickname(ME, new MeChangeProfileNicknameCommand("트윈이"));

        // then: 닉네임이 반영되고 유니크 위반을 잡기 위해 flush까지 수행
        assertThat(user.getNickname()).isEqualTo("트윈이");
        then(userRepository).should().saveAndFlush(user);
    }

    @Test
    @DisplayName("닉네임 앞뒤에 공백이 있으면 잘라내지 않고 INVALID_NICKNAME 예외가 발생한다")
    void changeProfileNickname_with_surrounding_spaces_throws() {
        // when & then: 앞뒤 공백이 있는 닉네임은 INVALID_NICKNAME으로 거절 + 유저·중복 조회 없음
        assertThatThrownBy(() -> meService.changeProfileNickname(ME, new MeChangeProfileNicknameCommand("  트윈이  ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_NICKNAME);

        then(userRepository).should(never()).findById(anyLong());
        then(anonSessionRepository).should(never()).existsByNickname(any());
    }

    @Test
    @DisplayName("자기 닉네임의 대소문자만 바꾸는 경우 본인 행을 중복 검사에서 제외해 성공한다")
    void changeProfileNickname_case_only_change_excludes_self() {
        // given: 현재 닉네임이 Twinly이고, 본인을 뺀 중복 검사에서는 걸리지 않음
        User user = user();
        user.changeNickname("Twinly");
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("twinly", ME)).willReturn(false);
        given(anonSessionRepository.existsByNickname("twinly")).willReturn(false);

        // when: 대소문자만 다른 닉네임으로 수정
        meService.changeProfileNickname(ME, new MeChangeProfileNicknameCommand("twinly"));

        // then: 본인 닉네임에 막히지 않고 바뀐 값이 반영됨
        assertThat(user.getNickname()).isEqualTo("twinly");
    }

    @Test
    @DisplayName("다른 유저가 쓰는 닉네임이면 NICKNAME_ALREADY_USED 예외가 발생하고 닉네임을 바꾸지 않는다")
    void changeProfileNickname_taken_by_other_user_throws() {
        // given: 다른 유저가 이미 사용 중인 닉네임
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("twinly", ME)).willReturn(true);

        // when & then: NICKNAME_ALREADY_USED 예외 발생 + 닉네임·저장 변화 없음
        assertThatThrownBy(() -> meService.changeProfileNickname(ME, new MeChangeProfileNicknameCommand("twinly")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_USED);

        assertThat(user.getNickname()).isEqualTo("nick");
        then(userRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("온보딩 중인 익명 세션이 잡아 둔 닉네임이면 NICKNAME_ALREADY_USED 예외가 발생한다")
    void changeProfileNickname_taken_by_onboarding_session_throws() {
        // given: 가입 유저 중에는 없지만 온보딩 중인 세션이 사용 중인 닉네임
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("twinly", ME)).willReturn(false);
        given(anonSessionRepository.existsByNickname("twinly")).willReturn(true);

        // when & then: NICKNAME_ALREADY_USED 예외 발생 + 닉네임·저장 변화 없음
        assertThatThrownBy(() -> meService.changeProfileNickname(ME, new MeChangeProfileNicknameCommand("twinly")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_USED);

        assertThat(user.getNickname()).isEqualTo("nick");
        then(userRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("닉네임이 2자 미만이면 INVALID_NICKNAME 예외가 발생하고 저장소를 조회하지 않는다")
    void changeProfileNickname_too_short_throws() {
        // when & then: 1자 닉네임은 INVALID_NICKNAME으로 거절 + 유저·중복 조회 없음
        assertThatThrownBy(() -> meService.changeProfileNickname(ME, new MeChangeProfileNicknameCommand("a")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_NICKNAME);

        then(userRepository).should(never()).findById(anyLong());
        then(anonSessionRepository).should(never()).existsByNickname(any());
    }

    @Test
    @DisplayName("닉네임 수정 시 유저가 없으면 USER_NOT_FOUND 예외가 발생한다")
    void changeProfileNickname_user_not_found_throws() {
        // given: 유저가 존재하지 않음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> meService.changeProfileNickname(ME, new MeChangeProfileNicknameCommand("twinly")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("검사를 통과한 뒤 다른 요청이 같은 닉네임을 선점하면 NICKNAME_ALREADY_USED 예외로 변환된다")
    void changeProfileNickname_when_lost_race_throws_already_used() {
        // given: 검사 시점에는 비어 있었지만 flush 시점에 유니크 제약이 걸리는 상황
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("twinly", ME)).willReturn(false);
        given(anonSessionRepository.existsByNickname("twinly")).willReturn(false);
        given(userRepository.saveAndFlush(user))
                .willThrow(new DataIntegrityViolationException("uk_users_nickname"));

        // when & then: 500이 아니라 도메인 에러(409)로 나간다
        assertThatThrownBy(() -> meService.changeProfileNickname(ME, new MeChangeProfileNicknameCommand("twinly")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_USED);
    }

    // ---------------------------------------------------------------- 닉네임 중복 확인

    @Test
    @DisplayName("닉네임 중복 확인은 다른 유저·온보딩 세션 어디에도 없으면 사용 가능으로 응답한다")
    void checkProfileNickname_available() {
        // given: 본인을 뺀 유저와 온보딩 세션 어디에도 같은 닉네임이 없음
        given(userRepository.existsByNicknameAndIdNot("트윈이", ME)).willReturn(false);
        given(anonSessionRepository.existsByNickname("트윈이")).willReturn(false);

        // when: 닉네임 중복 확인
        MeCheckProfileNicknameResult result = meService.checkProfileNickname(ME, new MeCheckProfileNicknameCommand("트윈이"));

        // then: 사용 가능
        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("닉네임 중복 확인은 다른 유저가 쓰는 닉네임이면 사용 불가로 응답하고 온보딩 세션은 조회하지 않는다")
    void checkProfileNickname_taken_by_other_user() {
        // given: 본인을 뺀 다른 유저가 사용 중
        given(userRepository.existsByNicknameAndIdNot("twinly", ME)).willReturn(true);

        // when: 닉네임 중복 확인
        MeCheckProfileNicknameResult result = meService.checkProfileNickname(ME, new MeCheckProfileNicknameCommand("twinly"));

        // then: 사용 불가 + 이미 판정이 끝났으므로 온보딩 세션 조회 생략
        assertThat(result.isAvailable()).isFalse();
        then(anonSessionRepository).should(never()).existsByNickname(any());
    }

    @Test
    @DisplayName("닉네임 중복 확인은 온보딩 중인 익명 세션이 잡아 둔 닉네임이면 사용 불가로 응답한다")
    void checkProfileNickname_taken_by_onboarding_session() {
        // given: 가입 유저 중에는 없지만 온보딩 중인 세션이 사용 중
        given(userRepository.existsByNicknameAndIdNot("twinly", ME)).willReturn(false);
        given(anonSessionRepository.existsByNickname("twinly")).willReturn(true);

        // when: 닉네임 중복 확인
        MeCheckProfileNicknameResult result = meService.checkProfileNickname(ME, new MeCheckProfileNicknameCommand("twinly"));

        // then: 사용 불가
        assertThat(result.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("닉네임 중복 확인은 금지어가 포함되면 INVALID_NICKNAME 예외가 발생하고 저장소를 조회하지 않는다")
    void checkProfileNickname_with_forbidden_word_throws() {
        // when & then: 금지어(관리자)가 들어간 닉네임은 INVALID_NICKNAME으로 거절 + 중복 조회 없음
        assertThatThrownBy(() -> meService.checkProfileNickname(ME, new MeCheckProfileNicknameCommand("관리자트윈")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_NICKNAME);

        then(userRepository).should(never()).existsByNicknameAndIdNot(any(), anyLong());
        then(anonSessionRepository).should(never()).existsByNickname(any());
    }
}
