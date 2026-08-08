package com.nidus.twinly.common.aws.ses;

import com.nidus.twinly.common.aws.AwsConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.common.aws.bedrock.BedrockProperties;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontProperties;
import com.nidus.twinly.common.aws.s3.S3Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.GetSendQuotaResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("external")
@SpringBootTest(classes = {AwsConfig.class, SesService.class})
@EnableConfigurationProperties({
        SesProperties.class,
        S3Properties.class,
        BedrockProperties.class,
        CloudFrontProperties.class
})
class SesExternalTest {

    @Autowired
    SesService sesService;

    @Autowired
    SesClient sesClient;

    @Autowired
    SesProperties sesProperties;

    // 수신 주소는 .env 로만 받는다. 기본값을 빈 문자열로 두어 미설정 시 기동은 되게 한다.
    @Value("${EXTERNAL_TEST_EMAIL:}")
    String to;

    @Test
    @DisplayName("발송 없이 전송 한도를 조회하면 자격증명과 리전이 확인된다")
    void get_send_quota() {
        // given: 조회성 호출이라 메일이 나가지 않는다

        // when: 실제 SES에 전송 한도를 물어본다
        GetSendQuotaResponse quota = sesClient.getSendQuota();

        // then: 자격증명·리전·ses 권한이 통과했다.
        //       max24HourSend 가 200 이면 샌드박스라 인증된 주소로만 발송된다.
        System.out.println("\n===== SES 전송 한도 =====");
        System.out.println("발신 주소: " + sesProperties.fromAddress());
        System.out.println("24시간 한도: " + quota.max24HourSend());
        System.out.println("초당 발송률: " + quota.maxSendRate());
        System.out.println("최근 24시간 발송: " + quota.sentLast24Hours() + "\n");

        assertThat(quota.max24HourSend()).isPositive();
    }

    @Test
    @DisplayName("형식이 잘못된 주소로 보내면 SdkException을 BusinessException으로 래핑한다")
    void invalid_address_is_wrapped() {
        // given: 이메일 형식이 아닌 문자열. SES가 요청 단계에서 거부하므로 메일이 나가지 않는다.
        String malformed = "not-an-email";

        // when & then: SesService의 catch(SdkException)가 실제 예외 타입과 맞아야
        //              BusinessException(EMAIL_SEND_FAILED)로 바뀐다. 안 맞으면 원본 예외가 그대로 샌다.
        assertThatThrownBy(() -> sesService.send(malformed, "[twinly] external test", "본문"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    @DisplayName("실제 SES로 메일을 발송하면 예외 없이 접수된다")
    void send_email() {
        // given: 수신 주소가 없으면 발송하지 않고 건너뛴다 (남의 주소로 나가는 사고 방지)
        assumeTrue(!to.isBlank(), "EXTERNAL_TEST_EMAIL 미설정 — 실제 발송을 건너뛴다");

        // when & then: 발신 주소 인증·샌드박스 제한·ses:SendEmail 권한이 모두 정상이면
        //              예외 없이 접수된다
        System.out.println("\n===== 메일 발송 =====\n" + sesProperties.fromAddress() + " → " + to + "\n");

        assertThatCode(() -> sesService.send(
                to,
                "[twinly] external test",
                "SES 연동 검증용 메일입니다."))
                .doesNotThrowAnyException();
    }
}
