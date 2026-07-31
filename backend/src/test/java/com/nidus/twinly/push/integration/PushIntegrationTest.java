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
    @DisplayName("계정 전환: 같은 기기에서 다른 유저가 등록하면 기존 행의 소유자가 새 유저로 넘어간다")
    void register_same_device_by_other_user_overwrites_owner() throws Exception {
        // given: A가 쓰던 기기 행 (같은 deviceId·같은 FCM 토큰). 로그아웃 시 해제 API를 부르지 않은 상태를 가정한다
        User previous = saveUser();
        User next = saveUser();
        UUID deviceId = UUID.randomUUID();
        Long originalRowId = deviceRepository.save(Device.create(previous.getId(), deviceId, "iPhone 15 Pro", "shared-token")).getId();
        entityManager.flush();
        entityManager.clear();

        // when: 같은 기기에서 B가 로그인해 같은 deviceId·토큰으로 등록
        String body = """
                {
                  "deviceId": "%s",
                  "deviceModel": "iPhone 15 Pro",
                  "fcmToken": "shared-token"
                }
                """.formatted(deviceId);
        mockMvc.perform(post("/api/v1/push/tokens")
                        .header("Authorization", bearer(next.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // then: 행이 늘지 않고 같은 행의 소유자가 B로 바뀌어, A의 알림은 이 기기로 배달되지 않는다
        entityManager.flush();
        entityManager.clear();
        Device reloaded = deviceRepository.findByDeviceId(deviceId).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(originalRowId);
        assertThat(reloaded.getUserId()).isEqualTo(next.getId());
        assertThat(reloaded.getPushToken()).isEqualTo("shared-token");
        assertThat(deviceRepository.findAllByUserIdAndPushToken(previous.getId(), "shared-token")).isEmpty();
    }

    @Test
    @DisplayName("토큰 단일성: 같은 유저가 새 deviceId로 같은 토큰을 등록하면 옛 행의 토큰이 비워진다")
    void register_new_device_id_with_same_token_clears_old_row() throws Exception {
        // given: 내 기기 행 1개 (클라이언트가 deviceId를 새로 만들기 전 상태)
        User me = saveUser();
        UUID oldDeviceId = UUID.randomUUID();
        UUID newDeviceId = UUID.randomUUID();
        deviceRepository.save(Device.create(me.getId(), oldDeviceId, "iPhone 15 Pro", "same-token"));
        entityManager.flush();
        entityManager.clear();

        // when: FCM 토큰은 그대로인데 deviceId만 새로 만들어 등록
        String body = """
                {
                  "deviceId": "%s",
                  "deviceModel": "iPhone 15 Pro",
                  "fcmToken": "same-token"
                }
                """.formatted(newDeviceId);
        mockMvc.perform(post("/api/v1/push/tokens")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // then: 같은 토큰을 들고 있는 행이 하나뿐이라 같은 기기에 두 번 발송되지 않는다
        entityManager.flush();
        entityManager.clear();
        assertThat(deviceRepository.findByDeviceId(oldDeviceId).orElseThrow().getPushToken()).isNull();
        assertThat(deviceRepository.findAllByUserIdAndPushToken(me.getId(), "same-token"))
                .extracting(Device::getDeviceId)
                .containsExactly(newDeviceId);
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
