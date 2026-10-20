package com.nidus.twinly.balancegame.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.balancegame.domain.BalanceGameStatus;
import com.nidus.twinly.balancegame.dto.command.BalanceGameAnswerCommand;
import com.nidus.twinly.balancegame.dto.result.BalanceGameHistoryItemResult;
import com.nidus.twinly.balancegame.dto.result.BalanceGameHistoryResult;
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
    @DisplayName("기록 조회 시 cursor 없이 위임하고, id 들은 문자열로 질문·내 답·일치 여부·다음 cursor 를 내려준다")
    void history_success() throws Exception {
        // given: 서로 답한 회차 하나, 더 이전 회차가 있다
        given(balanceGameService.history(1L, 42L, null)).willReturn(new BalanceGameHistoryResult(
                List.of(new BalanceGameHistoryItemResult(
                        102L,
                        new BalanceGameQuestionResult(7L, "평생 하나만 먹어야 한다면?", List.of(
                                new BalanceGameOptionResult(1L, "평생 라면"),
                                new BalanceGameOptionResult(2L, "평생 치킨"))),
                        1L,
                        false)),
                102L));

        // when: 인증 상태로 cursor 없이 기록 조회
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz/history", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + id 는 문자열, 상대 답 필드 없음 + 인증 유저 id·경로 userId·null cursor 로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].roundId").value("102"))
                .andExpect(jsonPath("$.questions[0].question.id").value("7"))
                .andExpect(jsonPath("$.questions[0].question.text").value("평생 하나만 먹어야 한다면?"))
                .andExpect(jsonPath("$.questions[0].question.options[1].id").value("2"))
                .andExpect(jsonPath("$.questions[0].question.options[1].label").value("평생 치킨"))
                .andExpect(jsonPath("$.questions[0].myOptionId").value("1"))
                .andExpect(jsonPath("$.questions[0].matched").value(false))
                .andExpect(jsonPath("$.questions[0].partnerOptionId").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").value("102"));
        then(balanceGameService).should().history(1L, 42L, null);
    }

    @Test
    @DisplayName("기록 cursor 는 숫자로 바꿔 위임하고, 더 이전 회차가 없으면 빈 목록과 null cursor 를 내려준다")
    void history_with_cursor_success() throws Exception {
        // given: 더 이전 회차가 없는 결과
        given(balanceGameService.history(1L, 42L, 102L)).willReturn(new BalanceGameHistoryResult(List.of(), null));

        // when: cursor 를 붙여 기록 조회
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz/history", "42")
                .param("cursor", "102")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 빈 목록·null cursor + cursor 를 숫자로 바꿔 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.questions").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        then(balanceGameService).should().history(1L, 42L, 102L);
    }

    @Test
    @DisplayName("기록 cursor 가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void history_with_non_numeric_cursor_returns_400() throws Exception {
        // when: 숫자가 아닌 cursor 로 기록 조회
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz/history", "42")
                .param("cursor", "abc")
                .header("Authorization", "Bearer access-token"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(balanceGameService).should(never()).history(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("인증 헤더 없이 기록을 조회하면 401을 반환하고 서비스를 호출하지 않는다")
    void history_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 기록 조회
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz/history", "42"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(balanceGameService).should(never()).history(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("트윈에게 답하면 문자열 id 들을 숫자로 바꿔 위임하고, 그 트윈과의 결과를 내려준다")
    void answer_success() throws Exception {
        // given: 답한 뒤 상대를 기다리는 결과
        given(balanceGameService.answer(1L, 100L, new BalanceGameAnswerCommand(42L, 2L))).willReturn(new BalanceGameResult(
                100L,
                42L,
                new BalanceGameQuestionResult(7L, "평생 하나만 먹어야 한다면?", List.of(
                        new BalanceGameOptionResult(1L, "평생 라면"),
                        new BalanceGameOptionResult(2L, "평생 치킨"))),
                Instant.parse("2026-10-03T09:00:00Z"),
                BalanceGameStatus.WAITING_PARTNER,
                2L,
                null,
                false,
                0));

        // when
        var result = mockMvc.perform(post("/api/v1/intimacy-quizzes/{roundId}/answers", "100")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partnerId\": \"42\", \"optionId\": \"2\"}"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId").value("42"))
                .andExpect(jsonPath("$.status").value("waitingPartner"))
                .andExpect(jsonPath("$.myOptionId").value("2"));
        then(balanceGameService).should().answer(1L, 100L, new BalanceGameAnswerCommand(42L, 2L));
    }

    @Test
    @DisplayName("partnerId 없이 답하면 400을 반환하고 서비스를 호출하지 않는다")
    void answer_without_partner_returns_400() throws Exception {
        // when
        var result = mockMvc.perform(post("/api/v1/intimacy-quizzes/{roundId}/answers", "100")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionId\": \"2\"}"));

        // then
        result.andExpect(status().isBadRequest());
        then(balanceGameService).should(never()).answer(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("optionId 없이 답하면 400을 반환하고 서비스를 호출하지 않는다")
    void answer_without_option_returns_400() throws Exception {
        // when
        var result = mockMvc.perform(post("/api/v1/intimacy-quizzes/{roundId}/answers", "100")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partnerId\": \"42\"}"));

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
                .content("{\"partnerId\": \"42\", \"optionId\": \"2\"}"));

        // then
        result.andExpect(status().isBadRequest());
        then(balanceGameService).should(never()).answer(anyLong(), anyLong(), any());
    }
}
