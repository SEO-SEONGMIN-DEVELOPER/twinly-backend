package com.nidus.twinly.support;

import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 오류 응답과 API 문서가 모두 ErrorCode 값만 노출하는지 고정한다. */
class ApiErrorContractIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("존재하지 않는 경로는 500이 아니라 404 NOT_FOUND로 응답한다")
    void unknown_path_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 500이 아니라 405 METHOD_NOT_ALLOWED로 응답한다")
    void unsupported_method_returns_405() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/survey-questions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("API 문서의 모든 오류 응답 description은 ErrorCode 이름으로만 채워진다")
    void api_docs_expose_only_error_code_names() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();

        Set<String> definedCodes = Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toSet());

        // 4xx/5xx 응답의 description만 추출한다. (2xx는 springdoc의 "OK"를 그대로 둔다)
        Matcher matcher = Pattern.compile("\"([45]\\d\\d)\":\\{\"description\":\"([^\"]*)\"").matcher(json);
        int checked = 0;
        while (matcher.find()) {
            String status = matcher.group(1);
            for (String name : matcher.group(2).split(",")) {
                assertThat(definedCodes)
                        .as("문서의 %s 응답 description '%s'가 ErrorCode에 없다", status, matcher.group(2))
                        .contains(name.trim());
            }
            checked++;
        }
        assertThat(checked).isGreaterThan(0);
    }

    @Test
    @DisplayName("문서의 401은 인증 방식별 코드와 컨트롤러가 선언한 코드의 합집합이다")
    void api_docs_merge_auth_and_declared_401() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();

        // 익명 세션 인증: 리졸버·AnonService가 던지는 4가지가 모두 나온다
        assertThat(description(json, "/api/v1/onboarding/interests", "401"))
                .contains("UNAUTHORIZED", "INVALID_TOKEN", "INVALID_ANON_SESSION", "TOKEN_EXPIRED");

        // 유저 인증: 리졸버·UserService가 던지는 3가지가 모두 나온다
        assertThat(description(json, "/api/v1/me/profile", "401"))
                .contains("UNAUTHORIZED", "INVALID_TOKEN", "WITHDRAWN_USER");

        // 인증 애노테이션이 없고 컨트롤러만 선언한 경우 그 값이 유지된다
        assertThat(description(json, "/api/v1/auth/refresh", "401"))
                .contains("INVALID_REFRESH_TOKEN", "REFRESH_TOKEN_ALREADY_REVOKED");
    }

    @Test
    @DisplayName("경로 미매칭·메서드 미지원은 전역 동작이므로 개별 오퍼레이션 문서에 섞이지 않는다")
    void api_docs_do_not_mix_global_routing_errors() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();

        // 문서에 있는 경로는 매핑이 존재하므로 NOT_FOUND(경로 없음)가 날 수 없다.
        // 그 오퍼레이션의 404는 컨트롤러가 선언한 도메인 코드뿐이어야 한다.
        assertThat(description(json, "/api/v1/people/{userId}/profile", "404")).isEqualTo("USER_NOT_FOUND");

        Matcher matcher = Pattern.compile("\"(404|405)\":\\{\"description\":\"([^\"]*)\"").matcher(json);
        while (matcher.find()) {
            assertThat(matcher.group(2).split(", "))
                    .as("오퍼레이션의 %s 응답에 전역 라우팅 코드가 섞였다: %s", matcher.group(1), matcher.group(2))
                    .doesNotContain("NOT_FOUND", "METHOD_NOT_ALLOWED");
        }
    }

    private String description(String json, String path, String status) {
        int from = json.indexOf("\"" + path + "\":");
        assertThat(from).as("문서에 %s 경로가 없다", path).isNotNegative();

        Matcher matcher = Pattern.compile("\"" + status + "\":\\{\"description\":\"([^\"]*)\"")
                .matcher(json.substring(from, Math.min(from + 3000, json.length())));
        assertThat(matcher.find()).as("%s에 %s 응답이 없다", path, status).isTrue();

        return matcher.group(1);
    }
}
