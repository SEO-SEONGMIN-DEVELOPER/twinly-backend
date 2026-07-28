package com.nidus.twinly.main.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.main.dto.result.MainTabResult;
import com.nidus.twinly.main.dto.result.MainTabSeasonResult;
import com.nidus.twinly.main.service.MainService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MainController.class)
class MainControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MainService mainService;

    // MainController는 @CurrentUser만 쓰지만, WebMvcConfig가 두 resolver를 모두 주입받고
    // 각 resolver가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString()))
                .willReturn(new UserInfo(1L));
    }

    @Test
    @DisplayName("메인 탭 조회 성공 시 200과 서비스 결과를 응답 JSON으로 반환하고 seasonId는 문자열로 직렬화한다")
    void mainTab_success() throws Exception {
        // given: 서비스가 진행률 42%인 시즌과 미읽음 개수를 반환
        given(mainService.mainTab(1L)).willReturn(new MainTabResult(
                new MainTabSeasonResult(7L, Instant.parse("2026-07-26T03:00:00Z"), "42%"),
                3,
                5
        ));

        // when: 인증 상태로 메인 탭 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/main")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + seasonId는 문자열, serverNow는 date-time 문자열, 나머지 필드가 그대로 매핑됨
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.season.seasonId").value("7"))
                .andExpect(jsonPath("$.season.serverNow").isString())
                .andExpect(jsonPath("$.season.progress").value("42%"))
                .andExpect(jsonPath("$.unreadChatRoomCount").value(3))
                .andExpect(jsonPath("$.unreadNotificationCount").value(5));

        // then: 인증 유저 id로 서비스에 위임
        then(mainService).should().mainTab(1L);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void mainTab_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 메인 탭 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/main"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(mainService).should(never()).mainTab(anyLong());
    }

    @Test
    @DisplayName("Authorization 헤더가 Bearer 스킴이 아니면 401을 반환하고 서비스를 호출하지 않는다")
    void mainTab_with_non_bearer_auth_returns_401() throws Exception {
        // when: Bearer가 아닌 인증 헤더로 메인 탭 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/main")
                .header("Authorization", "Basic access-token"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(mainService).should(never()).mainTab(anyLong());
    }
}
