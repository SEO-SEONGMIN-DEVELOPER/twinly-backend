package com.nidus.twinly.common.aws.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String accessKeyId,
        String secretAccessKey,
        String region,
        String bucket
) {
}