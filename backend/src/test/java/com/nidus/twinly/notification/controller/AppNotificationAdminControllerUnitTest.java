package com.nidus.twinly.notification.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.notification.dto.command.AppNotificationNoticePublishCommand;
import com.nidus.twinly.notification.dto.result.AppNotificationNoticePublishResult;
import com.nidus.twinly.notification.service.AppNotificationNoticeService;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppNotificationAdminController.class)
@Import(SecurityConfig.class)
class AppNotificationAdminControllerUnitTest {

    private static final String PATH = "/admin/app-notifications/notices";
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AppNotificationNoticeService appNotificationNoticeService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("공지 발행 성공 시 200과 발행 수를 반환하고, 문자열 userIds 를 Long 으로 바꿔 서비스를 호출한다")
    void publishNotice_withUserIds_success() throws Exception {
        // given: 서비스가 2건 발행했다고 응답
        given(appNotificationNoticeService.publish(any())).willReturn(new AppNotificationNoticePublishResult(2));

        // when: 대상 유저 2명을 지정해 발행
        var result = mockMvc.perform(post(PATH)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "처리방침이 바뀌어요", "body": "본문\\n\\n전문: https://trytwinly.com", "userIds": ["11", "12"]}
                        """));

        // then: 200 + 발행 수, 커맨드에 제목·본문(줄바꿈 포함)·대상이 그대로 담김
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(2));
        ArgumentCaptor<AppNotificationNoticePublishCommand> captor = ArgumentCaptor.forClass(AppNotificationNoticePublishCommand.class);
        then(appNotificationNoticeService).should().publish(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new AppNotificationNoticePublishCommand(
                "처리방침이 바뀌어요", "본문\n\n전문: https://trytwinly.com", List.of(11L, 12L)));
    }

    @Test
    @DisplayName("userIds 를 생략하면 대상 없이(전체 발행) 서비스를 호출한다")
    void publishNotice_withoutUserIds_publishesToAll() throws Exception {
        // given: 서비스가 100건 발행했다고 응답
        given(appNotificationNoticeService.publish(any())).willReturn(new AppNotificationNoticePublishResult(100));

        // when: userIds 없이 발행
        var result = mockMvc.perform(post(PATH)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "제목", "body": "본문"}
                        """));

        // then: 200 반환 + 커맨드의 userIds 가 null 이라 서비스가 전체 발행으로 처리할 수 있음
        result.andExpect(status().isOk());
        ArgumentCaptor<AppNotificationNoticePublishCommand> captor = ArgumentCaptor.forClass(AppNotificationNoticePublishCommand.class);
        then(appNotificationNoticeService).should().publish(captor.capture());
        assertThat(captor.getValue().userIds()).isNull();
    }

    @Test
    @DisplayName("제목이 비어 있으면 400을 반환하고 서비스를 호출하지 않는다")
    void publishNotice_blankTitle_badRequest() throws Exception {
        // when: 공백뿐인 제목으로 발행
        var result = mockMvc.perform(post(PATH)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": " ", "body": "본문"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(appNotificationNoticeService).should(never()).publish(any());
    }

    @Test
    @DisplayName("본문이 2000자를 넘으면 400을 반환하고 서비스를 호출하지 않는다")
    void publishNotice_tooLongBody_badRequest() throws Exception {
        // when: 2001자 본문으로 발행
        var result = mockMvc.perform(post(PATH)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"제목\", \"body\": \"" + "가".repeat(2001) + "\"}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(appNotificationNoticeService).should(never()).publish(any());
    }

    @Test
    @DisplayName("userIds 가 빈 배열이면 전체 발행으로 오인하지 않도록 400을 반환한다")
    void publishNotice_emptyUserIds_badRequest() throws Exception {
        // when: 빈 userIds 배열로 발행
        var result = mockMvc.perform(post(PATH)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "제목", "body": "본문", "userIds": []}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(appNotificationNoticeService).should(never()).publish(any());
    }

    @Test
    @DisplayName("userIds 에 숫자가 아닌 값이 있으면 400을 반환하고 서비스를 호출하지 않는다")
    void publishNotice_nonNumericUserId_badRequest() throws Exception {
        // when: 숫자가 아닌 userId 를 섞어 발행
        var result = mockMvc.perform(post(PATH)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "제목", "body": "본문", "userIds": ["11", "abc"]}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(appNotificationNoticeService).should(never()).publish(any());
    }

    @Test
    @DisplayName("관리자 토큰이 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void publishNotice_withoutToken_unauthorized() throws Exception {
        // when: 관리자 토큰 헤더 없이 발행
        var result = mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "제목", "body": "본문"}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(appNotificationNoticeService).should(never()).publish(any());
    }

    @Test
    @DisplayName("관리자 토큰이 틀리면 401을 반환하고 서비스를 호출하지 않는다")
    void publishNotice_withWrongToken_unauthorized() throws Exception {
        // when: 틀린 관리자 토큰으로 발행
        var result = mockMvc.perform(post(PATH)
                .header(ADMIN_TOKEN_HEADER, "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "제목", "body": "본문"}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(appNotificationNoticeService).should(never()).publish(any());
    }
}
