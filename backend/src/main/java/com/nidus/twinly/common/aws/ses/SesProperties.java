package com.nidus.twinly.common.aws.ses;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.ses")
public record SesProperties(
        String accessKeyId,
        String secretAccessKey,
        String region,
        String fromAddress
) {

    public SesProperties {
        RequiredProperty.require("aws.ses.access-key-id", accessKeyId);
        RequiredProperty.require("aws.ses.secret-access-key", secretAccessKey);
        RequiredProperty.require("aws.ses.region", region);
        RequiredProperty.require("aws.ses.from-address", fromAddress);
    }
}
