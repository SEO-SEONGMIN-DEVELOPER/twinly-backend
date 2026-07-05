package com.nidus.twinly.common.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(String accessKeyId,
                            String secretAccessKey,
                            String region,
                            S3 s3) {

    public record S3(String bucket) {
    }
}