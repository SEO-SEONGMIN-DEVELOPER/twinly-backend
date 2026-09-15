package com.nidus.twinly.auth.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NiceTokenProviderUnitTest {

    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

    @Mock
    NiceAuthClient niceAuthClient;

    NiceTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NiceTokenProvider(niceAuthClient, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("처음 요청하면 NICE에서 발급받아 토큰과 키 재료(ticket, iterators)를 함께 돌려준다")
    void getToken_issues_on_first_call() {
        // given
        when(niceAuthClient.issueToken()).thenReturn(body("token-1", NOW.plus(Duration.ofHours(24))));

        // when
        NiceToken token = provider.getToken();

        // then
        assertThat(token.accessToken()).isEqualTo("token-1");
        assertThat(token.ticket()).isEqualTo("ticket-1");
        assertThat(token.iterators()).isEqualTo(66);
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        verify(niceAuthClient, times(1)).issueToken();
    }

    @Test
    @DisplayName("만료가 넉넉히 남은 토큰은 다시 발급받지 않고 캐시된 것을 돌려준다")
    void getToken_reuses_cached_token() {
        // given
        when(niceAuthClient.issueToken()).thenReturn(body("token-1", NOW.plus(Duration.ofHours(24))));
        provider.getToken();

        // when
        NiceToken second = provider.getToken();

        // then
        assertThat(second.accessToken()).isEqualTo("token-1");
        verify(niceAuthClient, times(1)).issueToken();
    }

    @Test
    @DisplayName("만료 1분 안으로 들어온 토큰은 아직 살아 있어도 새로 발급받는다")
    void getToken_refreshes_when_within_margin() {
        // given: 첫 토큰은 30초 뒤 만료, 두 번째는 24시간
        when(niceAuthClient.issueToken())
                .thenReturn(body("token-1", NOW.plus(Duration.ofSeconds(30))))
                .thenReturn(body("token-2", NOW.plus(Duration.ofHours(24))));
        provider.getToken();

        // when
        NiceToken second = provider.getToken();

        // then
        assertThat(second.accessToken()).isEqualTo("token-2");
        verify(niceAuthClient, times(2)).issueToken();
    }

    @Test
    @DisplayName("invalidate 하면 만료 전이라도 다음 요청에서 새로 발급받는다")
    void invalidate_forces_refresh() {
        // given
        when(niceAuthClient.issueToken())
                .thenReturn(body("token-1", NOW.plus(Duration.ofHours(24))))
                .thenReturn(body("token-2", NOW.plus(Duration.ofHours(24))));
        provider.getToken();

        // when
        provider.invalidate();
        NiceToken second = provider.getToken();

        // then
        assertThat(second.accessToken()).isEqualTo("token-2");
        verify(niceAuthClient, times(2)).issueToken();
    }

    @Test
    @DisplayName("발급에 실패하면 캐시에 아무것도 남기지 않고 예외를 그대로 올린다")
    void getToken_propagates_failure_without_caching() {
        // given
        when(niceAuthClient.issueToken())
                .thenThrow(new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED))
                .thenReturn(body("token-1", NOW.plus(Duration.ofHours(24))));

        // when & then: 첫 호출은 실패
        assertThatThrownBy(provider::getToken)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);

        // then: 다음 호출은 다시 발급을 시도해 성공한다
        assertThat(provider.getToken().accessToken()).isEqualTo("token-1");
        verify(niceAuthClient, times(2)).issueToken();
    }

    private static NiceTokenBody body(String accessToken, Instant expiresAt) {
        return new NiceTokenBody("0000", "응답성공", accessToken, expiresAt.toEpochMilli(), 66, "ticket-1");
    }
}
