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

    public String getSignedUrl(String key) {
        String resourceUrl = "https://%s/%s".formatted(cloudFrontProperties.domain(), key);

        CannedSignerRequest cannedRequest = CannedSignerRequest.builder()
                .resourceUrl(resourceUrl)
                .privateKey(cloudFrontPrivateKey)
                .keyPairId(cloudFrontProperties.keyPairId())
                .expirationDate(Instant.now().plus(URL_EXPIRES_IN))
                .build();

        return cloudFrontUtilities.getSignedUrlWithCannedPolicy(cannedRequest).url();
    }
}