package com.nidus.twinly.push.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.push.dto.command.PushTokenRegisterCommand;
import com.nidus.twinly.push.service.PushService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.nidus.twinly.common.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PushController.class)
@Import(SecurityConfig.class)
class PushControllerUnitTest {

    private static final UUID DEVICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PushService pushService;

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
    @DisplayName("푸시 토큰 등록 성공 시 200을 반환하고 인증 유저 id와 요청 본문으로 만든 커맨드로 서비스를 호출한다")
    void register_success() throws Exception {
        // given: 정상적인 등록 요청 본문
        String body = """
                {
                  "deviceId": "11111111-1111-1111-1111-111111111111",
                  "platform": "ios",
                  "fcmToken": "fcm-token-abc"
                }
                """;

        // when: 인증 상태로 푸시 토큰 등록 API 호출
        var result = mockMvc.perform(post("/api/v1/push/tokens")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then: 200 반환 + 인증 유저 id·요청 본문 매핑 커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(pushService).should().register(
                1L,
                new PushTokenRegisterCommand(DEVICE_ID, DevicePlatform.IOS, "fcm-token-abc"));
    }

    @Test
    @DisplayName("푸시 토큰 등록 시 deviceId가 없으면 400 INVALID_REQUEST를 반환하고 서비스를 호출하지 않는다")
    void register_without_deviceId_returns_400() throws Exception {
        // given: deviceId가 누락된 등록 요청 본문
        String body = """
                {
                  "platform": "ios",
                  "fcmToken": "fcm-token-abc"
                }
                """;

        // when: 인증 상태로 푸시 토큰 등록 API 호출
        var result = mockMvc.perform(post("/api/v1/push/tokens")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then: 400 + INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(pushService).should(never()).register(anyLong(), any());
    }

    @Test
    @DisplayName("푸시 토큰 등록 시 deviceId가 UUID 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void register_with_malformed_deviceId_returns_400() throws Exception {
        // given: deviceId가 UUID 형식이 아닌 등록 요청 본문
        String body = """
                {
                  "deviceId": "not-a-uuid",
                  "platform": "ios",
                  "fcmToken": "fcm-token-abc"
                }
                """;

        // when: 인증 상태로 푸시 토큰 등록 API 호출
        var result = mockMvc.perform(post("/api/v1/push/tokens")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then: 400 반환 + 서비스는 호출되지 않음 (본문 역직렬화 단계에서 차단)
        result.andExpect(status().isBadRequest());
        then(pushService).should(never()).register(anyLong(), any());
    }

    @Test
    @DisplayName("푸시 토큰 등록 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void register_without_auth_returns_401() throws Exception {
        // given: 본문 자체는 정상인 등록 요청
        String body = """
                {
                  "deviceId": "11111111-1111-1111-1111-111111111111",
                  "platform": "ios",
                  "fcmToken": "fcm-token-abc"
                }
                """;

        // when: 인증 헤더 없이 푸시 토큰 등록 API 호출
        var result = mockMvc.perform(post("/api/v1/push/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then: 401 + UNAUTHORIZED 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        then(pushService).should(never()).register(anyLong(), any());
    }

    @Test
    @DisplayName("푸시 토큰 해제 성공 시 200을 반환하고 인증 유저 id와 경로의 deviceId로 서비스를 호출한다")
    void revoke_success() throws Exception {
        // when: 인증 상태로 푸시 토큰 해제 API 호출
        var result = mockMvc.perform(delete("/api/v1/push/tokens/{deviceId}", DEVICE_ID.toString())
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 인증 유저 id·경로의 deviceId로 서비스에 위임
        result.andExpect(status().isOk());
        then(pushService).should().revoke(1L, DEVICE_ID);
    }

    @Test
    @DisplayName("푸시 토큰 해제 시 deviceId가 UUID 형식이 아니면 400 INVALID_REQUEST를 반환하고 서비스를 호출하지 않는다")
    void revoke_with_invalid_deviceId_returns_400() throws Exception {
        // when: UUID 형식이 아닌 deviceId로 푸시 토큰 해제 API 호출
        var result = mockMvc.perform(delete("/api/v1/push/tokens/{deviceId}", "not-a-uuid")
                .header("Authorization", "Bearer access-token"));

        // then: 400 + INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(pushService).should(never()).revoke(anyLong(), any());
    }

    @Test
    @DisplayName("푸시 토큰 해제 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void revoke_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 푸시 토큰 해제 API 호출
        var result = mockMvc.perform(delete("/api/v1/push/tokens/{deviceId}", DEVICE_ID.toString()));

        // then: 401 + UNAUTHORIZED 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        then(pushService).should(never()).revoke(anyLong(), any());
    }

    @Test
    @DisplayName("푸시 토큰 등록 시 platform이 없고 fcmToken이 공백뿐이면 400을 반환하고 서비스를 호출하지 않는다")
    void register_with_blank_fields_returns_400() throws Exception {
        // given: platform이 누락되고 fcmToken이 빈 문자열인 요청 본문
        String body = """
                {
                  "deviceId": "11111111-1111-1111-1111-111111111111",
                  "fcmToken": ""
                }
                """;

        // when: 인증 상태로 푸시 토큰 등록 API 호출
        var result = mockMvc.perform(post("/api/v1/push/tokens")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then: 400 INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(pushService).should(never()).register(anyLong(), any());
    }

    @Test
    @DisplayName("푸시 토큰 등록 시 platform이 정의되지 않은 값이면 400 INVALID_REQUEST를 반환하고 서비스를 호출하지 않는다")
    void register_with_unknown_platform_returns_400() throws Exception {
        // given: DevicePlatform에 없는 값을 담은 요청 본문
        String body = """
                {
                  "deviceId": "11111111-1111-1111-1111-111111111111",
                  "platform": "windows",
                  "fcmToken": "fcm-token-abc"
                }
                """;

        // when: 인증 상태로 푸시 토큰 등록 API 호출
        var result = mockMvc.perform(post("/api/v1/push/tokens")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then: 400 + INVALID_REQUEST 반환 + 서비스는 호출되지 않음 (본문 역직렬화 단계에서 차단)
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(pushService).should(never()).register(anyLong(), any());
    }
}
