package com.nidus.twinly.common.aws.bedrock;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.bedrock")
public record BedrockProperties(
        String accessKeyId,
        String secretAccessKey,
        String region,
        String modelId
) {

    public BedrockProperties {
        RequiredProperty.require("aws.bedrock.access-key-id", accessKeyId);
        RequiredProperty.require("aws.bedrock.secret-access-key", secretAccessKey);
        RequiredProperty.require("aws.bedrock.region", region);
        RequiredProperty.require("aws.bedrock.model-id", modelId);
    }
}
