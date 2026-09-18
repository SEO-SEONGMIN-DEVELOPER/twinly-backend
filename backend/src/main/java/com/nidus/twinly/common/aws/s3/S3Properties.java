package com.nidus.twinly.common.aws.s3;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String accessKeyId,
        String secretAccessKey,
        String region,
        String bucket
) {

    public S3Properties {
        RequiredProperty.require("aws.s3.access-key-id", accessKeyId);
        RequiredProperty.require("aws.s3.secret-access-key", secretAccessKey);
        RequiredProperty.require("aws.s3.region", region);
        RequiredProperty.require("aws.s3.bucket", bucket);
    }
}
