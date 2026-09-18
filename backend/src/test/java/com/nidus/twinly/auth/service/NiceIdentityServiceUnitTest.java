package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.client.NiceAuthClient;
import com.nidus.twinly.auth.client.NiceAuthResult;
import com.nidus.twinly.auth.client.NiceAuthResultBody;
import com.nidus.twinly.auth.client.NiceAuthUrlBody;
import com.nidus.twinly.auth.client.NiceResultDecryptor;
import com.nidus.twinly.auth.client.NiceResultNotAvailableException;
import com.nidus.twinly.auth.client.NiceToken;
import com.nidus.twinly.auth.client.NiceTokenProvider;
import com.nidus.twinly.auth.client.NiceTokenRejectedException;
import com.nidus.twinly.auth.config.NiceProperties;
import com.nidus.twinly.auth.dto.result.NiceAuthUrlResult;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class NiceIdentityServiceUnitTest {

    private static final String REQUEST_NO = "TWINLY-11111111-2222-3333-4444-555555555555";
    private static final String RETURN_URL = "https://stage-api.trytwinly.com/api/v1/auth/onboarding/identity/return";
    private static final String CLOSE_URL = "https://stage-api.trytwinly.com/api/v1/auth/onboarding/identity/close";
    private static final NiceToken TOKEN_1 = new NiceToken("token-1", "ticket-1", 66, Instant.parse("2030-01-01T00:00:00Z"));
    private static final NiceToken TOKEN_2 = new NiceToken("token-2", "ticket-2", 66, Instant.parse("2030-01-01T00:00:00Z"));
    private static final NiceAuthUrlBody URL_BODY = new NiceAuthUrlBody(
            "0000", "응답성공", "https://auth.niceid.co.kr/ido/cert/request/S1", "tx-1");

    @Mock
    NiceTokenProvider niceTokenProvider;

    @Mock
    NiceAuthClient niceAuthClient;

    @Mock
    NiceResultDecryptor niceResultDecryptor;

    NiceIdentityService niceIdentityService;

    @BeforeEach
    void setUp() {
        niceIdentityService = new NiceIdentityService(niceTokenProvider, niceAuthClient, niceResultDecryptor,
                new NiceProperties("client-id", "client-secret", RETURN_URL, CLOSE_URL,
                        Duration.ofSeconds(3), Duration.ofSeconds(7), Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("캐시된 토큰으로 인증 URL을 받아 auth_url·transaction_id와 설정의 return/close URL을 함께 돌려준다")
    void requestAuthUrl_success() {
        // given
        given(niceTokenProvider.getToken()).willReturn(TOKEN_1);
        given(niceAuthClient.requestAuthUrl("token-1", REQUEST_NO)).willReturn(URL_BODY);

        // when
        NiceAuthUrlResult result = niceIdentityService.requestAuthUrl(REQUEST_NO);

        // then
        assertThat(result.authUrl()).isEqualTo("https://auth.niceid.co.kr/ido/cert/request/S1");
        assertThat(result.transactionId()).isEqualTo("tx-1");
        assertThat(result.returnUrl()).isEqualTo(RETURN_URL);
        assertThat(result.closeUrl()).isEqualTo(CLOSE_URL);
        then(niceTokenProvider).should(never()).invalidate();
    }

    @Test
    @DisplayName("NICE가 토큰을 거절하면 캐시를 비우고 새 토큰으로 한 번 더 시도한다")
    void requestAuthUrl_retries_once_when_token_rejected() {
        // given: 첫 토큰은 거절(3043), 재발급 토큰은 성공
        given(niceTokenProvider.getToken()).willReturn(TOKEN_1).willReturn(TOKEN_2);
        given(niceAuthClient.requestAuthUrl("token-1", REQUEST_NO)).willThrow(new NiceTokenRejectedException("3043"));
        given(niceAuthClient.requestAuthUrl("token-2", REQUEST_NO)).willReturn(URL_BODY);

        // when
        NiceAuthUrlResult result = niceIdentityService.requestAuthUrl(REQUEST_NO);

        // then: 하루 종일 죽은 토큰을 들고 있지 않도록 invalidate 후 재시도
        assertThat(result.transactionId()).isEqualTo("tx-1");
        then(niceTokenProvider).should(times(1)).invalidate();
        then(niceAuthClient).should(times(2)).requestAuthUrl(anyString(), eq(REQUEST_NO));
    }

    @Test
    @DisplayName("재시도도 토큰 거절이면 더 반복하지 않고 예외를 올린다")
    void requestAuthUrl_gives_up_after_second_rejection() {
        // given
        given(niceTokenProvider.getToken()).willReturn(TOKEN_1).willReturn(TOKEN_2);
        given(niceAuthClient.requestAuthUrl("token-1", REQUEST_NO)).willThrow(new NiceTokenRejectedException("3043"));
        given(niceAuthClient.requestAuthUrl("token-2", REQUEST_NO)).willThrow(new NiceTokenRejectedException("3043"));

        // when & then: 무한 재시도가 되지 않게 두 번째 실패는 그대로 올라간다
        assertThatThrownBy(() -> niceIdentityService.requestAuthUrl(REQUEST_NO))
                .isInstanceOf(NiceTokenRejectedException.class);
        then(niceTokenProvider).should(times(1)).invalidate();
    }

    @Test
    @DisplayName("결과 요청: 결과 요청 헤더에 쓴 토큰과 같은 토큰(ticket)으로 복호화한다")
    void fetchResult_decrypts_with_same_token() {
        // given
        NiceAuthResultBody resultBody = new NiceAuthResultBody("0000", "응답성공", "enc", "integrity");
        NiceAuthResult decrypted = new NiceAuthResult("홍길동", "19990314", "1", "0", "di-1", "1", "01012345678");
        given(niceTokenProvider.getToken()).willReturn(TOKEN_1);
        given(niceAuthClient.requestResult("token-1", "web-tx", "tx-1", REQUEST_NO)).willReturn(resultBody);
        given(niceResultDecryptor.decrypt(TOKEN_1, "tx-1", "enc", "integrity")).willReturn(decrypted);

        // when
        NiceAuthResult result = niceIdentityService.fetchResult(REQUEST_NO, "tx-1", "web-tx");

        // then
        assertThat(result).isEqualTo(decrypted);
        then(niceTokenProvider).should(never()).invalidate();
    }

    @Test
    @DisplayName("결과 요청: 토큰이 거절되면 재발급 토큰으로 요청하고 그 토큰으로 복호화한다")
    void fetchResult_retries_with_renewed_token() {
        // given: 첫 토큰은 거절, 두 번째 토큰으로 성공. 복호화 키 재료도 두 번째 토큰의 것이어야 한다
        NiceAuthResultBody resultBody = new NiceAuthResultBody("0000", "응답성공", "enc", "integrity");
        NiceAuthResult decrypted = new NiceAuthResult("홍길동", "19990314", "1", "0", "di-1", "1", "01012345678");
        given(niceTokenProvider.getToken()).willReturn(TOKEN_1).willReturn(TOKEN_2);
        given(niceAuthClient.requestResult("token-1", "web-tx", "tx-1", REQUEST_NO)).willThrow(new NiceTokenRejectedException("1003"));
        given(niceAuthClient.requestResult("token-2", "web-tx", "tx-1", REQUEST_NO)).willReturn(resultBody);
        given(niceResultDecryptor.decrypt(TOKEN_2, "tx-1", "enc", "integrity")).willReturn(decrypted);

        // when
        NiceAuthResult result = niceIdentityService.fetchResult(REQUEST_NO, "tx-1", "web-tx");

        // then
        assertThat(result).isEqualTo(decrypted);
        then(niceTokenProvider).should(times(1)).invalidate();
        then(niceResultDecryptor).should(never()).decrypt(eq(TOKEN_1), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("결과 요청: 인증 미완료·만료·기제공(3029/3032/3033)은 IDENTITY_NOT_VERIFIED 로 그대로 올라가고 재시도하지 않는다")
    void fetchResult_propagates_not_available() {
        // given
        given(niceTokenProvider.getToken()).willReturn(TOKEN_1);
        given(niceAuthClient.requestResult("token-1", "web-tx", "tx-1", REQUEST_NO))
                .willThrow(new NiceResultNotAvailableException("3032"));

        // when & then
        assertThatThrownBy(() -> niceIdentityService.fetchResult(REQUEST_NO, "tx-1", "web-tx"))
                .isInstanceOf(NiceResultNotAvailableException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_NOT_VERIFIED);
        then(niceTokenProvider).should(never()).invalidate();
        then(niceResultDecryptor).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("토큰 거절이 아닌 실패는 재시도 없이 그대로 올린다")
    void requestAuthUrl_does_not_retry_on_other_failure() {
        // given: IP 미등록(1007) 같은 실패는 토큰을 바꿔도 소용없다
        given(niceTokenProvider.getToken()).willReturn(TOKEN_1);
        given(niceAuthClient.requestAuthUrl("token-1", REQUEST_NO))
                .willThrow(new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED));

        // when & then
        assertThatThrownBy(() -> niceIdentityService.requestAuthUrl(REQUEST_NO))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        then(niceTokenProvider).should(never()).invalidate();
        then(niceAuthClient).should(times(1)).requestAuthUrl("token-1", REQUEST_NO);
    }
}
