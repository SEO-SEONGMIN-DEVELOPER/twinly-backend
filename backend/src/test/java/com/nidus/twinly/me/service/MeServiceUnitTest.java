package com.nidus.twinly.me.service;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.presign.PhotoCommitResult;
import com.nidus.twinly.common.presign.PhotoCommitService;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.presign.PresignService;
import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.purchase.service.PurchaseService;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
import com.nidus.twinly.support.TestPolicySummary;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import com.nidus.twinly.me.domain.HesitationDuration;
import com.nidus.twinly.me.domain.HesitationStatus;
import com.nidus.twinly.me.dto.command.MeAppNotificationsReadAllCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileVisibilitySettingCommand;
import com.nidus.twinly.me.dto.command.MeChangePushNotificationsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsItemCommand;
import com.nidus.twinly.me.dto.command.MeHesitationsAnswerCommand;
import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsItemCommand;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsChatTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsProfileTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsResult;
import com.nidus.twinly.me.dto.result.MeConsentsResult;
import com.nidus.twinly.me.dto.result.MeHesitationsResult;
import com.nidus.twinly.me.dto.result.MeProfileEditViewResult;
import com.nidus.twinly.me.dto.result.MeProfileResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoCommitResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoPresignResult;
import com.nidus.twinly.me.dto.result.MeProfileVisibilitySettingsResult;
import com.nidus.twinly.me.dto.result.MePurchasesResult;
import com.nidus.twinly.me.dto.result.MePushNotificationsResult;
import com.nidus.twinly.me.dto.result.MeStatusResult;
import com.nidus.twinly.me.dto.result.MeWithdrawResult;
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
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.report.repository.ReportRepository;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
    PersonaElementRepository personaElementRepository;

    @Mock
    EncounterRepository encounterRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    UserEntitlementRepository userEntitlementRepository;

    @Mock
    PurchaseService purchaseService;

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

        // when: 프로필 수정 화면 조회
        MeProfileEditViewResult result = meService.profileEditView(ME);

        // then: 유저 정보 + 서명 URL 반환
        assertThat(result.userId()).isEqualTo(ME);
        assertThat(result.familyName()).isEqualTo("홍");
        assertThat(result.givenName()).isEqualTo("길동");
        assertThat(result.affiliation()).isEqualTo("니두스");
        assertThat(result.affiliationNumber()).isEqualTo("2020123");
        assertThat(result.birthDate()).isEqualTo("2000-01-01");
        assertThat(result.profilePhoto().photoUrl()).isEqualTo("https://cdn/signed.jpg");
        assertThat(result.profilePhoto().position())
                .isEqualTo(new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200));
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
        assertThatThrownBy(() -> meService.profile(ME, new MeProfileCommand("새소속")))
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
        meService.profile(ME, new MeProfileCommand("새소속"));

        // then: 소속과 해시가 함께 갱신됨
        assertThat(user.getAffiliation()).isEqualTo("새소속");
        assertThat(user.getAffiliationHash()).isEqualTo("newAffHash");
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

        PolicySummary tosV2 = policy(11L, 1L, "2", "legal/tos/v2.html", true, now.minus(Duration.ofDays(1)));
        PolicySummary marketingV1 = policy(20L, 2L, "1", "legal/marketing/v1.html", false, now.minus(Duration.ofDays(5)));
        given(policyCatalog.loadLatestByPolicyNameId(List.of(1L, 2L)))
                .willReturn(Map.of(1L, tosV2, 2L, marketingV1));
        given(cloudFrontService.getPublicUrl("legal/tos/v2.html")).willReturn("https://cdn/legal/tos/v2.html");
        given(cloudFrontService.getPublicUrl("legal/marketing/v1.html")).willReturn("https://cdn/legal/marketing/v1.html");

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
        assertThat(result.consents().get(0).url()).isEqualTo("https://cdn/legal/tos/v2.html");
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
        PolicySummary tosV2 = policy(11L, 1L, "2", "https://policy/tos/2", true, now.minus(Duration.ofDays(1)));
        PolicySummary marketingV1 = policy(20L, 2L, "1", "https://policy/marketing/1", false, now.minus(Duration.ofDays(5)));
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
    @DisplayName("필수 약관을 철회하려 하면 REQUIRED_POLICY_REVOKE_DENIED 예외가 발생하고 철회 쿼리를 실행하지 않는다")
    void revokeConsents_required_policy_throws() {
        // given: 철회 대상에 필수 약관이 포함
        PolicySummary tosV2 = policy(11L, 1L, "2", "https://policy/tos/2", true, Instant.now().minus(Duration.ofDays(1)));
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
        PolicySummary marketingV1 = policy(20L, 2L, "1", "https://policy/marketing/1", false, Instant.now().minus(Duration.ofDays(5)));
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
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdIn(ME, List.of(10L, 20L)))
                .willReturn(List.of(relationship(10L, 75), relationship(20L, 10)));

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
        then(relationshipRepository).should(never()).findLatestByUserIdAndPartnerUserIdIn(anyLong(), anyList());
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

    // ---------------------------------------------------------------- 픽스처

    private PersonaElement personaElement(PersonaDimension dimension, String explanation) {
        return PersonaElement.create(ME, dimension, explanation, Instant.now());
    }

    private Relationship relationship(Long partnerUserId, int intimacy) {
        return Relationship.create(ME, LocalDate.of(2026, 7, 26), "v1", partnerUserId, intimacy, "model", null);
    }

    // ---------------------------------------------------------------- 구매 상태

    @Test
    @DisplayName("구매 상태 조회는 RevenueCat 식별자와 유효한 권한 목록을 함께 반환한다")
    void purchases_returns_identifier_and_active_entitlements() {
        // given: 유저가 존재하고 만료가 남은 premium 권한을 보유
        User user = user();
        Instant future = Instant.now().plus(Duration.ofDays(30));
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(userEntitlementRepository.findAllByUserId(ME))
                .willReturn(List.of(UserEntitlement.create(ME, "premium", future, Instant.now())));

        // when: 구매 상태 조회
        MePurchasesResult result = meService.purchases(ME);

        // then: 유저의 RevenueCat 식별자와 권한명이 반환됨
        assertThat(result.revenueCatUserId()).isEqualTo(user.getRevenueCatUserId());
        assertThat(result.entitlements()).containsExactly("premium");
    }

    @Test
    @DisplayName("만료된 권한은 목록에서 제외하고 만료 없는 권한은 포함한다")
    void purchases_excludes_expired_entitlements() {
        // given: 만료된 premium, 만료 없는 noAds 를 함께 보유
        Instant past = Instant.now().minus(Duration.ofDays(1));
        given(userRepository.findById(ME)).willReturn(Optional.of(user()));
        given(userEntitlementRepository.findAllByUserId(ME)).willReturn(List.of(
                UserEntitlement.create(ME, "premium", past, Instant.now()),
                UserEntitlement.create(ME, "noAds", null, Instant.now())));

        // when: 구매 상태 조회
        MePurchasesResult result = meService.purchases(ME);

        // then: 만료 없는 권한만 남음
        assertThat(result.entitlements()).containsExactly("noAds");
    }

    @Test
    @DisplayName("권한이 하나도 없으면 빈 목록을 반환한다")
    void purchases_with_no_entitlements_returns_empty() {
        // given: 저장된 권한이 없음
        given(userRepository.findById(ME)).willReturn(Optional.of(user()));
        given(userEntitlementRepository.findAllByUserId(ME)).willReturn(List.of());

        // when: 구매 상태 조회
        MePurchasesResult result = meService.purchases(ME);

        // then: 예외 없이 빈 목록
        assertThat(result.entitlements()).isEmpty();
    }

    @Test
    @DisplayName("유저가 없으면 USER_NOT_FOUND 예외가 발생하고 권한을 조회하지 않는다")
    void purchases_with_unknown_user_throws() {
        // given: 해당 유저가 존재하지 않음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생 + 권한 조회 안 함
        assertThatThrownBy(() -> meService.purchases(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(userEntitlementRepository).should(never()).findAllByUserId(anyLong());
    }

    @Test
    @DisplayName("구매 상태 조회는 오래된 동기화를 먼저 갱신한 뒤 결과를 반환한다")
    void purchases_syncs_stale_state_first() {
        // given: 유저가 존재하고 저장된 권한이 없음
        User user = user();
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
        given(userEntitlementRepository.findAllByUserId(ME)).willReturn(List.of());

        // when: 구매 상태 조회
        meService.purchases(ME);

        // then: 조회 전에 조건부 동기화를 위임 (웹훅 유실이 조회 시점에 복구된다)
        then(purchaseService).should().syncIfStale(user);
    }

    private User user() {
        return User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash");
    }

    private PolicyName policyName(Long id, String name, String identifier) {
        PolicyName policyName = BeanUtils.instantiateClass(PolicyName.class);
        ReflectionTestUtils.setField(policyName, "id", id);
        ReflectionTestUtils.setField(policyName, "name", name);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "isDeprecated", false);
        return policyName;
    }

    private TestPolicySummary policy(Long id, Long policyNameId, String version, String key, Boolean isRequired, Instant effectiveAt) {
        return new TestPolicySummary(id, policyNameId, version, key, isRequired, effectiveAt);
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
}
