package com.nidus.twinly.common.aws.bedrock;

import com.nidus.twinly.common.aws.AwsConfig;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontProperties;
import com.nidus.twinly.common.aws.s3.S3Properties;
import com.nidus.twinly.common.aws.ses.SesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

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
}
