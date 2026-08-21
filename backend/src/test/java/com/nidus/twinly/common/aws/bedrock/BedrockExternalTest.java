package com.nidus.twinly.common.aws.bedrock;

import com.nidus.twinly.common.aws.AwsConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontProperties;
import com.nidus.twinly.common.aws.s3.S3Properties;
import com.nidus.twinly.common.aws.ses.SesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("external")
@SpringBootTest(classes = {AwsConfig.class, BedrockService.class})
@EnableConfigurationProperties({
        BedrockProperties.class,
        S3Properties.class,
        CloudFrontProperties.class,
        SesProperties.class
})
class BedrockExternalTest {

    @Autowired
    BedrockService bedrockService;

    @Autowired
    BedrockRuntimeClient bedrockRuntimeClient;

    @Test
    @DisplayName("존재하지 않는 모델이면 SdkException을 BusinessException으로 래핑한다")
    void invalid_model_is_wrapped() {
        // given: 존재할 수 없는 모델 ID로 어댑터를 직접 만든다.
        //        AWS가 요청 단계에서 거부하므로 추론이 일어나지 않아 과금이 없다.
        BedrockService broken = new BedrockService(
                bedrockRuntimeClient,
                new BedrockProperties(null, null, null, "external-test-invalid-model"));

        // when & then: catch(SdkException)가 실제 예외 타입과 맞아야 502 도메인 오류가 된다.
        //              안 맞으면 AWS SDK 예외가 그대로 새어나가 500이 나간다.
        assertThatThrownBy(() -> broken.converse("ping"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_RESPONSE_FAILED);
    }

    @Test
    @DisplayName("첫 질문: 온보딩 성향만으로 첫 번째 후속 질문이 생성된다")
    void converse_first_question() {
        // given: AiChatService.buildPersonaPrompt(turnIndex 0)와 같은 형태.
        //        이 시점에는 DETAIL이 아직 없고 온보딩 성향만 존재한다.
        String prompt = """
                당신은 사용자와 대화를 나누며 그 사람을 더 깊이 이해하려는 인터뷰어입니다.
                아래는 지금까지 파악한 사용자의 정보입니다.

                [소속 정보]
                - 소속: 우아한테크코스

                [성격 특성]
                - EXTRAVERSION: 혼자 있는 시간에 에너지를 회복한다
                - OPENNESS: 새로운 기술을 먼저 시도해보는 편이다
                - LIFE_STYLE: 계획을 세우기보다 그때그때 정하는 편이다

                위 정보를 참고해서, 사용자를 더 깊이 이해할 수 있는 자연스러운 후속 질문을 한국어로 하나만 물어보세요.
                공식적인 인터뷰어같지 않은 친근한 말투와 반말을 사용하세요.
                질문 외에 다른 설명은 하지 마세요.
                """;

        // when: 실제 모델을 호출한다
        String answer = bedrockService.converse(prompt);

        // then: 응답이 왔는지만 단언하고, 내용은 눈으로 확인하도록 출력한다
        System.out.println("\n===== [첫 질문] 보낸 프롬프트 =====\n" + prompt);
        System.out.println("===== [첫 질문] 받은 응답 =====\n" + answer + "\n");

        assertThat(answer).isNotBlank();
    }

    @Test
    @DisplayName("후속 질문: 직전 문답을 반영한 다음 질문이 생성된다")
    void converse_follow_up_question() {
        // given: AiChatService.buildFollowUpPrompt와 같은 형태.
        //        직전 문답이 DETAIL로 누적되고 [방금 나눈 대화] 블록이 추가된다.
        String prompt = """
                당신은 사용자와 대화를 나누며 그 사람을 더 깊이 이해하려는 인터뷰어입니다.
                아래는 지금까지 파악한 사용자의 정보입니다.

                [소속 정보]
                - 소속: 우아한테크코스

                [성격 특성]
                - EXTRAVERSION: 혼자 있는 시간에 에너지를 회복한다
                - OPENNESS: 새로운 기술을 먼저 시도해보는 편이다
                - LIFE_STYLE: 계획을 세우기보다 그때그때 정하는 편이다
                - DETAIL: 요즘 뭐에 빠져 있어?: 백엔드 성능 개선하는 데 재미 붙였어

                [방금 나눈 대화]
                나의 질문: 요즘 뭐에 빠져 있어?
                사용자의 답변: 백엔드 성능 개선하는 데 재미 붙였어

                위 정보와 사용자의 방금 답변을 참고해서, 사용자를 더 깊이 이해할 수 있는 자연스러운 후속 질문을 한국어로 하나만 물어보세요.
                공식적인 인터뷰어같지 않은 친근한 말투와 반말을 사용하세요.
                질문 외에 다른 설명은 하지 마세요.
                """;

        // when: 실제 모델을 호출한다
        String answer = bedrockService.converse(prompt);

        // then: 응답이 왔는지만 단언한다 (모델 응답은 매번 달라져 내용 단언은 깨진다)
        System.out.println("\n===== [후속 질문] 보낸 프롬프트 =====\n" + prompt);
        System.out.println("===== [후속 질문] 받은 응답 =====\n" + answer + "\n");

        assertThat(answer).isNotBlank();
    }

    @Test
    @DisplayName("대화 요약: 채팅 종료 시점의 전체 페르소나로 \"~한 사람\" 한 문장이 생성된다")
    void converse_summary() {
        // given: AiChatService.buildSummaryPrompt와 같은 형태.
        //        7번 턴까지 끝난 시점이라 관심사·성격·DETAIL(문답)이 모두 쌓여 있다.
        String prompt = """
                당신은 사용자와 나눈 대화를 바탕으로 그 사람이 어떤 사람인지 한 문장으로 소개하는 작가입니다.
                아래는 지금까지 파악한 사용자의 정보입니다.

                [소속 정보]
                - 소속: 우아한테크코스

                [관심사]
                - 등산
                - 사진
                - 커피

                [성격 특성]
                - EXTRAVERSION: 혼자 있는 시간에 에너지를 회복한다
                - OPENNESS: 새로운 기술을 먼저 시도해보는 편이다
                - LIFE_STYLE: 계획을 세우기보다 그때그때 정하는 편이다

                [나눈 대화]
                - 등산은 어디로 자주 가?: 북한산, 주말 아침에 혼자 올라가
                - 정상에서 뭐 해?: 필름 카메라로 사진 찍고 보온병에 담아간 커피 마셔
                - 요즘 제일 행복한 순간은?: 정상에서 바람 맞으면서 커피 마실 때

                위 정보를 바탕으로 이 사람이 어떤 사람인지 한국어 한 문장으로 요약하세요.
                반드시 "~한 사람"으로 끝나는 형태여야 합니다. (예: 주말마다 북한산에 오르며 사진으로 순간을 남기는 사람)
                성격 특성을 그대로 나열하지 말고, 대화에서 드러난 구체적인 모습을 중심으로 쓰세요.
                60자 이내로 쓰세요.
                요약 문장 외에 다른 설명, 따옴표, 마침표는 붙이지 마세요.
                """;

        // when: 실제 모델을 호출한다
        String answer = bedrockService.converse(prompt);

        // then: 응답이 왔는지만 단언한다. 형식("~한 사람"·길이)은 모델 출력이라 단언하지 않고 출력으로 확인한다
        System.out.println("\n===== [대화 요약] 보낸 프롬프트 =====\n" + prompt);
        System.out.println("===== [대화 요약] 받은 응답 =====\n" + answer + "\n");
        System.out.println("===== [대화 요약] 길이(공백 제거 후) = " + answer.strip().length() + "\n");

        assertThat(answer).isNotBlank();
    }
}
