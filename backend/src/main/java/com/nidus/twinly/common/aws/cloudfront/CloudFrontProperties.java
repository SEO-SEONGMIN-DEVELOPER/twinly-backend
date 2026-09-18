package com.nidus.twinly.common.aws.cloudfront;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.cloudfront")
public record CloudFrontProperties(
        String domain,
        String keyPairId,
        String privateKeyBase64
) {

    public CloudFrontProperties {
        RequiredProperty.require("aws.cloudfront.domain", domain);
        RequiredProperty.require("aws.cloudfront.key-pair-id", keyPairId);
        RequiredProperty.require("aws.cloudfront.private-key-base64", privateKeyBase64);
    }
}
