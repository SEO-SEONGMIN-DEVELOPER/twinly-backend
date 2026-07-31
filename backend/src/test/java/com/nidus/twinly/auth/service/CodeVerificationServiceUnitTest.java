package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.dto.command.AuthEmailVerifyCommand;
import com.nidus.twinly.auth.dto.command.AuthSmsVerifyCommand;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CodeVerificationServiceUnitTest {

    private static final String EMAIL = "user@test.com";
    private static final String PHONE = "01012345678";

    @Mock
    VerificationSessionRepository verificationSessionRepository;

    @InjectMocks
    CodeVerificationService codeVerificationService;

    @Test
    @DisplayName("인증 토큰에 해당하는 세션이 없으면 VERIFICATION_NOT_FOUND 예외가 발생한다")
    void verify_without_session_throws() {
        // given: 해당 타입·인증 토큰으로 조회되는 세션이 없음
        UUID verificationToken = UUID.randomUUID();
        given(verificationSessionRepository.findByTypeAndVerificationToken(VerificationType.EMAIL, verificationToken))
                .willReturn(Optional.empty());

        // when & then: VERIFICATION_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> codeVerificationService.verify(
                new AuthEmailVerifyCommand(verificationToken, "123456"), VerificationType.EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("코드 유효 시간이 지났으면 VERIFICATION_CODE_EXPIRED 예외가 발생하고 인증 완료 토큰을 발급하지 않는다")
    void verify_with_expired_code_throws() {
        // given: 코드 만료 시각이 이미 지난 세션
        VerificationSession session = VerificationSession.create(
                VerificationType.EMAIL, EMAIL, "123456", Instant.now().minusSeconds(1));
        given(verificationSessionRepository.findByTypeAndVerificationToken(VerificationType.EMAIL, session.getVerificationToken()))
                .willReturn(Optional.of(session));

        // when & then: VERIFICATION_CODE_EXPIRED 예외 발생 + 인증 완료 토큰 미발급
        assertThatThrownBy(() -> codeVerificationService.verify(
                new AuthEmailVerifyCommand(session.getVerificationToken(), "123456"), VerificationType.EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
        assertThat(session.getVerifiedToken()).isNull();
    }

    @Test
    @DisplayName("코드가 일치하지 않으면 VERIFICATION_CODE_MISMATCH 예외가 발생하고 인증 완료 토큰을 발급하지 않는다")
    void verify_with_wrong_code_throws() {
        // given: 유효 기간이 남은 세션
        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, PHONE, "123456", Instant.now().plusSeconds(60));
        given(verificationSessionRepository.findByTypeAndVerificationToken(VerificationType.SMS, session.getVerificationToken()))
                .willReturn(Optional.of(session));

        // when & then: VERIFICATION_CODE_MISMATCH 예외 발생 + 인증 완료 토큰 미발급
        assertThatThrownBy(() -> codeVerificationService.verify(
                new AuthSmsVerifyCommand(session.getVerificationToken(), "999999"), VerificationType.SMS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_CODE_MISMATCH);
        assertThat(session.getVerifiedToken()).isNull();
    }

    @Test
    @DisplayName("코드가 일치하면 인증 완료 토큰을 발급하고 만료 시각을 5분 뒤로 설정한다")
    void verify_success_issues_verified_token() {
        // given: 유효 기간이 남고 코드가 123456인 세션
        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, PHONE, "123456", Instant.now().plusSeconds(60));
        given(verificationSessionRepository.findByTypeAndVerificationToken(VerificationType.SMS, session.getVerificationToken()))
                .willReturn(Optional.of(session));

        // when: 올바른 코드로 인증 확인
        VerificationSession verified = codeVerificationService.verify(
                new AuthSmsVerifyCommand(session.getVerificationToken(), "123456"), VerificationType.SMS);

        // then: 인증 완료 토큰·시각이 기록되고 만료 시각이 약 5분 뒤로 설정됨 (리플레이 창을 좁힌다)
        assertThat(verified).isSameAs(session);
        assertThat(verified.getVerifiedToken()).isNotNull();
        assertThat(verified.getVerifiedAt()).isNotNull();
        assertThat(verified.getVerifiedTokenExpiresAt())
                .isBetween(Instant.now().plusSeconds(4 * 60), Instant.now().plusSeconds(6 * 60));
    }

    @Test
    @DisplayName("이미 인증된 세션을 같은 코드로 다시 확인하면 기존 토큰과 만료 시각을 그대로 반환한다")
    void verify_twice_keeps_first_verified_token() {
        // given: 이미 한 번 인증을 마친 세션
        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, PHONE, "123456", Instant.now().plusSeconds(60));
        given(verificationSessionRepository.findByTypeAndVerificationToken(VerificationType.SMS, session.getVerificationToken()))
                .willReturn(Optional.of(session));
        codeVerificationService.verify(new AuthSmsVerifyCommand(session.getVerificationToken(), "123456"), VerificationType.SMS);
        UUID firstToken = session.getVerifiedToken();
        Instant firstExpiresAt = session.getVerifiedTokenExpiresAt();

        // when: 더블클릭·재시도로 같은 코드를 다시 확인
        VerificationSession verified = codeVerificationService.verify(
                new AuthSmsVerifyCommand(session.getVerificationToken(), "123456"), VerificationType.SMS);

        // then: 토큰이 새로 발급되지 않아 유효한 토큰이 늘지 않고, 만료 시각도 연장되지 않는다
        assertThat(verified.getVerifiedToken()).isEqualTo(firstToken);
        assertThat(verified.getVerifiedTokenExpiresAt()).isEqualTo(firstExpiresAt);
    }
}
