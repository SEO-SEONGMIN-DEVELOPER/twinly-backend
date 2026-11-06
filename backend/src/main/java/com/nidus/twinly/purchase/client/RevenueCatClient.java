package com.nidus.twinly.purchase.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class RevenueCatClient {

    private static final String BASE_URL = "https://api.revenuecat.com/v1";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int CONCURRENT_REQUEST_CODE = 7638;
    private static final char PLAY_BASE_PLAN_SEPARATOR = ':';

    private final JsonMapper jsonMapper;
    private final RestClient restClient;
    private final RevenueCatEnvironment environment;

    public RevenueCatClient(JsonMapper jsonMapper, RevenueCatProperties revenueCatProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(revenueCatProperties.connectTimeout());
        requestFactory.setReadTimeout(revenueCatProperties.readTimeout());

        this.jsonMapper = jsonMapper;
        this.environment = revenueCatProperties.environment();
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + revenueCatProperties.secretApiKey())
                .build();
    }

    public List<RevenueCatEntitlement> entitlements(String appUserId) {
        RevenueCatSubscriberBody body = subscriber(appUserId);

        return body == null ? List.of() : body.entitlementsIn(environment);
    }

    public RevenueCatSubscriptions subscriptions(String appUserId, String entitlement) {
        RevenueCatSubscriberBody body = subscriber(appUserId);

        return body == null ? new RevenueCatSubscriptions(null, List.of()) : body.subscriptionsIn(environment, entitlement);
    }

    public void defer(String appUserId, String productId, Instant expiresAt) {
        try {
            restClient.post()
                    .uri("/subscribers/{appUserId}/subscriptions/{productId}/defer", appUserId, playSubscriptionId(productId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiry_time_ms", expiresAt.toEpochMilli()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REVENUE_CAT_GRANT_FAILED, e);
        }
    }

    private RevenueCatSubscriberBody subscriber(String appUserId) {
        try {
            return restClient.get()
                    .uri("/subscribers/{appUserId}", appUserId)
                    .retrieve()
                    .body(RevenueCatSubscriberBody.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new BusinessException(isConcurrentRequest(e) ? ErrorCode.REVENUE_CAT_SYNC_CONFLICT : ErrorCode.REVENUE_CAT_SYNC_FAILED, e);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REVENUE_CAT_SYNC_FAILED, e);
        }
    }

    private String playSubscriptionId(String productId) {
        int separator = productId.indexOf(PLAY_BASE_PLAN_SEPARATOR);

        return separator < 0 ? productId : productId.substring(0, separator);
    }

    public void grantPromotional(String appUserId, String entitlement, Instant expiresAt) {
        try {
            restClient.get()
                    .uri("/subscribers/{appUserId}", appUserId)
                    .retrieve()
                    .toBodilessEntity();

            restClient.post()
                    .uri("/subscribers/{appUserId}/entitlements/{entitlement}/promotional", appUserId, entitlement)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("end_time_ms", expiresAt.toEpochMilli()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REVENUE_CAT_GRANT_FAILED, e);
        }
    }

    private boolean isConcurrentRequest(HttpClientErrorException e) {
        try {
            return jsonMapper.readTree(e.getResponseBodyAsString()).path("code").asInt() == CONCURRENT_REQUEST_CODE;
        } catch (JacksonException parseFailure) {
            return false;
        }
    }
}
