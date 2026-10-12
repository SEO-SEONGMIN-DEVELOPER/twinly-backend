package com.nidus.twinly.map.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.map.dto.result.MapTwinResult;
import com.nidus.twinly.map.dto.result.MapTwinSceneResult;
import com.nidus.twinly.map.dto.result.MapTwinsResult;
import com.nidus.twinly.map.service.MapService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MapController.class)
@Import(SecurityConfig.class)
class MapControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MapService mapService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString()))
                .willReturn(new UserInfo(12L));
    }

    @Test
    @DisplayName("조회 성공 시 200과 KST serverNow, 문자열 userId, 표시명, 줄인 장면을 반환한다")
    void twins_success() throws Exception {
        // given: 서비스가 접점 없는 트윈 한 명의 action·move 장면을 돌려준다
        given(mapService.twins(12L)).willReturn(new MapTwinsResult(
                OffsetDateTime.parse("2026-10-10T14:05:12+09:00"),
                List.of(new MapTwinResult(123L, "누군가의 트윈", List.of(
                        new MapTwinSceneResult("action",
                                OffsetDateTime.parse("2026-10-10T13:00:00+09:00"),
                                OffsetDateTime.parse("2026-10-10T13:40:00+09:00"),
                                "P0012", null, null),
                        new MapTwinSceneResult("move",
                                OffsetDateTime.parse("2026-10-10T13:40:00+09:00"),
                                OffsetDateTime.parse("2026-10-10T13:50:00+09:00"),
                                "P0158", "P0012", "walk"))))));

        // when: 인증 상태로 지도 트윈 API 호출
        var result = mockMvc.perform(get("/api/v1/map/twins")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + userId 는 문자열, 시각은 +09:00, action 의 move 전용 필드는 null 로 나간다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.serverNow").value("2026-10-10T14:05:12+09:00"))
                .andExpect(jsonPath("$.twins", hasSize(1)))
                .andExpect(jsonPath("$.twins[0].userId").value("123"))
                .andExpect(jsonPath("$.twins[0].displayName").value("누군가의 트윈"))
                .andExpect(jsonPath("$.twins[0].scenes[0].type").value("action"))
                .andExpect(jsonPath("$.twins[0].scenes[0].startsAt").value("2026-10-10T13:00:00+09:00"))
                .andExpect(jsonPath("$.twins[0].scenes[0].placeCode").value("P0012"))
                .andExpect(jsonPath("$.twins[0].scenes[0].fromPlaceCode").value(nullValue()))
                .andExpect(jsonPath("$.twins[0].scenes[0].travelMode").value(nullValue()))
                .andExpect(jsonPath("$.twins[0].scenes[1].type").value("move"))
                .andExpect(jsonPath("$.twins[0].scenes[1].fromPlaceCode").value("P0012"))
                .andExpect(jsonPath("$.twins[0].scenes[1].placeCode").value("P0158"))
                .andExpect(jsonPath("$.twins[0].scenes[1].travelMode").value("walk"))
                .andExpect(jsonPath("$.twins[0].scenes[1].narration").doesNotExist())
                .andExpect(jsonPath("$.twins[0].scenes[1].with").doesNotExist());
        then(mapService).should().twins(12L);
    }

    @Test
    @DisplayName("보여 줄 트윈이 없으면 200과 빈 twins 를 반환한다")
    void twins_empty_returns_200() throws Exception {
        // given: 서비스가 빈 목록을 돌려준다
        given(mapService.twins(12L)).willReturn(new MapTwinsResult(
                OffsetDateTime.parse("2026-10-10T14:05:12+09:00"), List.of()));

        // when: 인증 상태로 지도 트윈 API 호출
        var result = mockMvc.perform(get("/api/v1/map/twins")
                .header("Authorization", "Bearer access-token"));

        // then: 오류가 아니라 200 + twins: []
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.twins", hasSize(0)));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void twins_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 지도 트윈 API 호출
        var result = mockMvc.perform(get("/api/v1/map/twins"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(mapService).should(never()).twins(anyLong());
    }
}
