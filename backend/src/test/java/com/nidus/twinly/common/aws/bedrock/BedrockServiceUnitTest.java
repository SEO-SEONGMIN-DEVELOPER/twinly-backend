package com.nidus.twinly.common.aws.bedrock;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BedrockServiceUnitTest {

    @Mock
    BedrockRuntimeClient bedrockRuntimeClient;

    BedrockService bedrockService;

    @Test
    @DisplayName("텍스트가 아닌 블록이 앞에 와도 첫 텍스트 블록을 찾아낸다")
    void picks_first_text_block() {
        // given: 첫 블록이 텍스트가 아닌 응답 (text() 가 null 인 블록)
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(responseOf(ContentBlock.builder().build(), ContentBlock.fromText("답변")));
        bedrockService = new BedrockService(bedrockRuntimeClient, properties());

        // when: 응답을 파싱한다
        String answer = bedrockService.converse("prompt");

        // then: 앞 블록을 건너뛰고 텍스트를 반환한다 (get(0) 이었다면 null 이 반환됐다)
        assertThat(answer).isEqualTo("답변");
    }

    @Test
    @DisplayName("텍스트 블록이 하나도 없으면 AI_RESPONSE_FAILED 로 실패한다")
    void fails_when_no_text_block() {
        // given: 텍스트 블록이 없는 응답
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(responseOf());
        bedrockService = new BedrockService(bedrockRuntimeClient, properties());

        // when & then: 조용히 null 을 흘리지 않고 도메인 오류로 실패한다
        assertThatThrownBy(() -> bedrockService.converse("prompt"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_RESPONSE_FAILED);
    }

    private BedrockProperties properties() {
        return new BedrockProperties("key", "secret", "ap-northeast-2", "model");
    }

    private ConverseResponse responseOf(ContentBlock... blocks) {
        return ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(List.of(blocks))
                        .build()))
                .build();
    }
}
