package com.nidus.twinly.user.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.service.UserPersonaService;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAdminController.class)
@Import(SecurityConfig.class)
class UserAdminControllerUnitTest {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserPersonaService userPersonaService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("페르소나 초기화 성공 시 200을 반환하고 경로의 유저 id 로 서비스를 호출한다")
    void resetPersona_success() throws Exception {
        // when: 관리자 토큰으로 19번 유저 페르소나 초기화 요청
        var result = mockMvc.perform(delete("/admin/users/19/persona")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 200 반환 + 19번 유저로 서비스 호출
        result.andExpect(status().isOk());
        then(userPersonaService).should().resetPersona(19L);
    }

    @Test
    @DisplayName("유저 id 가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void resetPersona_withInvalidUserId_returns_400() throws Exception {
        // when: 숫자가 아닌 유저 id 로 요청
        var result = mockMvc.perform(delete("/admin/users/abc/persona")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
        then(userPersonaService).should(never()).resetPersona(any());
    }

    @Test
    @DisplayName("없는 유저면 서비스의 USER_NOT_FOUND 예외가 404로 매핑된다")
    void resetPersona_unknownUser_returns_404() throws Exception {
        // given: 서비스가 유저를 찾지 못함
        willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND)).given(userPersonaService).resetPersona(999L);

        // when: 없는 유저 id 로 요청
        var result = mockMvc.perform(delete("/admin/users/999/persona")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 404 + USER_NOT_FOUND
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("관리자 토큰이 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void resetPersona_withoutAdminToken_returns_401() throws Exception {
        // when: 관리자 토큰 없이 요청
        var result = mockMvc.perform(delete("/admin/users/19/persona"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(userPersonaService).should(never()).resetPersona(any());
    }
}
