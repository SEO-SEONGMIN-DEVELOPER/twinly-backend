package com.nidus.twinly.anon.service;

import com.nidus.twinly.anon.config.AnonProperties;
import com.nidus.twinly.anon.dto.result.AnonStartResult;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AnonServiceUnitTest {

    private static final Duration TTL = Duration.ofDays(14);

    @Mock
    AnonSessionRepository anonSessionRepository;

    AnonService anonService;

    @BeforeEach
    void setUp() {
        anonService = new AnonService(new AnonProperties(TTL), anonSessionRepository);
    }

    @Test
    @DisplayName("익명 세션 시작 시 새 세션을 저장하고 저장한 토큰·만료시각을 그대로 반환한다")
    void start_saves_new_session_and_returns_it() {
        // given: 만료시각이 발급 시점 + 14일인지 판정하기 위해 호출 직전 시각을 기록
        Instant before = Instant.now();

        // when: 익명 세션 시작
        AnonStartResult result = anonService.start();

        // then: 저장된 엔티티의 토큰·만료시각이 반환값과 일치하고 만료시각은 발급 시점 + 14일이다
        ArgumentCaptor<AnonSession> captor = ArgumentCaptor.forClass(AnonSession.class);
        then(anonSessionRepository).should().save(captor.capture());
        AnonSession saved = captor.getValue();

        assertThat(saved.getToken()).isNotNull();
        assertThat(result.anonSessionToken()).isEqualTo(saved.getToken());
        assertThat(result.expiresAt()).isEqualTo(saved.getExpiresAt());
        assertThat(result.expiresAt()).isBetween(before.plus(TTL), Instant.now().plus(TTL));
    }

    @Test
    @DisplayName("익명 세션 시작 시 온보딩 정보(닉네임 등)는 비어 있는 상태로 저장된다")
    void start_saves_session_without_onboarding_fields() {
        // when: 익명 세션 시작
        anonService.start();

        // then: 토큰·만료시각·생성시각만 채워지고 온보딩 필드는 아직 비어 있음
        ArgumentCaptor<AnonSession> captor = ArgumentCaptor.forClass(AnonSession.class);
        then(anonSessionRepository).should().save(captor.capture());
        AnonSession saved = captor.getValue();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getNickname()).isNull();
        assertThat(saved.getGender()).isNull();
        assertThat(saved.getPhoneNumberHash()).isNull();
        assertThat(saved.getEmailHash()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 조회하면 INVALID_ANON_SESSION 예외가 발생한다")
    void resolveByToken_not_found_throws() {
        // given: 해당 토큰의 익명 세션이 DB에 없음
        UUID token = UUID.randomUUID();
        given(anonSessionRepository.findByToken(token)).willReturn(Optional.empty());

        // when & then: INVALID_ANON_SESSION 예외 발생
        assertThatThrownBy(() -> anonService.resolveByToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ANON_SESSION);
    }

    @Test
    @DisplayName("만료시각이 지난 세션을 조회하면 TOKEN_EXPIRED 예외가 발생한다")
    void resolveByToken_expired_throws() {
        // given: 만료시각이 이미 지난 익명 세션
        UUID token = UUID.randomUUID();
        AnonSession expired = AnonSession.create(token, Instant.now().minusSeconds(1));
        given(anonSessionRepository.findByToken(token)).willReturn(Optional.of(expired));

        // when & then: TOKEN_EXPIRED 예외 발생
        assertThatThrownBy(() -> anonService.resolveByToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("만료 전 세션을 조회하면 엔티티 필드를 담은 스냅샷을 반환한다")
    void resolveByToken_valid_returns_snapshot() {
        // given: 만료 전이며 온보딩으로 닉네임·성별이 채워진 익명 세션
        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(TTL);
        AnonSession session = AnonSession.create(token, expiresAt);
        ReflectionTestUtils.setField(session, "id", 7L);
        session.changeNickname("익명이");
        session.changeGender(Gender.FEMALE);
        given(anonSessionRepository.findByToken(token)).willReturn(Optional.of(session));

        // when: 토큰으로 익명 세션 조회
        AnonSessionSnapshot snapshot = anonService.resolveByToken(token);

        // then: 엔티티의 값이 스냅샷에 그대로 담겨 반환됨
        assertThat(snapshot.id()).isEqualTo(7L);
        assertThat(snapshot.token()).isEqualTo(token);
        assertThat(snapshot.expiresAt()).isEqualTo(expiresAt);
        assertThat(snapshot.nickname()).isEqualTo("익명이");
        assertThat(snapshot.gender()).isEqualTo(Gender.FEMALE);
    }
}
