package com.nidus.twinly.auth.integration;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionAgreement;
import com.nidus.twinly.anon.repository.AnonSessionAgreementRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.entity.AnonSessionIdentityVerification;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionIdentityVerificationRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.MobileCarrier;
import com.nidus.twinly.common.domain.NationalInfo;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.entity.EarlySignupGrant;
import com.nidus.twinly.purchase.entity.EarlySignupGrantCounter;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.EarlySignupGrantCounterRepository;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import com.nidus.twinly.simulation.client.SimulationPreloadClient;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입 → 커밋 → 선착순 권한 부여까지, 실제 커밋을 거쳐야만 드러나는 흐름을 검증한다.
 * 부여는 커밋 뒤 리스너가 하고 완료 표시는 별도 트랜잭션(REQUIRES_NEW)으로 저장되며, 자리 경쟁은 카운터 행 잠금으로 막는다.
 * 셋 다 테스트 트랜잭션(롤백) 안에서는 일어나지 않으므로 끄고, 가입이 실제로 커밋되게 한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthSignupGrantIntegrationTest extends AbstractIntegrationTest {

    private static final String NICKNAME_PREFIX = "grant-signup-";
    private static final int ROUNDS = 3;
    private static final int THREADS = 6;

    @Autowired
    AnonSessionRepository anonSessionRepository;

    @Autowired
    AnonSessionIdentityVerificationRepository anonSessionIdentityVerificationRepository;

    @Autowired
    AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;

    @Autowired
    AnonSessionAgreementRepository anonSessionAgreementRepository;

    @Autowired
    EarlySignupGrantRepository earlySignupGrantRepository;

    @Autowired
    EarlySignupGrantCounterRepository earlySignupGrantCounterRepository;

    @Autowired
    BlindIndexHasher blindIndexHasher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // 실제 RevenueCat 부여 차단. PurchaseIntegrationTest 와 같은 목 구성이라 스프링 컨텍스트를 재사용한다.
    @MockitoBean
    RevenueCatClient revenueCatClient;

    @MockitoBean
    SimulationPreloadClient simulationPreloadClient;

    private final AtomicInteger seq = new AtomicInteger();
    private final List<Long> anonSessionIds = new CopyOnWriteArrayList<>();
    private final List<Long> otherUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(부여·인증·약관·토큰 → users), 카운터는 마이그레이션 초기값으로
        List<Long> userIds = new ArrayList<>(jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE nickname LIKE ?", Long.class, NICKNAME_PREFIX + "%"));
        userIds.addAll(otherUserIds);

        earlySignupGrantRepository.deleteAll();
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM verifications WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM agreements WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
        for (Long anonSessionId : anonSessionIds) {
            jdbcTemplate.update("DELETE FROM anon_session_agreements WHERE anon_session_id = ?", anonSessionId);
            jdbcTemplate.update("DELETE FROM anon_session_identity_verifications WHERE anon_session_id = ?", anonSessionId);
            jdbcTemplate.update("DELETE FROM anon_session_verification_sessions WHERE anon_session_id = ?", anonSessionId);
            jdbcTemplate.update("DELETE FROM anon_sessions WHERE id = ?", anonSessionId);
        }
        setAssignedCount(0);

        anonSessionIds.clear();
        otherUserIds.clear();
    }

    @Test
    @DisplayName("회원가입: 응답 전에 앱이 로그인할 RevenueCat 식별자로 배정 때 정한 만료 시각의 권한을 부여하고, 부여 완료가 커밋된다")
    void signup_grants_simulation_access_before_response() throws Exception {
        // given: 가입 조건을 모두 갖춘 익명 세션
        Applicant applicant = readyToSignUp();

        // when: 익명 세션 토큰으로 회원가입 API 호출
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer " + applicant.anonToken()))
                .andExpect(status().isCreated());

        // then: 응답이 돌아온 시점에 이미 부여 요청이 나가 있다 (앱 SDK 가 가입 직후 조회 결과를 5분간 캐시하기 때문)
        User created = createdUser(applicant);
        EarlySignupGrant grant = earlySignupGrantRepository.findAll().getFirst();
        then(revenueCatClient).should(atLeastOnce()).grantPromotional(
                created.getRevenueCatUserId().toString(), EntitlementReader.SIMULATION_ACCESS, grant.getExpiresAt());

        // then: 커밋이 끝난 뒤의 쓰기지만 별도 트랜잭션으로 저장돼 재시도 대상에서 빠지고, 자리는 하나 소모된다
        assertThat(grant.getUserId()).isEqualTo(created.getId());
        assertThat(grant.getGrantedAt()).isNotNull();
        assertThat(grant.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(58)));
        assertThat(assignedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원가입: RevenueCat 부여가 실패해도 가입은 201 로 성공하고 자리는 부여 전 상태로 남아 재시도 대상이 된다")
    void signup_succeeds_when_grant_fails() throws Exception {
        // given: 가입 가능한 익명 세션 + RevenueCat 장애
        Applicant applicant = readyToSignUp();
        willThrow(new BusinessException(ErrorCode.REVENUE_CAT_GRANT_FAILED))
                .given(revenueCatClient).grantPromotional(anyString(), anyString(), any());

        // when: 회원가입 API 호출
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer " + applicant.anonToken()))
                .andExpect(status().isCreated());

        // then: 유저는 만들어졌고, 배정 행은 granted_at 이 비어 있어 주기 작업이 다시 보낸다
        User created = createdUser(applicant);
        EarlySignupGrant grant = earlySignupGrantRepository.findAll().getFirst();
        assertThat(grant.getUserId()).isEqualTo(created.getId());
        assertThat(grant.getGrantedAt()).isNull();
    }

    @Test
    @DisplayName("회원가입: 탈퇴 후 같은 본인인증으로 다시 가입하면 가입은 되지만 선착순 자리는 다시 받지 않는다")
    void resignup_with_same_di_does_not_grant_again() throws Exception {
        // given: 탈퇴 전 계정으로 이미 받은 본인 (users.di_hash 는 파기 때 지워지므로 부여 기록에만 DI 해시가 남는다)
        Applicant applicant = readyToSignUp();
        User withdrawn = saveUser();
        otherUserIds.add(withdrawn.getId());
        earlySignupGrantRepository.save(EarlySignupGrant.assign(withdrawn.getId(), blindIndexHasher.hash(applicant.di()), Instant.now()));
        jdbcTemplate.update("UPDATE early_signup_grants SET granted_at = UTC_TIMESTAMP(6) WHERE user_id = ?", withdrawn.getId());
        setAssignedCount(1);

        // when: 같은 DI 로 회원가입 API 호출
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer " + applicant.anonToken()))
                .andExpect(status().isCreated());

        // then: 새 계정에는 자리를 배정하지 않고 부여 요청도 보내지 않으며, 카운터도 그대로다
        User created = createdUser(applicant);
        assertThat(earlySignupGrantRepository.findAll()).extracting(EarlySignupGrant::getUserId).containsExactly(withdrawn.getId());
        then(revenueCatClient).should(never()).grantPromotional(eq(created.getRevenueCatUserId().toString()), anyString(), any());
        assertThat(assignedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원가입 동시 요청: 마지막 한 자리를 두고 여러 명이 동시에 가입하면 모두 가입되지만 자리는 한 명만 받고 카운터는 한도를 넘지 않는다")
    void signup_concurrent_last_slot_only_one_assigned() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                // given: 299명이 배정된 상태 + 서로 다른 본인으로 가입 준비를 마친 익명 세션 여러 개
                earlySignupGrantRepository.deleteAll();
                setAssignedCount(EarlySignupGrantCounter.LIMIT - 1);
                List<Applicant> applicants = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    applicants.add(readyToSignUp());
                }

                // when: 출발 신호에 맞춰 동시에 회원가입 API 호출
                CountDownLatch start = new CountDownLatch(1);
                List<Future<MvcResult>> futures = new ArrayList<>();
                for (Applicant applicant : applicants) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        return mockMvc.perform(post("/api/v1/auth/signup")
                                        .header("Authorization", "Bearer " + applicant.anonToken()))
                                .andReturn();
                    }));
                }
                start.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<MvcResult> future : futures) {
                    statuses.add(future.get().getResponse().getStatus());
                }

                // then: 자리 경쟁이 가입 자체를 실패시키지 않는다 (잠금 대기·교착으로 500 이 나면 안 된다)
                assertThat(statuses).as("round %d statuses", round).containsOnly(201);

                // then: 행은 정확히 하나, 카운터는 정확히 한도 (count(*) 로 셌다면 여러 명이 299 를 보고 모두 배정된다)
                assertThat(earlySignupGrantRepository.findAll()).as("round %d grants", round).hasSize(1);
                assertThat(assignedCount()).as("round %d counter", round).isEqualTo(EarlySignupGrantCounter.LIMIT);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private record Applicant(
            UUID anonToken,
            String phone,
            String di
    ) {
    }

    /** 본인인증·이메일 인증·필수 약관 동의·프로필 입력을 마친 익명 세션을 커밋된 상태로 만든다. */
    private Applicant readyToSignUp() {
        int n = seq.incrementAndGet();
        UUID anonToken = UUID.randomUUID();
        String phone = "0109%07d".formatted(n);
        String di = "grant-di-" + UUID.randomUUID();

        AnonSession anonSession = AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1)));
        anonSession.changeNickname(NICKNAME_PREFIX + n + "-" + anonToken.toString().substring(0, 8));
        anonSession.changeFamilyName("홍");
        anonSession.changeGivenName("길동");
        anonSession.changeOrganization("트윈리대학교");
        anonSession.changeAffiliation("트윈리대학교");
        anonSession.changeAffiliationNumber("2025%04d".formatted(n));
        Long anonSessionId = anonSessionRepository.save(anonSession).getId();
        anonSessionIds.add(anonSessionId);

        AnonSessionIdentityVerification identity = AnonSessionIdentityVerification.create(
                anonSessionId, "TWINLY-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), Instant.now().plus(Duration.ofMinutes(10)));
        identity.verify("홍길동", "2000-01-01", Gender.MALE, phone, di, blindIndexHasher.hash(di),
                NationalInfo.DOMESTIC, MobileCarrier.SKT);
        anonSessionIdentityVerificationRepository.save(identity);

        AnonSessionVerificationSession email = AnonSessionVerificationSession.create(
                VerificationType.EMAIL, anonSessionId, "grant" + n + "-" + anonToken.toString().substring(0, 8) + "@test.com",
                "123456", Instant.now().plus(Duration.ofMinutes(5)));
        email.verify();
        anonSessionVerificationSessionRepository.save(email);

        policyCatalog.loadRequiredPolicyIds(PolicyKind.ONBOARDING).forEach(policyId ->
                anonSessionAgreementRepository.save(AnonSessionAgreement.create(anonSessionId, policyId, Instant.now())));

        return new Applicant(anonToken, phone, di);
    }

    private User createdUser(Applicant applicant) {
        return userRepository.findByPhoneNumberHash(blindIndexHasher.hash(applicant.phone())).orElseThrow();
    }

    private void setAssignedCount(int assignedCount) {
        jdbcTemplate.update("UPDATE early_signup_grant_counter SET assigned_count = ? WHERE id = ?",
                assignedCount, EarlySignupGrantCounter.SINGLETON_ID);
    }

    private int assignedCount() {
        return earlySignupGrantCounterRepository.findById(EarlySignupGrantCounter.SINGLETON_ID).orElseThrow().getAssignedCount();
    }
}
