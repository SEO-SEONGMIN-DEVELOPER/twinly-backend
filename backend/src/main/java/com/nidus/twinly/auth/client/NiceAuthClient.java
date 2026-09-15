package com.nidus.twinly.auth.client;

import com.nidus.twinly.auth.config.NiceProperties;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
public class NiceAuthClient {

    private static final String BASE_URL = "https://auth.niceid.co.kr";
    private static final String DEV_LANG_HEADER = "X-Intc-DevLang";
    private static final String DEV_LANG = "Linux/Java";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GRANT_TYPE = "client_credentials";
    private static final String REQUEST_NO_PREFIX = "TWINLY-";
    private static final String SUCCESS_CODE = "0000";
    private static final Set<String> TOKEN_REJECTED_CODES = Set.of(
            "1003", "1004", "1012", "1013", "3041", "3042", "3043", "3044", "3045", "3046");
    private static final Set<String> RESULT_NOT_AVAILABLE_CODES = Set.of("3029", "3032", "3033");
    private static final List<String> SVC_TYPES_MOBILE = List.of("M");
    private static final String METHOD_TYPE_GET = "GET";
    private static final List<String> EXP_MODS_CLOSE_BUTTON = List.of("closeButtonOn");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(7);

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final NiceProperties niceProperties;
    private final String basicAuthorization;

    public NiceAuthClient(JsonMapper jsonMapper, NiceProperties niceProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(DEV_LANG_HEADER, DEV_LANG)
                .build();
        this.jsonMapper = jsonMapper;
        this.niceProperties = niceProperties;

        String credentials = niceProperties.clientId() + ":" + niceProperties.clientSecret();
        this.basicAuthorization = BASIC_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public NiceTokenBody issueToken() {
        NiceTokenBody body;

        try {
            body = restClient.post()
                    .uri("/ido/intc/v1.0/auth/token")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthorization)
                    .body(Map.of(
                            "grant_type", GRANT_TYPE,
                            "request_no", newRequestNo()))
                    .retrieve()
                    .body(NiceTokenBody.class);
        } catch (RestClientException e) {
            throw failed("토큰 발급", e);
        }

        if (body == null || !SUCCESS_CODE.equals(body.resultCode())) {
            throw failed("토큰 발급", body == null ? null : body.resultCode(), body == null ? null : body.resultMessage());
        }

        if (body.accessToken() == null || body.ticket() == null || body.iterators() == null || body.expiresIn() == null) {
            WarnLog.log(log, "NICE 토큰 응답에 필수 항목이 없습니다.", field("resultCode", body.resultCode()));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        return body;
    }

    public NiceAuthUrlBody requestAuthUrl(String accessToken, String requestNo) {
        NiceAuthUrlBody body;

        try {
            body = restClient.post()
                    .uri("/ido/intc/v1.0/auth/url")
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .body(Map.of(
                            "request_no", requestNo,
                            "return_url", niceProperties.returnUrl(),
                            "close_url", niceProperties.closeUrl(),
                            "svc_types", SVC_TYPES_MOBILE,
                            "method_type", METHOD_TYPE_GET,
                            "exp_mods", EXP_MODS_CLOSE_BUTTON))
                    .retrieve()
                    .body(NiceAuthUrlBody.class);
        } catch (RestClientException e) {
            throw failed("인증 URL 요청", e);
        }

        if (body == null || !SUCCESS_CODE.equals(body.resultCode())) {
            throw failed("인증 URL 요청", body == null ? null : body.resultCode(), body == null ? null : body.resultMessage());
        }

        if (body.authUrl() == null || body.transactionId() == null) {
            WarnLog.log(log, "NICE 인증 URL 응답에 필수 항목이 없습니다.", field("resultCode", body.resultCode()));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        return body;
    }

    public NiceAuthResultBody requestResult(String accessToken, String webTransactionId, String transactionId, String requestNo) {
        NiceAuthResultBody body;

        try {
            body = restClient.post()
                    .uri("/ido/intc/v1.0/auth/result")
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .body(Map.of(
                            "web_transaction_id", webTransactionId,
                            "transaction_id", transactionId,
                            "request_no", requestNo))
                    .retrieve()
                    .body(NiceAuthResultBody.class);
        } catch (RestClientException e) {
            throw failed("인증 결과 요청", e);
        }

        if (body == null || !SUCCESS_CODE.equals(body.resultCode())) {
            throw failed("인증 결과 요청", body == null ? null : body.resultCode(), body == null ? null : body.resultMessage());
        }

        if (body.encData() == null || body.integrityValue() == null) {
            WarnLog.log(log, "NICE 인증 결과 응답에 필수 항목이 없습니다.", field("resultCode", body.resultCode()));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        return body;
    }

    private BusinessException failed(String action, RestClientException e) {
        if (e instanceof HttpStatusCodeException statusException) {
            NiceErrorBody errorBody = parseErrorBody(statusException.getResponseBodyAsString());

            if (errorBody != null) {
                return failed(action, errorBody.resultCode(), errorBody.resultMessage());
            }
        }

        return new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
    }

    private BusinessException failed(String action, String resultCode, String resultMessage) {
        WarnLog.log(log, "NICE 요청이 실패했습니다.", field("action", action), field("resultCode", resultCode), field("resultMessage", resultMessage));

        if (resultCode != null && TOKEN_REJECTED_CODES.contains(resultCode)) {
            return new NiceTokenRejectedException(resultCode);
        }

        if (resultCode != null && RESULT_NOT_AVAILABLE_CODES.contains(resultCode)) {
            return new NiceResultNotAvailableException(resultCode);
        }

        return new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    private NiceErrorBody parseErrorBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            return jsonMapper.readValue(responseBody, NiceErrorBody.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String newRequestNo() {
        return REQUEST_NO_PREFIX + UUID.randomUUID();
    }
}
