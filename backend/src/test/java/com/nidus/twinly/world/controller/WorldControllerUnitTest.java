package com.nidus.twinly.world.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import com.nidus.twinly.world.dto.result.WorldSectorsResult;
import com.nidus.twinly.world.service.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorldController.class)
@Import(SecurityConfig.class)
class WorldControllerUnitTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 16);
    private static final String FROM = "2026-10-16T13:00:00+09:00";
    private static final String TO = "2026-10-16T14:00:00+09:00";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WorldService worldService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString())).willReturn(new UserInfo(12L));
    }

    @Test
    @DisplayName("건물 화면 조회 성공 시 200 과 함께 인증 유저 id·경로·쿼리를 그대로 서비스에 넘기고 결과를 JSON 으로 내려 준다")
    void sectors_success() throws Exception {
        // given: 서비스가 내 트윈 한 명이 든 1시간 창을 돌려준다
        ObjectNode actor = new ObjectMapper().createObjectNode().put("ref", "user:12").put("displayName", "나");
        given(worldService.sectors(12L, "S034", DATE, FROM, TO)).willReturn(new WorldSectorsResult(
                "7", DATE, "S034", "sha256:aa", OffsetDateTime.parse("2026-10-16T13:20:00+09:00"), "user:12",
                List.of(actor), List.of(), List.of(), List.of(), List.of(), List.of()));

        // when: 인증 상태로 건물 화면 조회
        var result = mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "S034").header("Authorization", "Bearer access-token")
                .param("date", "2026-10-16").param("from", FROM).param("to", TO));

        // then: 200 + 맨 위 칸과 원소가 그대로, 빈 목록은 [] 로 나간다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.poolNumber").value("7"))
                .andExpect(jsonPath("$.date").value("2026-10-16"))
                .andExpect(jsonPath("$.focusRef").value("user:12"))
                .andExpect(jsonPath("$.serverNow").value("2026-10-16T13:20:00+09:00"))
                .andExpect(jsonPath("$.actors[0].displayName").value("나"))
                .andExpect(jsonPath("$.talks").isEmpty())
                .andExpect(jsonPath("$.showcaseId").doesNotExist());
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401 을 반환하고 서비스를 호출하지 않는다")
    void sectors_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 건물 화면 조회
        var result = mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "S034")
                .param("date", "2026-10-16").param("from", FROM).param("to", TO));

        // then: 401 + 서비스 미호출
        result.andExpect(status().isUnauthorized());
        then(worldService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("from·to 가 없거나 날짜·구역 코드 형식이 틀리면 400 INVALID_REQUEST 이고 서비스를 호출하지 않는다")
    void sectors_with_invalid_params_returns_400() throws Exception {
        // when & then: to 없음
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "S034").header("Authorization", "Bearer access-token")
                        .param("date", "2026-10-16").param("from", FROM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));

        // when & then: 구역 코드 소문자
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "s034").header("Authorization", "Bearer access-token")
                        .param("date", "2026-10-16").param("from", FROM).param("to", TO))
                .andExpect(status().isBadRequest());

        // when & then: 없는 날짜
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "S034").header("Authorization", "Bearer access-token")
                        .param("date", "2026-13-01").param("from", FROM).param("to", TO))
                .andExpect(status().isBadRequest());

        // then: 서비스는 한 번도 호출되지 않음
        then(worldService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("서비스의 도메인 예외는 403 WORLD_PARTICIPATION_REQUIRED, 404 WORLD_TRACK_NOT_FOUND 로 매핑된다")
    void sectors_maps_domain_exceptions() throws Exception {
        // given: 미참여자와 트랙 없음 예외를 차례로 던진다
        willThrow(new BusinessException(ErrorCode.WORLD_PARTICIPATION_REQUIRED))
                .willThrow(new BusinessException(ErrorCode.WORLD_TRACK_NOT_FOUND))
                .given(worldService).sectors(12L, "S034", DATE, FROM, TO);

        // when & then: 첫 호출은 403
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "S034").header("Authorization", "Bearer access-token")
                        .param("date", "2026-10-16").param("from", FROM).param("to", TO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_PARTICIPATION_REQUIRED.name()));

        // when & then: 두 번째 호출은 404
        mockMvc.perform(get("/api/v1/world/sectors/{sectorCode}", "S034").header("Authorization", "Bearer access-token")
                        .param("date", "2026-10-16").param("from", FROM).param("to", TO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_TRACK_NOT_FOUND.name()));
    }
}
