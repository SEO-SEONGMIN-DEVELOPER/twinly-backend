package com.nidus.twinly.balancegame.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.balancegame.domain.BalanceGameStatus;
import com.nidus.twinly.balancegame.dto.command.BalanceGameAnswerCommand;
import com.nidus.twinly.balancegame.dto.result.BalanceGameOptionResult;
import com.nidus.twinly.balancegame.dto.result.BalanceGameQuestionResult;
import com.nidus.twinly.balancegame.dto.result.BalanceGameResult;
import com.nidus.twinly.balancegame.service.BalanceGameService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceGameController.class)
@Import(SecurityConfig.class)
class BalanceGameControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BalanceGameService balanceGameService;

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
    @DisplayName("이번 시간 게임 조회 시 200과 함께 id 는 문자열로, 공통 질문과 이 트윈과의 상태를 내려준다")
    void current_success() throws Exception {
        // given: 내가 1번을 고르고 상대를 기다리는 회차
        given(balanceGameService.current(1L, 42L)).willReturn(new BalanceGameResult(
                100L,
                42L,
                new BalanceGameQuestionResult(7L, "평생 하나만 먹어야 한다면?", List.of(
                        new BalanceGameOptionResult(1L, "평생 라면"),
                        new BalanceGameOptionResult(2L, "평생 치킨"))),
                Instant.parse("2026-10-03T02:00:00Z"),
                BalanceGameStatus.WAITING_PARTNER,
                1L,
                null,
                false,
                0));

        // when: 인증 상태로 트윈의 이번 시간 게임 조회
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz", "42")
                .header("Authorization", "Bearer access-token"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.roundId").value("100"))
                .andExpect(jsonPath("$.partnerId").value("42"))
                .andExpect(jsonPath("$.question.id").value("7"))
                .andExpect(jsonPath("$.question.text").value("평생 하나만 먹어야 한다면?"))
                .andExpect(jsonPath("$.question.options[0].id").value("1"))
                .andExpect(jsonPath("$.question.options[0].label").value("평생 라면"))
                .andExpect(jsonPath("$.question.options[1].id").value("2"))
                .andExpect(jsonPath("$.endsAt").value("2026-10-03T02:00:00Z"))
                .andExpect(jsonPath("$.status").value("waitingPartner"))
                .andExpect(jsonPath("$.myOptionId").value("1"))
                .andExpect(jsonPath("$.partnerOptionId").isEmpty())
                .andExpect(jsonPath("$.partnerAnswered").value(false))
                .andExpect(jsonPath("$.intimacyBonus").value(0));
        then(balanceGameService).should().current(1L, 42L);
    }

    @Test
    @DisplayName("답변 시 문자열 optionId 를 숫자로 바꿔 위임하고 200을 반환한다")
    void answer_success() throws Exception {
        // when
        var result = mockMvc.perform(post("/api/v1/intimacy-quizzes/{roundId}/answers", "100")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionId\": \"2\"}"));

        // then
        result.andExpect(status().isOk());
        then(balanceGameService).should().answer(1L, 100L, new BalanceGameAnswerCommand(2L));
    }

    @Test
    @DisplayName("optionId 없이 답하면 400을 반환하고 서비스를 호출하지 않는다")
    void answer_without_option_returns_400() throws Exception {
        // when
        var result = mockMvc.perform(post("/api/v1/intimacy-quizzes/{roundId}/answers", "100")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then
        result.andExpect(status().isBadRequest());
        then(balanceGameService).should(never()).answer(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("회차 id 가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void answer_with_non_numeric_round_returns_400() throws Exception {
        // when
        var result = mockMvc.perform(post("/api/v1/intimacy-quizzes/{roundId}/answers", "abc")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionId\": \"2\"}"));

        // then
        result.andExpect(status().isBadRequest());
        then(balanceGameService).should(never()).answer(anyLong(), anyLong(), any());
    }
}
