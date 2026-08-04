package com.nidus.twinly.block.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.block.dto.result.BlockListItemResult;
import com.nidus.twinly.block.dto.result.BlockListResult;
import com.nidus.twinly.block.service.BlockService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.nidus.twinly.common.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BlockController.class)
@Import(SecurityConfig.class)
class BlockControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BlockService blockService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
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
    @DisplayName("차단 성공 시 200을 반환하고 인증 유저 id와 경로의 userId로 서비스를 호출한다")
    void block_success() throws Exception {
        // when: 차단자 인증 상태로 차단 API 호출
        var result = mockMvc.perform(put("/api/v1/blocks/{userId}", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 인증 유저 id·경로 userId로 서비스에 위임
        result.andExpect(status().isOk());
        then(blockService).should().block(1L, 42L);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void block_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 차단 API 호출
        var result = mockMvc.perform(put("/api/v1/blocks/{userId}", "42"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(blockService).should(never()).block(anyLong(), anyLong());
    }

    @Test
    @DisplayName("경로 변수 userId가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void block_with_non_numeric_userId_returns_400() throws Exception {
        // when: 경로 변수 userId를 숫자가 아닌 값으로 차단 API 호출
        var result = mockMvc.perform(put("/api/v1/blocks/{userId}", "abc")
                .header("Authorization", "Bearer access-token"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(blockService).should(never()).block(anyLong(), anyLong());
    }

    @Test
    @DisplayName("차단 해제 성공 시 200을 반환하고 인증 유저 id와 경로의 userId로 서비스를 호출한다")
    void unblock_success() throws Exception {
        // when: 차단자 인증 상태로 차단 해제 API 호출
        var result = mockMvc.perform(delete("/api/v1/blocks/{userId}", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 인증 유저 id·경로 userId로 서비스에 위임
        result.andExpect(status().isOk());
        then(blockService).should().unblock(1L, 42L);
    }

    @Test
    @DisplayName("차단 목록 조회 시 서비스 결과를 응답 JSON으로 변환하고 id는 문자열로 직렬화한다")
    void blockList_success() throws Exception {
        // given: 서비스가 차단 목록 1건을 반환
        given(blockService.blockList(1L))
                .willReturn(new BlockListResult(List.of(new BlockListItemResult(42L, "홍길동"))));

        // when: 차단 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/blocks")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + blockedUserId가 문자열로 직렬화된 JSON 응답
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].blockedUserId").value("42"))
                .andExpect(jsonPath("$.blocks[0].blockedUserName").value("홍길동"));
    }
}
