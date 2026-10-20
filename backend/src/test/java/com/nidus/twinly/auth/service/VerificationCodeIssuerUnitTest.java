package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.config.TestVerificationProperties;
import com.nidus.twinly.common.aws.ses.SesService;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.solapi.SolapiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VerificationCodeIssuerUnitTest {

    private static final String CODE = "123456";
    private static final String MESSAGE = "인증번호는 [123456] 입니다. 5분 이내에 입력해주세요.";

    @Mock
    SesService sesService;

    @Mock
    SolapiService solapiService;

    VerificationCodeIssuer verificationCodeIssuer;

    @BeforeEach
    void setUp() {
        TestVerificationProperties testVerificationProperties = new TestVerificationProperties("000000", "0100000", "test+");
        verificationCodeIssuer = new VerificationCodeIssuer(sesService, solapiService, testVerificationProperties);
    }

    @Test
    @DisplayName("EMAIL 이면 메일 제목과 공통 안내 문구로 메일만 보낸다")
    void send_email_uses_ses_only() {
        // when
        verificationCodeIssuer.send(VerificationType.EMAIL, "user@nidus.ac.kr", CODE);

        // then
        then(sesService).should().send("user@nidus.ac.kr", "[트윈리] 인증번호 발송", MESSAGE);
        then(solapiService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("SMS 이면 [트윈리] 머리말을 붙인 공통 안내 문구로 문자만 보낸다")
    void send_sms_uses_solapi_only() {
        // when
        verificationCodeIssuer.send(VerificationType.SMS, "01012345678", CODE);

        // then: 기존 문자 문구와 한 글자도 다르지 않다
        then(solapiService).should().send("01012345678", "[트윈리] 인증번호는 [123456] 입니다. 5분 이내에 입력해주세요.");
        then(sesService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증번호를 보내지 않는 유형(IDENTITY)이면 문자로 흘려보내지 않고 IllegalArgumentException 이 발생한다")
    void send_identity_throws_without_sending() {
        // when & then
        assertThatThrownBy(() -> verificationCodeIssuer.send(VerificationType.IDENTITY, "01012345678", CODE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type=IDENTITY");
        then(sesService).shouldHaveNoInteractions();
        then(solapiService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("테스트 계정이면 유형과 상관없이 아무것도 보내지 않는다")
    void send_to_test_contact_sends_nothing() {
        // when
        verificationCodeIssuer.send(VerificationType.SMS, "01000001234", CODE);
        verificationCodeIssuer.send(VerificationType.EMAIL, "test+qa@nidus.ac.kr", CODE);

        // then
        then(sesService).shouldHaveNoInteractions();
        then(solapiService).shouldHaveNoInteractions();
    }
}
