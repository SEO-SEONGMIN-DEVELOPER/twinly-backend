package com.nidus.twinly.world.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.service.UserService;
import com.nidus.twinly.world.dto.command.WorldTrackSaveCommand;
import com.nidus.twinly.world.service.WorldTrackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorldTrackController.class)
@Import(SecurityConfig.class)
class WorldTrackControllerUnitTest {

    private static final String PATH = "/internal/v1/pools/{poolNumber}/tracks/{date}/{sectorCode}";
    private static final byte[] BODY = {31, -117, 8, 0};

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WorldTrackService worldTrackService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("인증 없이 200, 본문 없음이고 경로 값·Content-Encoding·본문을 그대로 서비스에 넘긴다")
    void saveTrack_passes_path_header_and_body_to_service() throws Exception {
        // when: gzip 헤더와 압축 본문으로 세계 트랙 저장 호출
        mockMvc.perform(put(PATH, 3, "2026-10-16", "S034")
                        .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        // then: 200·본문 없음 + 경로 값·헤더·본문을 그대로 담은 Command 로 서비스에 위임
        ArgumentCaptor<WorldTrackSaveCommand> captor = ArgumentCaptor.forClass(WorldTrackSaveCommand.class);
        then(worldTrackService).should().saveTrack(captor.capture());
        WorldTrackSaveCommand command = captor.getValue();
        assertThat(command.poolNumber()).isEqualTo(3);
        assertThat(command.date()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(command.sectorCode()).isEqualTo("S034");
        assertThat(command.contentEncoding()).isEqualTo("gzip");
        assertThat(command.body().readAllBytes()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("풀 번호가 양수가 아니거나, 날짜·구역 코드 형식이 틀리면 서비스를 부르지 않고 400 INVALID_REQUEST")
    void saveTrack_rejects_invalid_path_values() throws Exception {
        // when & then: 경로 값마다 400 INVALID_REQUEST
        for (Object[] path : new Object[][]{{0, "2026-10-16", "S034"}, {3, "2026-13-01", "S034"}, {3, "2026-10-16", "s034"}, {3, "2026-10-16", "S0345"}}) {
            mockMvc.perform(put(PATH, path).header(HttpHeaders.CONTENT_ENCODING, "gzip").content(BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
        }
        then(worldTrackService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("동시 저장 상한에 걸리면 429 WORLD_TRACK_SAVE_BUSY")
    void saveTrack_returns_429_when_busy() throws Exception {
        // given: 서비스가 동시 저장 상한에 걸림
        willThrow(new BusinessException(ErrorCode.WORLD_TRACK_SAVE_BUSY)).given(worldTrackService).saveTrack(any());

        // when & then: 429 와 WORLD_TRACK_SAVE_BUSY 코드
        mockMvc.perform(put(PATH, 3, "2026-10-16", "S034").header(HttpHeaders.CONTENT_ENCODING, "gzip").content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_TRACK_SAVE_BUSY.name()));
    }

    @Test
    @DisplayName("서비스가 넣은 응답 헤더를 오류 응답에 그대로 싣는다 (415 + Accept-Encoding: gzip)")
    void saveTrack_returns_415_with_accept_encoding() throws Exception {
        // given: 서비스가 Accept-Encoding 헤더를 담은 415 예외를 던짐
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
        willThrow(new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, headers)).given(worldTrackService).saveTrack(any());

        // when & then: 415 와 Accept-Encoding: gzip 헤더가 응답에 실림
        mockMvc.perform(put(PATH, 3, "2026-10-16", "S034").content(BODY))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT_ENCODING, "gzip"))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name()));
    }

    @Test
    @DisplayName("본문이 상한을 넘으면 413 WORLD_TRACK_TOO_LARGE")
    void saveTrack_returns_413_when_too_large() throws Exception {
        // given: 서비스가 용량 초과 예외를 던짐
        willThrow(new BusinessException(ErrorCode.WORLD_TRACK_TOO_LARGE)).given(worldTrackService).saveTrack(any());

        // when & then: 413 과 WORLD_TRACK_TOO_LARGE 코드
        mockMvc.perform(put(PATH, 3, "2026-10-16", "S034").header(HttpHeaders.CONTENT_ENCODING, "gzip").content(BODY))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_TRACK_TOO_LARGE.name()));
    }
}
