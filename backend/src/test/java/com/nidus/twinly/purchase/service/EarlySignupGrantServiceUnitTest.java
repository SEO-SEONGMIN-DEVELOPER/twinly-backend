package com.nidus.twinly.purchase.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nidus.twinly.auth.event.UserSignedUpEvent;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.client.RevenueCatClient;
import com.nidus.twinly.purchase.entity.EarlySignupGrant;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import com.nidus.twinly.purchase.writer.EarlySignupGrantWriter;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EarlySignupGrantServiceUnitTest {

    private static final Long USER_ID = 1L;
    private static final Long GRANT_ID = 7L;
    private static final UUID REVENUE_CAT_USER_ID = UUID.fromString("0f8c1e2a-4b7d-4c31-9a6e-2f5b8c0d1e34");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-27T07:00:00Z");

    @Mock
    EarlySignupGrantRepository earlySignupGrantRepository;

    @Mock
    EarlySignupGrantWriter earlySignupGrantWriter;

    @Mock
    UserRepository userRepository;

    @Mock
    RevenueCatClient revenueCatClient;

    @InjectMocks
    EarlySignupGrantService earlySignupGrantService;

    @Test
    @DisplayName("가입 커밋 뒤 배정된 자리가 있으면 RevenueCat 에 저장된 만료 시각으로 부여하고 완료로 표시한다")
    void onUserSignedUp_grants_assigned_user() {
        // given: 가입 트랜잭션에서 자리를 배정받은 유저
        EarlySignupGrant grant = grant(USER_ID);
        given(earlySignupGrantRepository.findByUserIdAndGrantedAtIsNull(USER_ID)).willReturn(Optional.of(grant));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        // when: 가입 완료 이벤트
        earlySignupGrantService.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        // then: 앱이 로그인에 쓰는 RevenueCat 식별자로, 배정 때 정한 만료 시각 그대로 부여한다 (재시도해도 같은 값이라 중복 부여되지 않는다)
        then(revenueCatClient).should().grantPromotional(REVENUE_CAT_USER_ID.toString(), EntitlementReader.SIMULATION_ACCESS, grant.getExpiresAt());
        then(earlySignupGrantWriter).should().markGranted(eq(GRANT_ID), any(Instant.class));
    }

    @Test
    @DisplayName("배정받지 못한 가입자는 RevenueCat 을 호출하지 않는다")
    void onUserSignedUp_without_assignment_does_nothing() {
        // given: 300명이 다 찼거나 이미 받은 본인이라 배정 행이 없음
        given(earlySignupGrantRepository.findByUserIdAndGrantedAtIsNull(USER_ID)).willReturn(Optional.empty());

        // when: 가입 완료 이벤트
        earlySignupGrantService.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        // then: 부여 요청도 완료 표시도 없다
        then(revenueCatClient).shouldHaveNoInteractions();
        then(earlySignupGrantWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("RevenueCat 부여가 실패해도 예외를 밖으로 내지 않고 완료로 표시하지 않아 주기 작업이 다시 시도한다")
    void onUserSignedUp_keeps_pending_when_grant_fails() {
        // given: RevenueCat 장애
        given(earlySignupGrantRepository.findByUserIdAndGrantedAtIsNull(USER_ID)).willReturn(Optional.of(grant(USER_ID)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        willThrow(new BusinessException(ErrorCode.REVENUE_CAT_GRANT_FAILED))
                .given(revenueCatClient).grantPromotional(anyString(), anyString(), any());

        // when & then: 가입은 이미 커밋됐으므로 부여 실패가 가입 응답을 실패로 바꾸면 안 된다
        List<String> errorCodes = loggedErrorCodes(() -> assertThatCode(
                () -> earlySignupGrantService.onUserSignedUp(new UserSignedUpEvent(USER_ID))).doesNotThrowAnyException());

        // then: granted_at 이 비어 있어야 주기 작업의 재시도 대상이 된다
        then(earlySignupGrantWriter).should(never()).markGranted(anyLong(), any());
        assertThat(errorCodes).containsExactly(ErrorCode.REVENUE_CAT_GRANT_FAILED.name());
    }

    @Test
    @DisplayName("부여는 됐지만 완료 표시 저장이 실패해도 예외를 밖으로 내지 않는다")
    void onUserSignedUp_swallows_mark_failure() {
        // given: RevenueCat 부여 성공 뒤 DB 저장이 실패
        given(earlySignupGrantRepository.findByUserIdAndGrantedAtIsNull(USER_ID)).willReturn(Optional.of(grant(USER_ID)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        willThrow(new QueryTimeoutException("timeout"))
                .given(earlySignupGrantWriter).markGranted(anyLong(), any());

        // when & then: 행이 부여 전으로 남아 재시도되지만, 같은 만료 시각이라 RevenueCat 이 중복으로 무시한다
        assertThatCode(() -> earlySignupGrantService.onUserSignedUp(new UserSignedUpEvent(USER_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주기 작업은 만료 전이고 부여 전인 행을 모두 다시 보낸다")
    void grantPending_retries_all_pending() {
        // given: 부여 전인 행 두 개
        given(earlySignupGrantRepository.findAllByGrantedAtIsNullAndExpiresAtAfter(any(Instant.class)))
                .willReturn(List.of(grant(1L), grant(2L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));

        // when: 주기 작업 실행
        earlySignupGrantService.grantPending();

        // then: 두 건 모두 부여하고 완료로 표시한다
        then(revenueCatClient).should(times(2)).grantPromotional(anyString(), eq(EntitlementReader.SIMULATION_ACCESS), any());
        then(earlySignupGrantWriter).should(times(2)).markGranted(eq(GRANT_ID), any(Instant.class));
    }

    @Test
    @DisplayName("주기 작업에서 한 건이 실패해도 나머지는 계속 보낸다")
    void grantPending_continues_after_failure() {
        // given: 첫 번째 유저 부여만 실패
        given(earlySignupGrantRepository.findAllByGrantedAtIsNullAndExpiresAtAfter(any(Instant.class)))
                .willReturn(List.of(grant(1L), grant(2L)));
        User first = user(1L);
        ReflectionTestUtils.setField(first, "revenueCatUserId", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        given(userRepository.findById(1L)).willReturn(Optional.of(first));
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        willThrow(new BusinessException(ErrorCode.REVENUE_CAT_GRANT_FAILED))
                .given(revenueCatClient).grantPromotional(eq("11111111-1111-1111-1111-111111111111"), anyString(), any());

        // when: 주기 작업 실행
        earlySignupGrantService.grantPending();

        // then: 두 번째 유저는 부여되고 완료 표시된다
        then(revenueCatClient).should().grantPromotional(eq(REVENUE_CAT_USER_ID.toString()), anyString(), any());
        then(earlySignupGrantWriter).should().markGranted(eq(GRANT_ID), any(Instant.class));
    }

    @Test
    @DisplayName("파기된 탈퇴 유저에게는 부여하지 않는다")
    void grantPending_skips_deleted_user() {
        // given: 부여 전에 탈퇴가 확정돼 개인정보가 파기된 유저
        User deleted = user(USER_ID);
        deleted.delete();
        given(earlySignupGrantRepository.findAllByGrantedAtIsNullAndExpiresAtAfter(any(Instant.class)))
                .willReturn(List.of(grant(USER_ID)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(deleted));

        // when: 주기 작업 실행
        earlySignupGrantService.grantPending();

        // then: 부여 요청도 완료 표시도 없다
        then(revenueCatClient).shouldHaveNoInteractions();
        then(earlySignupGrantWriter).shouldHaveNoInteractions();
    }

    private EarlySignupGrant grant(Long userId) {
        EarlySignupGrant grant = EarlySignupGrant.assign(userId, "di-hash-" + userId, ASSIGNED_AT);
        ReflectionTestUtils.setField(grant, "id", GRANT_ID);
        return grant;
    }

    private User user(Long id) {
        User user = User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash", null, null, null, null);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "revenueCatUserId", REVENUE_CAT_USER_ID);
        return user;
    }

    private List<String> loggedErrorCodes(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(EarlySignupGrantService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }

        return appender.list.stream()
                .filter(event -> event.getKeyValuePairs() != null)
                .flatMap(event -> event.getKeyValuePairs().stream())
                .filter(pair -> pair.key.equals("errorCode"))
                .map(pair -> String.valueOf(pair.value))
                .toList();
    }
}
