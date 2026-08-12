package com.nidus.twinly.user.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.user.dto.result.UsersPageResult;
import com.nidus.twinly.user.dto.result.UsersResult;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 필수.
    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("유저 목록 조회 성공 시 200과 함께 id를 문자열로 직렬화하고 커서·limit을 서비스에 위임한다")
    void users_success() throws Exception {
        // given: 커서 이후 유저가 더 남아 있는 조회 결과
        given(userService.users(15L, 2)).willReturn(new UsersResult(
                List.of(16L, 17L), new UsersPageResult(17L, true)));

        // when: 커서와 limit을 지정해 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users")
                .param("cursor", "15")
                .param("limit", "2"));

        // then: 200 반환 + 숫자 id는 문자열로 직렬화 + 커서·limit 그대로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", hasSize(2)))
                .andExpect(jsonPath("$.userIds[0]", is("16")))
                .andExpect(jsonPath("$.userIds[1]", is("17")))
                .andExpect(jsonPath("$.page.nextCursor", is("17")))
                .andExpect(jsonPath("$.page.hasMore", is(true)));
        then(userService).should().users(15L, 2);
    }

    @Test
    @DisplayName("커서와 limit을 생략하면 서비스에 null로 위임하고 마지막 페이지는 nextCursor가 null이다")
    void users_without_params_delegates_null() throws Exception {
        // given: 더 받을 것이 없는 마지막 페이지 조회 결과
        given(userService.users(null, null)).willReturn(new UsersResult(
                List.of(1L), new UsersPageResult(null, false)));

        // when: 파라미터 없이 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users"));

        // then: 200 반환 + nextCursor는 null + 서비스에 null/null 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.page.nextCursor", is(nullValue())))
                .andExpect(jsonPath("$.page.hasMore", is(false)));
        then(userService).should().users(null, null);
    }

    @Test
    @DisplayName("커서가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void users_with_non_numeric_cursor_returns_400() throws Exception {
        // when: 커서를 숫자가 아닌 값으로 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users")
                .param("cursor", "abc"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(userService).should(never()).users(any(), anyInt());
    }

    @Test
    @DisplayName("limit이 허용 범위를 벗어나면 400을 반환하고 서비스를 호출하지 않는다")
    void users_with_out_of_range_limit_returns_400() throws Exception {
        // when: limit을 상한(500)보다 크게 지정해 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users")
                .param("limit", "501"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(userService).should(never()).users(any(), anyInt());
    }
}
