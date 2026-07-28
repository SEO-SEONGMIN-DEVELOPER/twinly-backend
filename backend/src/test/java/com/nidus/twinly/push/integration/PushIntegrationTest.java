package com.nidus.twinly.push.integration;

import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.device.repository.DeviceRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    DeviceRepository deviceRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("푸시 토큰 등록: 실제 유저·JWT 인증·MockMvc·DB까지 관통하여 devices 행이 생성된다")
    void register_new_device_end_to_end() throws Exception {
        // given: 실제 유저 저장 (devices.user_id FK 때문에 필수) + 신규 deviceId
        User me = saveUser();
        UUID deviceId = UUID.randomUUID();
        String body = """
                {
                  "deviceId": "%s",
                  "deviceModel": "iPhone 15 Pro",
                  "fcmToken": "fcm-token-abc"
                }
                """.formatted(deviceId);

        // when: 유저의 실제 액세스 토큰으로 푸시 토큰 등록 API 호출
        mockMvc.perform(post("/api/v1/push/tokens")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // then: 영속성 컨텍스트를 비우고 실제 DB에서 다시 읽어 행 생성과 값 저장을 확인
        entityManager.flush();
        entityManager.clear();
        Device saved = deviceRepository.findByDeviceId(deviceId).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(me.getId());
        assertThat(saved.getDeviceModel()).isEqualTo("iPhone 15 Pro");
        assertThat(saved.getPushToken()).isEqualTo("fcm-token-abc");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("푸시 토큰 재등록: 같은 deviceId로 다시 등록하면 행이 늘지 않고 모델·토큰만 갱신된다")
    void register_same_device_twice_updates_in_place() throws Exception {
        // given: 실제 유저와 이미 등록된 기기 1대
        User me = saveUser();
        UUID deviceId = UUID.randomUUID();
        deviceRepository.save(Device.create(me.getId(), deviceId, "iPhone 14", "old-token"));
        entityManager.flush();
        entityManager.clear();
        Long originalRowId = deviceRepository.findByDeviceId(deviceId).orElseThrow().getId();

        // when: 같은 deviceId로 모델·토큰을 바꿔 다시 등록
        String body = """
                {
                  "deviceId": "%s",
                  "deviceModel": "iPhone 15 Pro",
                  "fcmToken": "new-token"
                }
                """.formatted(deviceId);
        mockMvc.perform(post("/api/v1/push/tokens")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // then: 같은 행(id 동일)이 그대로 갱신되고 새 행은 생기지 않음
        entityManager.flush();
        entityManager.clear();
        Device reloaded = deviceRepository.findByDeviceId(deviceId).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(originalRowId);
        assertThat(reloaded.getDeviceModel()).isEqualTo("iPhone 15 Pro");
        assertThat(reloaded.getPushToken()).isEqualTo("new-token");
    }

    @Test
    @DisplayName("푸시 토큰 해제: 본인 기기의 push_token만 NULL이 되고 기기 행 자체는 남는다")
    void revoke_device_token_end_to_end() throws Exception {
        // given: 실제 유저와 토큰이 등록된 기기 1대
        User me = saveUser();
        UUID deviceId = UUID.randomUUID();
        deviceRepository.save(Device.create(me.getId(), deviceId, "iPhone 15 Pro", "fcm-token-abc"));
        entityManager.flush();
        entityManager.clear();

        // when: 유저의 실제 액세스 토큰으로 푸시 토큰 해제 API 호출
        mockMvc.perform(delete("/api/v1/push/tokens/{deviceId}", deviceId.toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: 실제 DB에서 push_token만 NULL로 비워지고 행은 유지
        entityManager.flush();
        entityManager.clear();
        Device reloaded = deviceRepository.findByDeviceId(deviceId).orElseThrow();
        assertThat(reloaded.getPushToken()).isNull();
        assertThat(reloaded.getUserId()).isEqualTo(me.getId());
        assertThat(reloaded.getDeviceModel()).isEqualTo("iPhone 15 Pro");
    }
}
