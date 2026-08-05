package com.nidus.twinly.common.aws.cloudfront;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CloudFrontService {

    private static final Duration URL_EXPIRES_IN = Duration.ofMinutes(10);

    private final CloudFrontUtilities cloudFrontUtilities;
    private final PrivateKey cloudFrontPrivateKey;
    private final CloudFrontProperties cloudFrontProperties;

    public String getPublicUrl(String key) {
        return "https://%s/%s".formatted(cloudFrontProperties.domain(), key);
    }

    public String getSignedUrl(String key) {
        return getSignedUrl(key, URL_EXPIRES_IN);
    }

    public String getSignedUrl(String key, Duration expiresIn) {
        CannedSignerRequest cannedRequest = CannedSignerRequest.builder()
                .resourceUrl(getPublicUrl(key))
                .privateKey(cloudFrontPrivateKey)
                .keyPairId(cloudFrontProperties.keyPairId())
                .expirationDate(Instant.now().plus(expiresIn))
                .build();

        return cloudFrontUtilities.getSignedUrlWithCannedPolicy(cannedRequest).url();
    }
}