package com.nidus.twinly.app.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.app.dto.command.DotWorldFlagUpdateCommand;
import com.nidus.twinly.app.service.AppFeatureFlagService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppFeatureFlagAdminController.class)
@Import(SecurityConfig.class)
class AppFeatureFlagAdminControllerUnitTest {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AppFeatureFlagService appFeatureFlagService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("스위치 조회 시 200과 도트 세계 스위치 값을 반환한다")
    void featureFlags_success() throws Exception {
        // given: 도트 세계가 켜진 상태
        given(appFeatureFlagService.dotWorldEnabled()).willReturn(true);

        // when: 관리자 토큰으로 조회
        var result = mockMvc.perform(get("/admin/app/feature-flags")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 200 반환 + 켜짐 값이 담긴다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.dotWorldEnabled").value(true));
    }

    @Test
    @DisplayName("도트 세계 스위치 변경 성공 시 200을 반환하고 본문 값으로 서비스를 호출한다")
    void updateDotWorld_success() throws Exception {
        // when: 관리자 토큰으로 끄기 요청
        var result = mockMvc.perform(put("/admin/app/feature-flags/dot-world")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled": false}
                        """));

        // then: 200 반환 + 본문 값이 커맨드로 전달된다
        result.andExpect(status().isOk());
        then(appFeatureFlagService).should().updateDotWorld(new DotWorldFlagUpdateCommand(false));
    }

    @Test
    @DisplayName("도트 세계 스위치 변경 시 enabled가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void updateDotWorld_withoutEnabled_returns_400() throws Exception {
        // when: 필수 필드 enabled를 뺀 요청
        var result = mockMvc.perform(put("/admin/app/feature-flags/dot-world")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않는다
        result.andExpect(status().isBadRequest());
        then(appFeatureFlagService).should(never()).updateDotWorld(any());
    }

    @Test
    @DisplayName("관리자 토큰이 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void updateDotWorld_withoutAdminToken_returns_401() throws Exception {
        // when: 관리자 토큰 없이 변경 요청
        var result = mockMvc.perform(put("/admin/app/feature-flags/dot-world")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled": true}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않는다
        result.andExpect(status().isUnauthorized());
        then(appFeatureFlagService).should(never()).updateDotWorld(any());
    }
}
