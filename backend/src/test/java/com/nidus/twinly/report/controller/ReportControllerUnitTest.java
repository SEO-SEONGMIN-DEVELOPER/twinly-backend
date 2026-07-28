package com.nidus.twinly.report.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.dto.command.ReportAiUtteranceCommand;
import com.nidus.twinly.report.dto.command.ReportUserCommand;
import com.nidus.twinly.report.dto.result.ReportUserResult;
import com.nidus.twinly.report.service.ReportService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
class ReportControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReportService reportService;

    // ReportController가 직접 쓰진 않지만, WebMvcConfig가 두 resolver를 모두 주입받고
    // 각 resolver가 이 서비스에 의존하므로 슬라이스 기동에 필수.
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
    @DisplayName("유저 신고 성공 시 200과 autoBlock을 반환하고 인증 유저 id·커맨드로 서비스를 호출한다")
    void reportUser_success() throws Exception {
        // given: 서비스가 자동 차단됨(autoBlock=true) 결과를 반환
        given(reportService.reportUser(anyLong(), any())).willReturn(new ReportUserResult(true));

        // when: 신고자 인증 상태로 유저 신고 API 호출
        var result = mockMvc.perform(post("/api/v1/reports/users")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetUserId":"42","reason":"HARASSMENT","detail":"지속적으로 괴롭힙니다"}
                        """));

        // then: 200 + autoBlock JSON 반환 + 인증 유저 id·커맨드로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.autoBlock").value(true));
        then(reportService).should()
                .reportUser(1L, new ReportUserCommand(42L, ReportReason.HARASSMENT, "지속적으로 괴롭힙니다"));
    }

    @Test
    @DisplayName("유저 신고 시 reason이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void reportUser_without_reason_returns_400() throws Exception {
        // when: reason을 뺀 본문으로 유저 신고 API 호출
        var result = mockMvc.perform(post("/api/v1/reports/users")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetUserId":"42","detail":"지속적으로 괴롭힙니다"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(reportService).should(never()).reportUser(anyLong(), any());
    }

    @Test
    @DisplayName("유저 신고 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void reportUser_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 유저 신고 API 호출
        var result = mockMvc.perform(post("/api/v1/reports/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetUserId":"42","reason":"SPAM","detail":"광고 도배"}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(reportService).should(never()).reportUser(anyLong(), any());
    }

    @Test
    @DisplayName("유저 신고 시 본문의 targetUserId가 숫자 문자열이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void reportUser_with_non_numeric_targetUserId_returns_400() throws Exception {
        // when: targetUserId를 숫자가 아닌 값으로 유저 신고 API 호출
        var result = mockMvc.perform(post("/api/v1/reports/users")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetUserId":"abc","reason":"SPAM","detail":"광고 도배"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(reportService).should(never()).reportUser(anyLong(), any());
    }

    @Test
    @DisplayName("AI 발화 신고 성공 시 200을 반환하고 인증 유저 id와 커맨드로 서비스를 호출한다")
    void aiUtterance_success() throws Exception {
        // when: 신고자 인증 상태로 AI 발화 신고 API 호출 (id는 문자열로 전달)
        var result = mockMvc.perform(post("/api/v1/reports/ai-utterances")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetUserId":"42","sceneId":"77","utteranceText":"부적절한 발언","reason":"HARASSMENT"}
                        """));

        // then: 200 반환 + 인증 유저 id와 커맨드로 서비스에 위임 (문자열 id가 Long으로 매핑됨)
        result.andExpect(status().isOk());
        then(reportService).should()
                .reportAiUtterance(1L, new ReportAiUtteranceCommand(42L, 77L, "부적절한 발언", "HARASSMENT"));
    }

    @Test
    @DisplayName("AI 발화 신고 시 utteranceText가 공백이면 400을 반환하고 서비스를 호출하지 않는다")
    void aiUtterance_with_blank_utteranceText_returns_400() throws Exception {
        // when: utteranceText를 공백으로 채워 AI 발화 신고 API 호출
        var result = mockMvc.perform(post("/api/v1/reports/ai-utterances")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetUserId":"42","sceneId":"77","utteranceText":"   ","reason":"HARASSMENT"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(reportService).should(never()).reportAiUtterance(anyLong(), any());
    }

    @Test
    @DisplayName("AI 발화 신고 시 sceneId가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void aiUtterance_without_sceneId_returns_400() throws Exception {
        // when: sceneId를 뺀 본문으로 AI 발화 신고 API 호출
        var result = mockMvc.perform(post("/api/v1/reports/ai-utterances")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetUserId":"42","utteranceText":"부적절한 발언","reason":"HARASSMENT"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(reportService).should(never()).reportAiUtterance(anyLong(), any());
    }

    @Test
    @DisplayName("AI 발화 신고 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void aiUtterance_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 AI 발화 신고 API 호출
        var result = mockMvc.perform(post("/api/v1/reports/ai-utterances")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetUserId":"42","sceneId":"77","utteranceText":"부적절한 발언","reason":"HARASSMENT"}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(reportService).should(never()).reportAiUtterance(anyLong(), any());
    }
}
