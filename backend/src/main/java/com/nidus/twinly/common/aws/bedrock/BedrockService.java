package com.nidus.twinly.common.aws.bedrock;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BedrockService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockProperties bedrockProperties;

    public String converse(String prompt) {
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(prompt))
                .build();

        ConverseRequest request = ConverseRequest.builder()
                .modelId(bedrockProperties.modelId())
                .messages(List.of(message))
                .build();

        ConverseResponse response;
        try {
            response = bedrockRuntimeClient.converse(request);
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FAILED, e);
        }

        return firstText(response);
    }

    private String firstText(ConverseResponse response) {
        return response.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_RESPONSE_FAILED));
    }
}