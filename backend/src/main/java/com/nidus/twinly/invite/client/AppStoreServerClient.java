package com.nidus.twinly.invite.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.invite.config.AppStoreProperties;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
public class AppStoreServerClient {

    private static final String PRODUCTION_URL = "https://api.storekit.itunes.apple.com";
    private static final String SANDBOX_URL = "https://api.storekit-sandbox.itunes.apple.com";
    private static final String AUDIENCE = "appstoreconnect-v1";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final JsonMapper jsonMapper;
    private final AppStoreProperties appStoreProperties;
    private final RestClient restClient;

    public AppStoreServerClient(JsonMapper jsonMapper, AppStoreProperties appStoreProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.jsonMapper = jsonMapper;
        this.appStoreProperties = appStoreProperties;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public List<AppStoreTransaction> latestTransactions(String transactionId) {
        if (!appStoreProperties.configured()) {
            throw new BusinessException(ErrorCode.INVITE_REWARD_APPLE_OFFER_UNAVAILABLE);
        }

        try {
            return parse(subscriptionStatuses(PRODUCTION_URL, transactionId));
        } catch (HttpClientErrorException.NotFound e) {
            return sandboxTransactions(transactionId);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.APP_STORE_REQUEST_FAILED, e);
        }
    }

    private List<AppStoreTransaction> sandboxTransactions(String transactionId) {
        try {
            return parse(subscriptionStatuses(SANDBOX_URL, transactionId));
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.APP_STORE_REQUEST_FAILED, e);
        }
    }

    private String subscriptionStatuses(String baseUrl, String transactionId) {
        return restClient.get()
                .uri(baseUrl + "/inApps/v1/subscriptions/{transactionId}", transactionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .retrieve()
                .body(String.class);
    }

    private String token() {
        Instant now = Instant.now();

        return Jwts.builder()
                .header().keyId(appStoreProperties.keyId()).type("JWT").and()
                .issuer(appStoreProperties.issuerId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_LIFETIME)))
                .audience().add(AUDIENCE).and()
                .claim("bid", appStoreProperties.bundleId())
                .signWith(AppStoreKeys.parse(appStoreProperties.privateKey()), Jwts.SIG.ES256)
                .compact();
    }

    private List<AppStoreTransaction> parse(String body) {
        try {
            List<AppStoreTransaction> transactions = new ArrayList<>();

            for (JsonNode group : jsonMapper.readTree(body == null ? "{}" : body).path("data")) {
                for (JsonNode last : group.path("lastTransactions")) {
                    transactions.add(toTransaction(decodePayload(last.path("signedTransactionInfo").asString())));
                }
            }

            return transactions;
        } catch (JacksonException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.APP_STORE_REQUEST_FAILED, e);
        }
    }

    private JsonNode decodePayload(String jws) {
        String[] parts = jws.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWS 형식이 아닙니다.");
        }

        return jsonMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    private AppStoreTransaction toTransaction(JsonNode payload) {
        return new AppStoreTransaction(
                payload.path("productId").asString(null),
                payload.hasNonNull("offerType") ? payload.get("offerType").asInt() : null,
                payload.path("offerIdentifier").asString(null),
                instantOf(payload, "purchaseDate"),
                instantOf(payload, "expiresDate"));
    }

    private Instant instantOf(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? Instant.ofEpochMilli(payload.get(field).asLong()) : null;
    }
}
