package com.nidus.twinly.common.aws.ses;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.ses")
public record SesProperties(
        String accessKeyId,
        String secretAccessKey,
        String region,
        String fromAddress
) {
}