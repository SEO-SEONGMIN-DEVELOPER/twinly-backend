package com.nidus.twinly.common.aws.cloudfront;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.cloudfront")
public record CloudFrontProperties(
        String domain,
        String keyPairId,
        String privateKeyBase64
) {
}
