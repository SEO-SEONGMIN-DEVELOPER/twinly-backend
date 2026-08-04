package com.nidus.twinly.push.service;

import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.device.repository.DeviceRepository;
import com.nidus.twinly.push.dto.command.PushTokenRegisterCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PushServiceUnitTest {

    private static final UUID DEVICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_DEVICE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    DeviceRepository deviceRepository;

    @InjectMocks
    PushService pushService;

    @Test
    @DisplayName("등록: 조회 없이 upsert 한 번으로 저장한다 (동시 등록이 유니크 위반으로 500이 되지 않게)")
    void register_delegates_to_upsert() {
        // given: 같은 토큰을 든 다른 행이 없음
        given(deviceRepository.findAllByUserIdAndPushToken(1L, "fcm-token-abc")).willReturn(List.of());

        // when: 푸시 토큰 등록
        pushService.register(1L, new PushTokenRegisterCommand(DEVICE_ID, DevicePlatform.IOS, "fcm-token-abc"));

        // then: 조회-후-저장(check-then-act)이 아니라 원자적 upsert로 위임된다
        then(deviceRepository).should().upsert(1L, DEVICE_ID, "IOS", "fcm-token-abc");
        then(deviceRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("등록: 내 다른 기기 행이 같은 토큰을 들고 있으면 그 행의 토큰을 비운다 (토큰 하나 = 행 하나)")
    void register_clears_same_token_on_my_other_rows() {
        // given: deviceId만 다른 내 옛 행이 같은 토큰을 들고 있음 (클라이언트가 deviceId를 새로 만든 경우)
        Device myStaleRow = Device.create(1L, OTHER_DEVICE_ID, DevicePlatform.IOS, "shared-token");
        given(deviceRepository.findAllByUserIdAndPushToken(1L, "shared-token")).willReturn(List.of(myStaleRow));

        // when: 새 deviceId로 같은 토큰을 등록
        pushService.register(1L, new PushTokenRegisterCommand(DEVICE_ID, DevicePlatform.IOS, "shared-token"));

        // then: 옛 행의 토큰이 비워져 같은 기기에 두 번 발송되지 않는다
        assertThat(myStaleRow.getPushToken()).isNull();
    }

    @Test
    @DisplayName("해제: 본인 소유 기기가 있으면 pushToken을 null로 비운다")
    void revoke_existing_device_clears_token() {
        // given: 유저 1이 소유한 기기가 토큰을 가지고 있음
        Device existing = Device.create(1L, DEVICE_ID, DevicePlatform.IOS, "fcm-token-abc");
        given(deviceRepository.findByUserIdAndDeviceId(1L, DEVICE_ID)).willReturn(Optional.of(existing));

        // when: 푸시 토큰 해제
        pushService.revoke(1L, DEVICE_ID);

        // then: 토큰만 비워지고 행 자체는 남는다
        assertThat(existing.getPushToken()).isNull();
        assertThat(existing.getUserId()).isEqualTo(1L);
        assertThat(existing.getDeviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    @DisplayName("해제: 본인 소유 기기를 못 찾으면 예외 없이 아무것도 하지 않는다 (멱등)")
    void revoke_missing_device_is_noop() {
        // given: 해당 유저 소유의 기기가 없음 (미등록이거나 다른 유저 소유)
        given(deviceRepository.findByUserIdAndDeviceId(1L, DEVICE_ID)).willReturn(Optional.empty());

        // when: 푸시 토큰 해제
        pushService.revoke(1L, DEVICE_ID);

        // then: 저장/삭제 등 어떤 쓰기도 일어나지 않음
        then(deviceRepository).should(never()).save(any());
        then(deviceRepository).should(never()).delete(any());
    }
}
