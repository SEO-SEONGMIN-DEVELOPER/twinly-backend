package com.nidus.twinly.common.aws.bedrock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;

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

        ConverseResponse response = bedrockRuntimeClient.converse(request);

        return response.output().message().content().get(0).text();
    }
}