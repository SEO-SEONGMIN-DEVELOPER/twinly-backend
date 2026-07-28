package com.nidus.twinly.legal.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.legal.dto.result.LegalPoliciesItemResult;
import com.nidus.twinly.legal.dto.result.LegalPoliciesResult;
import com.nidus.twinly.legal.service.LegalService;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegalController.class)
class LegalControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LegalService legalService;

    // LegalController가 직접 쓰진 않지만, WebMvcConfig가 두 resolver를 모두 주입받고
    // 각 resolver가 이 서비스에 의존하므로 슬라이스 기동에 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("정책 목록 조회는 인증 없이 200을 반환하고 version을 문자열로 직렬화한다")
    void policies_success() throws Exception {
        // given: 서비스가 시행 중인 정책 1건을 반환
        given(legalService.policies()).willReturn(new LegalPoliciesResult(List.of(
                new LegalPoliciesItemResult(
                        "terms_of_service",
                        "서비스 이용약관",
                        2,
                        "https://cdn.twinly.app/terms/v2.html",
                        true))));

        // when: 인증 헤더 없이 정책 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/legal/policies"));

        // then: 200 반환 + version이 문자열로 직렬화된 JSON 응답 + 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.policies.length()").value(1))
                .andExpect(jsonPath("$.policies[0].policyId").value("terms_of_service"))
                .andExpect(jsonPath("$.policies[0].title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.policies[0].version").value("2"))
                .andExpect(jsonPath("$.policies[0].url").value("https://cdn.twinly.app/terms/v2.html"))
                .andExpect(jsonPath("$.policies[0].isRequired").value(true));
        then(legalService).should().policies();
    }

    @Test
    @DisplayName("시행 중인 버전이 없는 정책은 version/url/isRequired가 비어 있는 상태로 응답한다")
    void policies_without_effective_version() throws Exception {
        // given: 서비스가 버전 정보 없는(시행 전) 정책 1건을 반환
        given(legalService.policies()).willReturn(new LegalPoliciesResult(List.of(
                new LegalPoliciesItemResult("privacy_policy", "개인정보 처리방침", null, null, null))));

        // when: 정책 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/legal/policies"));

        // then: 200 반환 + 정책명 필드만 채워지고 버전 관련 필드는 비어 있음
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.policies.length()").value(1))
                .andExpect(jsonPath("$.policies[0].policyId").value("privacy_policy"))
                .andExpect(jsonPath("$.policies[0].title").value("개인정보 처리방침"))
                .andExpect(jsonPath("$.policies[0].version").doesNotExist())
                .andExpect(jsonPath("$.policies[0].url").doesNotExist())
                .andExpect(jsonPath("$.policies[0].isRequired").doesNotExist());
    }

    @Test
    @DisplayName("노출할 정책이 하나도 없으면 200과 빈 배열을 반환한다")
    void policies_empty() throws Exception {
        // given: 서비스가 빈 정책 목록을 반환
        given(legalService.policies()).willReturn(new LegalPoliciesResult(List.of()));

        // when: 정책 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/legal/policies"));

        // then: 200 반환 + policies가 빈 배열
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.policies").isArray())
                .andExpect(jsonPath("$.policies").isEmpty());
    }
}
