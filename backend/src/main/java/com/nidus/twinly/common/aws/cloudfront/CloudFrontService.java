package com.nidus.twinly.common.aws.cloudfront;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;
import software.amazon.awssdk.services.cloudfront.model.CustomSignerRequest;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CloudFrontService {

    private static final Duration URL_EXPIRES_IN = Duration.ofMinutes(10);
    private static final Duration MIN_REMAINING_LIFETIME = Duration.ofMinutes(5);
    private static final String SCOPE_SEPARATOR = "/";
    private static final String SCOPE_WILDCARD = "/*";
    private static final char QUERY_SEPARATOR = '?';

    private final CloudFrontUtilities cloudFrontUtilities;
    private final PrivateKey cloudFrontPrivateKey;
    private final CloudFrontProperties cloudFrontProperties;

    private final Map<String, ScopeSignature> signatureByScope = new ConcurrentHashMap<>();

    public String getSignedUrl(String key) {
        return "%s%c%s".formatted(getPublicUrl(key), QUERY_SEPARATOR, signatureQuery(scopeOf(key)));
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

    public String getPublicUrl(String key) {
        return "https://%s/%s".formatted(cloudFrontProperties.domain(), key);
    }

    private String scopeOf(String key) {
        int separatorIndex = key.indexOf(SCOPE_SEPARATOR);

        if (separatorIndex <= 0) {
            throw new IllegalArgumentException("범위를 판별할 수 없는 key 입니다: " + key);
        }

        return key.substring(0, separatorIndex);
    }

    private String signatureQuery(String scope) {
        Instant now = Instant.now();
        ScopeSignature cached = signatureByScope.get(scope);

        if (cached != null && cached.isReusableAt(now)) {
            return cached.query();
        }

        ScopeSignature issued = issue(scope, now);
        signatureByScope.put(scope, issued);

        return issued.query();
    }

    private ScopeSignature issue(String scope, Instant now) {
        Instant expiresAt = now.plus(URL_EXPIRES_IN);

        CustomSignerRequest customRequest = CustomSignerRequest.builder()
                .resourceUrl(getPublicUrl(scope + SCOPE_WILDCARD))
                .privateKey(cloudFrontPrivateKey)
                .keyPairId(cloudFrontProperties.keyPairId())
                .expirationDate(expiresAt)
                .build();

        String signedUrl = cloudFrontUtilities.getSignedUrlWithCustomPolicy(customRequest).url();

        return new ScopeSignature(
                signedUrl.substring(signedUrl.indexOf(QUERY_SEPARATOR) + 1),
                expiresAt.minus(MIN_REMAINING_LIFETIME));
    }

    private record ScopeSignature(String query, Instant reusableUntil) {

        private boolean isReusableAt(Instant now) {
            return now.isBefore(reusableUntil);
        }
    }
}
