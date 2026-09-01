package com.nidus.twinly.auth.client;

import com.nidus.twinly.auth.config.PortOneProperties;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

@Component
public class PortOneIdentityClient {

    private static final String BASE_URL = "https://api.portone.io";
    private static final String PORT_ONE_PREFIX = "PortOne ";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    public PortOneIdentityClient(JsonMapper jsonMapper, PortOneProperties portOneProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, PORT_ONE_PREFIX + portOneProperties.apiSecret())
                .build();
    }

    public Optional<PortOneIdentityVerificationBody> identityVerification(String identityVerificationId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/identity-verifications/{identityVerificationId}", identityVerificationId)
                    .retrieve()
                    .body(PortOneIdentityVerificationBody.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
        }
    }
}
