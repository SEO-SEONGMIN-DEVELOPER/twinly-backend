package com.nidus.twinly.auth.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NiceIdentityReturnController.class)
@Import(SecurityConfig.class)
class NiceIdentityReturnControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("return URL은 인증 헤더 없이 브라우저가 열어도 200과 '앱으로 돌아가 주세요' 안내 HTML을 돌려준다")
    void identityReturn_without_auth_returns_html() throws Exception {
        // when: NICE 표준창이 리다이렉트한 브라우저가 web_transaction_id 를 붙여 GET 으로 호출 (앱의 가로채기가 실패한 경우)
        var result = mockMvc.perform(get("/api/v1/auth/onboarding/identity/return")
                .queryParam("web_transaction_id", "ZGIxOGZkYjUtMjE4NC00MDZmLTkxZjgtM2ZhNjA0OTdiZTY2"));

        // then: 익명 세션 검사 없이 200, HTML 안내, 쿼리로 받은 값은 페이지에 노출되지 않음
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("앱으로 돌아가 주세요")))
                .andExpect(content().string(not(containsString("ZGIxOGZk"))));
    }

    @Test
    @DisplayName("close URL은 인증 헤더 없이 브라우저가 열어도 200과 종료 안내 HTML을 돌려준다")
    void identityClose_without_auth_returns_html() throws Exception {
        // when: 표준창의 닫기 버튼이 close_url 로 이동 (앱의 가로채기가 실패한 경우)
        var result = mockMvc.perform(get("/api/v1/auth/onboarding/identity/close"));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("본인인증을 종료했습니다")));
    }

    @Test
    @DisplayName("return URL은 GET 만 받는다 (method_type 을 GET 으로 요청하므로 POST 는 405)")
    void identityReturn_post_is_not_allowed() throws Exception {
        // when: POST 로 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/identity/return"));

        // then: 405
        result.andExpect(status().isMethodNotAllowed());
    }
}
