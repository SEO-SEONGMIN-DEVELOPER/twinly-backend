package com.nidus.twinly.purchase.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.RevenueCatProperties;
import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
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

@Component
public class RevenueCatClient {

    private static final String BASE_URL = "https://api.revenuecat.com/v1";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int CONCURRENT_REQUEST_CODE = 7638;

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

        return body == null ? List.of() : body.entitlementsIn(environment);
    }

    private boolean isConcurrentRequest(HttpClientErrorException e) {
        try {
            return jsonMapper.readTree(e.getResponseBodyAsString()).path("code").asInt() == CONCURRENT_REQUEST_CODE;
        } catch (JacksonException parseFailure) {
            return false;
        }
    }
}
