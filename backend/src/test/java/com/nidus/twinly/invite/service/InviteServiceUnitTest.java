package com.nidus.twinly.invite.service;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.invite.client.AppleOfferSignature;
import com.nidus.twinly.invite.client.AppleOfferSigner;
import com.nidus.twinly.invite.config.AppStoreProperties;
import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.InviteRewardWaitingReason;
import com.nidus.twinly.invite.domain.PromotionalSource;
import com.nidus.twinly.invite.entity.InviteRegistration;
import com.nidus.twinly.invite.entity.InviteReward;
import com.nidus.twinly.invite.repository.InviteRegistrationRepository;
import com.nidus.twinly.invite.repository.InviteRewardRepository;
import com.nidus.twinly.invite.repository.TwinCreationRepository;
import com.nidus.twinly.me.dto.result.MeInviteRewardResult;
import com.nidus.twinly.me.dto.result.MeIssueInviteRewardAppleOfferSignatureResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationIssueCodeResult;
import com.nidus.twinly.parallelrelation.entity.ParallelRelationCode;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationCodeRepository;
import com.nidus.twinly.parallelrelation.service.ParallelRelationService;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class InviteServiceUnitTest {

    private static final Long ME = 1L;
    private static final Long INVITER = 2L;
    private static final String CODE = "K7M2QX";
    private static final String OFFER_ID = "invite_reward_1m";

    @Mock
    UserRepository userRepository;

    @Mock
    ParallelRelationCodeRepository parallelRelationCodeRepository;

    @Mock
    ParallelRelationService parallelRelationService;

    @Mock
    InviteRegistrationRepository inviteRegistrationRepository;

    @Mock
    InviteRewardRepository inviteRewardRepository;

    @Mock
    TwinCreationRepository twinCreationRepository;

    @Mock
    UserEntitlementRepository userEntitlementRepository;

    @Mock
    EarlySignupGrantRepository earlySignupGrantRepository;

    @Mock
    AppleOfferSigner appleOfferSigner;

    InviteService inviteService;

    @BeforeEach
    void setUp() {
        inviteService = new InviteService(
                userRepository, parallelRelationCodeRepository, parallelRelationService, inviteRegistrationRepository,
                inviteRewardRepository, twinCreationRepository, userEntitlementRepository, earlySignupGrantRepository,
                appleOfferSigner, new AppStoreProperties("com.nidus.twinly", "issuer", "KEY123", "pem", OFFER_ID));
    }

    @Test
    @DisplayName("코드 등록: 소문자로 보내도 대문자 코드로 찾아 초대자와 나의 등록을 저장한다")
    void registerCode_saves_registration() {
        // given: 트윈을 아직 만들지 않은 나 + 활성 초대자의 코드
        givenMe(user(ME, "di-me"));
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.of(ParallelRelationCode.create(INVITER, CODE)));
        given(inviteRegistrationRepository.findByInviteeUserId(ME)).willReturn(Optional.empty());
        given(userRepository.findById(INVITER)).willReturn(Optional.of(user(INVITER, "di-inviter")));

        // when: 소문자 코드로 등록
        inviteService.registerCode(ME, "k7m2qx");

        // then: 초대자·나의 등록 저장
        ArgumentCaptor<InviteRegistration> captor = ArgumentCaptor.forClass(InviteRegistration.class);
        then(inviteRegistrationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getInviterUserId()).isEqualTo(INVITER);
        assertThat(captor.getValue().getInviteeUserId()).isEqualTo(ME);
        assertThat(captor.getValue().getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("코드 등록: 없는 코드면 PARALLEL_RELATION_CODE_NOT_FOUND")
    void registerCode_unknown_code_throws() {
        // given: 코드가 없음
        givenMe(user(ME, "di-me"));
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.empty());

        // when & then: 404 코드
        assertErrorCode(() -> inviteService.registerCode(ME, CODE), ErrorCode.PARALLEL_RELATION_CODE_NOT_FOUND);
        then(inviteRegistrationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("코드 등록: 내 코드면 OWN_PARALLEL_RELATION_CODE")
    void registerCode_own_code_throws() {
        // given: 내가 발급한 코드
        givenMe(user(ME, "di-me"));
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.of(ParallelRelationCode.create(ME, CODE)));

        // when & then: 422 코드
        assertErrorCode(() -> inviteService.registerCode(ME, CODE), ErrorCode.OWN_PARALLEL_RELATION_CODE);
    }

    @Test
    @DisplayName("코드 등록: 탈퇴한 사람의 코드면 PARALLEL_RELATION_CODE_NOT_FOUND")
    void registerCode_withdrawn_inviter_throws() {
        // given: 초대자가 탈퇴 신청함
        givenMe(user(ME, "di-me"));
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.of(ParallelRelationCode.create(INVITER, CODE)));
        given(inviteRegistrationRepository.findByInviteeUserId(ME)).willReturn(Optional.empty());
        User inviter = user(INVITER, "di-inviter");
        inviter.requestWithdrawal(Duration.ofDays(7));
        given(userRepository.findById(INVITER)).willReturn(Optional.of(inviter));

        // when & then: 404 코드
        assertErrorCode(() -> inviteService.registerCode(ME, CODE), ErrorCode.PARALLEL_RELATION_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("코드 등록: 같은 코드를 다시 보내면 저장 없이 성공한다 (멱등)")
    void registerCode_same_code_is_idempotent() {
        // given: 이미 같은 초대자로 등록됨
        givenMe(user(ME, "di-me"));
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.of(ParallelRelationCode.create(INVITER, CODE)));
        given(inviteRegistrationRepository.findByInviteeUserId(ME))
                .willReturn(Optional.of(InviteRegistration.register(INVITER, ME, Instant.now())));

        // when: 같은 코드로 다시 등록
        inviteService.registerCode(ME, CODE);

        // then: 저장하지 않음
        then(inviteRegistrationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("코드 등록: 다른 코드를 이미 등록했으면 INVITE_CODE_ALREADY_REGISTERED")
    void registerCode_other_code_throws() {
        // given: 다른 초대자로 등록됨
        givenMe(user(ME, "di-me"));
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.of(ParallelRelationCode.create(INVITER, CODE)));
        given(inviteRegistrationRepository.findByInviteeUserId(ME))
                .willReturn(Optional.of(InviteRegistration.register(3L, ME, Instant.now())));

        // when & then: 409 코드
        assertErrorCode(() -> inviteService.registerCode(ME, CODE), ErrorCode.INVITE_CODE_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("코드 등록: 내가 트윈을 만들었으면 INVITE_CODE_NOT_ELIGIBLE")
    void registerCode_after_twin_created_throws() {
        // given: AI 대화까지 끝낸 나
        User me = user(ME, "di-me");
        ReflectionTestUtils.setField(me, "aiChatCompletedAt", Instant.now());
        givenEligibilityLookups(me);

        // when & then: 409 코드
        assertErrorCode(() -> inviteService.registerCode(ME, CODE), ErrorCode.INVITE_CODE_NOT_ELIGIBLE);
        then(inviteRegistrationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("코드 등록: 같은 DI 의 예전 계정이 트윈을 만든 적 있으면 INVITE_CODE_NOT_ELIGIBLE")
    void registerCode_previous_account_created_twin_throws() {
        // given: 이 계정은 새 계정이지만 같은 DI 의 트윈 생성 기록이 있음
        givenEligibilityLookups(user(ME, "di-me"));
        given(twinCreationRepository.existsByUserId(ME)).willReturn(false);
        given(twinCreationRepository.existsByDiHash("di-me")).willReturn(true);

        // when & then: 409 코드
        assertErrorCode(() -> inviteService.registerCode(ME, CODE), ErrorCode.INVITE_CODE_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("보상 조회: 성립 전이면 reward 는 null, 코드·문구·집계와 등록 가능 여부를 담는다")
    void inviteReward_before_goal() {
        // given: 코드 있음, 등록 2명 중 1명 성립, 내가 아직 등록 안 함
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, "di-me")));
        given(parallelRelationService.issueCode(ME)).willReturn(new ParallelRelationIssueCodeResult(CODE, "relation"));
        given(inviteRewardRepository.findByUserId(ME)).willReturn(Optional.empty());
        given(inviteRegistrationRepository.existsByInviteeUserId(ME)).willReturn(false);
        given(inviteRegistrationRepository.countByInviterUserId(ME)).willReturn(2);
        given(inviteRegistrationRepository.countByInviterUserIdAndCompletedAtIsNotNull(ME)).willReturn(1);

        // when: 조회
        MeInviteRewardResult result = inviteService.inviteReward(ME);

        // then: 진행 상황과 초대 링크 문구
        assertThat(result.code()).isEqualTo(CODE);
        assertThat(result.shareMessage()).contains("https://trytwinly.com/i/" + CODE);
        assertThat(result.canRegisterCode()).isTrue();
        assertThat(result.registeredCount()).isEqualTo(2);
        assertThat(result.completedCount()).isEqualTo(1);
        assertThat(result.goal()).isEqualTo(5);
        assertThat(result.reward()).isNull();
        assertThat(result.promotionalSource()).isNull();
    }

    @Test
    @DisplayName("보상 조회: 지급 중(목표 시각 기록됨)이면 waiting 이고 waitingReason 은 null 이다")
    void inviteReward_granting_maps_to_waiting() {
        // given: 무료 지급을 시작한 보상
        InviteReward reward = reward(InviteRewardStatus.WAITING);
        ReflectionTestUtils.setField(reward, "waitingReason", InviteRewardWaitingReason.STORE_TRIAL);
        ReflectionTestUtils.setField(reward, "method", InviteRewardMethod.PROMOTIONAL);
        ReflectionTestUtils.setField(reward, "targetExpiresAt", Instant.now().plus(Duration.ofDays(30)));
        givenRewardLookups(reward);

        // when: 조회
        MeInviteRewardResult result = inviteService.inviteReward(ME);

        // then: waiting, 이유·방식·종료 시각 모두 null
        assertThat(result.reward().status()).isEqualTo(InviteRewardStatus.WAITING);
        assertThat(result.reward().waitingReason()).isNull();
        assertThat(result.reward().method()).isNull();
        assertThat(result.reward().endsAt()).isNull();
    }

    @Test
    @DisplayName("보상 조회: 애플 오퍼 대기면 appleOfferId 를 담는다")
    void inviteReward_apple_offer_pending() {
        // given: 애플 오퍼 대기
        givenRewardLookups(reward(InviteRewardStatus.APPLE_OFFER_PENDING));

        // when: 조회
        MeInviteRewardResult result = inviteService.inviteReward(ME);

        // then: 오퍼 id
        assertThat(result.reward().status()).isEqualTo(InviteRewardStatus.APPLE_OFFER_PENDING);
        assertThat(result.reward().appleOfferId()).isEqualTo(OFFER_ID);
    }

    @Test
    @DisplayName("보상 조회: 선착순 혜택이 끝나고 초대 무료 지급 기간이면 promotionalSource 는 inviteReward 다")
    void inviteReward_promotional_source_invite() {
        // given: 무료 지급된 보상(이미 시작), 현재 권한은 promotional, 선착순 혜택 없음
        Instant now = Instant.now();
        InviteReward reward = reward(InviteRewardStatus.GRANTED);
        ReflectionTestUtils.setField(reward, "method", InviteRewardMethod.PROMOTIONAL);
        ReflectionTestUtils.setField(reward, "baseExpiresAt", now.minus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(reward, "endsAt", now.plus(Duration.ofDays(29)));
        givenRewardLookups(reward);
        given(userEntitlementRepository.findByUserIdAndEntitlement(ME, EntitlementReader.SIMULATION_ACCESS))
                .willReturn(Optional.of(UserEntitlement.create(ME, EntitlementReader.SIMULATION_ACCESS, now.plus(Duration.ofDays(29)), "promotional", "normal", now)));
        given(earlySignupGrantRepository.findByUserId(ME)).willReturn(Optional.empty());

        // when: 조회
        MeInviteRewardResult result = inviteService.inviteReward(ME);

        // then: 지급 정보와 inviteReward 출처
        assertThat(result.reward().status()).isEqualTo(InviteRewardStatus.GRANTED);
        assertThat(result.reward().method()).isEqualTo(InviteRewardMethod.PROMOTIONAL);
        assertThat(result.reward().endsAt()).isEqualTo(now.plus(Duration.ofDays(29)));
        assertThat(result.promotionalSource()).isEqualTo(PromotionalSource.INVITE_REWARD);
    }

    @Test
    @DisplayName("애플 오퍼 수락: 대기 상태면 수락으로 바꾸고 바뀐 상태를 돌려준다")
    void acceptAppleOffer_pending_accepts() {
        // given: 대기 상태 보상, 조건부 갱신 성공
        InviteReward pending = reward(InviteRewardStatus.APPLE_OFFER_PENDING);
        InviteReward accepted = reward(InviteRewardStatus.APPLE_OFFER_ACCEPTED);
        given(inviteRewardRepository.findByUserId(ME)).willReturn(Optional.of(pending), Optional.of(accepted));
        given(inviteRewardRepository.acceptAppleOffer(eq(10L), eq("2000000123"), eq(InviteRewardStatus.APPLE_OFFER_PENDING),
                eq(InviteRewardStatus.APPLE_OFFER_ACCEPTED), any())).willReturn(1);
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, "di-me")));
        given(parallelRelationService.issueCode(ME)).willReturn(new ParallelRelationIssueCodeResult(CODE, "relation"));

        // when: 수락 보고
        MeInviteRewardResult result = inviteService.acceptAppleOffer(ME, "2000000123");

        // then: appleOfferAccepted 응답
        assertThat(result.reward().status()).isEqualTo(InviteRewardStatus.APPLE_OFFER_ACCEPTED);
        assertThat(result.reward().appleOfferId()).isNull();
    }

    @Test
    @DisplayName("애플 오퍼 수락: 이미 같은 거래 ID 로 수락했으면 다시 갱신하지 않고 성공한다 (멱등)")
    void acceptAppleOffer_same_transaction_is_idempotent() {
        // given: 같은 거래 ID 로 이미 수락
        InviteReward accepted = reward(InviteRewardStatus.APPLE_OFFER_ACCEPTED);
        ReflectionTestUtils.setField(accepted, "appleTransactionId", "2000000123");
        given(inviteRewardRepository.findByUserId(ME)).willReturn(Optional.of(accepted));
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, "di-me")));
        given(parallelRelationService.issueCode(ME)).willReturn(new ParallelRelationIssueCodeResult(CODE, "relation"));

        // when: 같은 거래 ID 로 다시 보고
        MeInviteRewardResult result = inviteService.acceptAppleOffer(ME, "2000000123");

        // then: 갱신 없이 수락 상태
        assertThat(result.reward().status()).isEqualTo(InviteRewardStatus.APPLE_OFFER_ACCEPTED);
        then(inviteRewardRepository).should(never()).acceptAppleOffer(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("애플 오퍼 수락: 대기 상태가 아니면 INVITE_REWARD_NOT_PENDING")
    void acceptAppleOffer_not_pending_throws() {
        // given: 이미 지급됨
        given(inviteRewardRepository.findByUserId(ME)).willReturn(Optional.of(reward(InviteRewardStatus.GRANTED)));
        given(inviteRewardRepository.acceptAppleOffer(anyLong(), anyString(), any(), any(), any())).willReturn(0);

        // when & then: 409 코드
        assertErrorCode(() -> inviteService.acceptAppleOffer(ME, "2000000123"), ErrorCode.INVITE_REWARD_NOT_PENDING);
    }

    @Test
    @DisplayName("오퍼 서명: 대기 상태면 기록한 App Store 상품과 내 RevenueCat id 로 서명한다")
    void issueAppleOfferSignature_signs_recorded_product() {
        // given: 대기 상태 + 기록된 상품
        InviteReward pending = reward(InviteRewardStatus.APPLE_OFFER_PENDING);
        ReflectionTestUtils.setField(pending, "productId", "twinly_monthly");
        User me = user(ME, "di-me");
        given(inviteRewardRepository.findByUserId(ME)).willReturn(Optional.of(pending));
        given(userRepository.findById(ME)).willReturn(Optional.of(me));
        UUID nonce = UUID.randomUUID();
        given(appleOfferSigner.sign(eq("twinly_monthly"), eq(me.getRevenueCatUserId()), any()))
                .willReturn(new AppleOfferSignature("twinly_monthly", OFFER_ID, "KEY123", nonce, 1L, me.getRevenueCatUserId(), "sig"));

        // when: 서명 발급
        MeIssueInviteRewardAppleOfferSignatureResult result = inviteService.issueAppleOfferSignature(ME);

        // then: 서명 정보 그대로
        assertThat(result.productId()).isEqualTo("twinly_monthly");
        assertThat(result.nonce()).isEqualTo(nonce);
        assertThat(result.appAccountToken()).isEqualTo(me.getRevenueCatUserId());
        assertThat(result.signature()).isEqualTo("sig");
    }

    @Test
    @DisplayName("오퍼 서명: 보상이 없거나 대기 상태가 아니면 INVITE_REWARD_NOT_PENDING 이고 서명하지 않는다")
    void issueAppleOfferSignature_without_pending_throws() {
        // given: 출시 전 초대 0건인 기존 구독자 (보상 없음)
        given(inviteRewardRepository.findByUserId(ME)).willReturn(Optional.empty());

        // when & then: 409 코드 + 서명 없음
        assertErrorCode(() -> inviteService.issueAppleOfferSignature(ME), ErrorCode.INVITE_REWARD_NOT_PENDING);
        then(appleOfferSigner).should(never()).sign(any(), any(), any());
    }

    private void givenMe(User me) {
        given(userRepository.findWithLockById(ME)).willReturn(Optional.of(me));
    }

    private void givenEligibilityLookups(User me) {
        givenMe(me);
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.of(ParallelRelationCode.create(INVITER, CODE)));
        given(inviteRegistrationRepository.findByInviteeUserId(ME)).willReturn(Optional.empty());
        given(userRepository.findById(INVITER)).willReturn(Optional.of(user(INVITER, "di-inviter")));
    }

    private void givenRewardLookups(InviteReward reward) {
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, "di-me")));
        given(parallelRelationService.issueCode(ME)).willReturn(new ParallelRelationIssueCodeResult(CODE, "relation"));
        given(inviteRewardRepository.findByUserId(ME)).willReturn(Optional.of(reward));
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    private InviteReward reward(InviteRewardStatus status) {
        InviteReward reward = InviteReward.create(ME, "di-me", Instant.now());
        ReflectionTestUtils.setField(reward, "id", 10L);
        ReflectionTestUtils.setField(reward, "status", status);
        return reward;
    }

    private User user(Long id, String diHash) {
        User user = User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash", "di", diHash, null, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
