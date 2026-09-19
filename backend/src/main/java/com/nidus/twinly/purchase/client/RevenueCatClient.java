package com.nidus.twinly.purchase.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.RevenueCatProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Component
public class RevenueCatClient {

    private static final String BASE_URL = "https://api.revenuecat.com/v1";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int CONCURRENT_REQUEST_CODE = 7638;

    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public RevenueCatClient(JsonMapper jsonMapper, RevenueCatProperties revenueCatProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(revenueCatProperties.connectTimeout());
        requestFactory.setReadTimeout(revenueCatProperties.readTimeout());

        this.jsonMapper = jsonMapper;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + revenueCatProperties.secretApiKey())
                .build();
    }

    public List<RevenueCatEntitlement> entitlements(String appUserId) {
        RevenueCatSubscriberBody body;

        try {
            body = restClient.get()
                    .uri("/subscribers/{appUserId}", appUserId)
                    .retrieve()
                    .body(RevenueCatSubscriberBody.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new BusinessException(isConcurrentRequest(e) ? ErrorCode.REVENUE_CAT_SYNC_CONFLICT : ErrorCode.REVENUE_CAT_SYNC_FAILED, e);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REVENUE_CAT_SYNC_FAILED, e);
        }

        Map<String, RevenueCatSubscriberBody.Entitlement> entitlements = entitlementsOf(body);

        return entitlements.entrySet().stream()
                .map(entry -> new RevenueCatEntitlement(entry.getKey(), entry.getValue().expiresDate()))
                .toList();
    }

    private boolean isConcurrentRequest(HttpClientErrorException e) {
        try {
            return jsonMapper.readTree(e.getResponseBodyAsString()).path("code").asInt() == CONCURRENT_REQUEST_CODE;
        } catch (JacksonException parseFailure) {
            return false;
        }
    }

    private Map<String, RevenueCatSubscriberBody.Entitlement> entitlementsOf(RevenueCatSubscriberBody body) {
        if (body == null || body.subscriber() == null || body.subscriber().entitlements() == null) {
            return Map.of();
        }

        return body.subscriber().entitlements();
    }
}
