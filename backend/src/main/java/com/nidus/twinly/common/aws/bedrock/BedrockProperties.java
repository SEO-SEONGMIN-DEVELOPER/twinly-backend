package com.nidus.twinly.common.aws.bedrock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.bedrock")
public record BedrockProperties(
        String accessKeyId,
        String secretAccessKey,
        String region,
        String modelId
) {
}