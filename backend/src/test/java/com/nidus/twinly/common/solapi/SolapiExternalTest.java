package com.nidus.twinly.common.solapi;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.solapi.sdk.message.model.Balance;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("external")
@SpringBootTest(classes = {SolapiConfig.class, SolapiService.class})
@EnableConfigurationProperties(SolapiProperties.class)
class SolapiExternalTest {

    @Autowired
    SolapiService solapiService;

    @Autowired
    DefaultMessageService defaultMessageService;

    @Autowired
    SolapiProperties solapiProperties;

    // 수신 번호는 .env 로만 받는다. 기본값을 빈 문자열로 두어 미설정 시 기동은 되게 한다.
    @Value("${EXTERNAL_TEST_PHONE:}")
    String to;

    @Test
    @DisplayName("발송 없이 잔액을 조회하면 API 키와 시크릿이 확인된다")
    void get_balance() {
        // given: 조회성 호출이라 문자가 나가지 않고 과금도 없다

        // when: 실제 Solapi에 잔액을 물어본다
        Balance balance = defaultMessageService.getBalance();

        // then: API 키·시크릿 인증이 통과했다. 잔액이 0이면 발송 자체가 불가능하다.
        System.out.println("\n===== Solapi 계정 상태 =====");
        System.out.println("발신번호: " + solapiProperties.fromNumber());
        System.out.println("잔액: " + balance.getBalance());
        System.out.println("포인트: " + balance.getPoint() + "\n");

        assertThat(balance.getBalance()).isNotNull();
    }

    @Test
    @DisplayName("형식이 잘못된 번호로 보내면 접수 실패를 BusinessException으로 래핑한다")
    void invalid_number_is_wrapped() {
        // given: 전화번호 형식이 아닌 문자열. 접수 단계에서 거부되므로 문자가 나가지 않는다.
        String malformed = "not-a-number";

        // when & then: 단건이라 전건 실패가 되어 SDK가 SolapiMessageNotReceivedException을 던진다.
        //              SolapiService의 catch가 그 타입과 맞아야 BusinessException으로 바뀐다.
        assertThatThrownBy(() -> solapiService.send(malformed, "[twinly] external test"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SMS_SEND_FAILED);
    }

    @Test
    @DisplayName("실제 Solapi로 문자를 발송하면 예외 없이 접수된다")
    void send_sms() {
        // given: 수신 번호가 없으면 발송하지 않고 건너뛴다 (남의 번호로 나가는 사고 방지)
        assumeTrue(!to.isBlank(), "EXTERNAL_TEST_PHONE 미설정 — 실제 발송을 건너뛴다");

        // when & then: 발신번호 사전등록·인증·잔액이 모두 정상이면 예외 없이 접수된다
        System.out.println("\n===== 문자 발송 =====\n" + solapiProperties.fromNumber() + " → " + to + "\n");

        assertThatCode(() -> solapiService.send(to, "[twinly] external test"))
                .doesNotThrowAnyException();
    }
}
