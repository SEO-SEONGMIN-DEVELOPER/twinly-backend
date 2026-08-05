package com.nidus.twinly.common.fcm;

import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.device.repository.DeviceRepository;
import com.nidus.twinly.notification.domain.NotificationChannel;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.NotificationSetting;
import com.nidus.twinly.notification.repository.NotificationSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PushRecipientResolverUnitTest {

    @Mock
    DeviceRepository deviceRepository;

    @Mock
    NotificationSettingRepository notificationSettingRepository;

    @InjectMocks
    PushRecipientResolver pushRecipientResolver;

    @Test
    @DisplayName("설정 행이 없으면 켜진 것으로 보고 기기를 모두 돌려준다")
    void resolve_treats_missing_setting_as_enabled() {
        // given: 설정을 한 번도 건드리지 않은 유저 (기본값은 켜짐)
        given(notificationSettingRepository.findAllByUserIdInAndChannelAndType(
                List.of(1L, 2L), NotificationChannel.PUSH, NotificationType.CHAT)).willReturn(List.of());
        given(deviceRepository.findAllByUserIdInAndPushTokenIsNotNull(List.of(1L, 2L)))
                .willReturn(List.of(device(1L), device(2L)));

        // when: 수신자 해석
        List<Device> devices = pushRecipientResolver.resolve(List.of(1L, 2L), NotificationType.CHAT);

        // then: 둘 다 대상이 된다
        assertThat(devices).hasSize(2);
    }

    @Test
    @DisplayName("푸시를 끈 유저는 기기 조회 대상에서 빠진다")
    void resolve_excludes_disabled_user() {
        // given: 2번 유저만 CHAT 푸시를 껐음
        given(notificationSettingRepository.findAllByUserIdInAndChannelAndType(
                List.of(1L, 2L), NotificationChannel.PUSH, NotificationType.CHAT))
                .willReturn(List.of(setting(2L, false)));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.captor();
        given(deviceRepository.findAllByUserIdInAndPushTokenIsNotNull(captor.capture()))
                .willReturn(List.of(device(1L)));

        // when: 수신자 해석
        pushRecipientResolver.resolve(List.of(1L, 2L), NotificationType.CHAT);

        // then: 애초에 조회 자체를 1번 유저로만 한다
        assertThat(captor.getValue()).containsExactly(1L);
    }

    @Test
    @DisplayName("설정이 켜져 있으면 그대로 대상에 남는다")
    void resolve_keeps_explicitly_enabled_user() {
        // given: 명시적으로 켜둔 유저
        given(notificationSettingRepository.findAllByUserIdInAndChannelAndType(
                List.of(1L), NotificationChannel.PUSH, NotificationType.CHAT))
                .willReturn(List.of(setting(1L, true)));
        given(deviceRepository.findAllByUserIdInAndPushTokenIsNotNull(List.of(1L)))
                .willReturn(List.of(device(1L)));

        // when: 수신자 해석
        List<Device> devices = pushRecipientResolver.resolve(List.of(1L), NotificationType.CHAT);

        // then: 대상에 남는다
        assertThat(devices).hasSize(1);
    }

    @Test
    @DisplayName("전원이 푸시를 껐으면 기기를 조회하지 않는다")
    void resolve_skips_device_query_when_all_disabled() {
        // given: 두 명 모두 껐음
        given(notificationSettingRepository.findAllByUserIdInAndChannelAndType(
                List.of(1L, 2L), NotificationChannel.PUSH, NotificationType.CHAT))
                .willReturn(List.of(setting(1L, false), setting(2L, false)));

        // when: 수신자 해석
        List<Device> devices = pushRecipientResolver.resolve(List.of(1L, 2L), NotificationType.CHAT);

        // then: 쓸데없는 조회를 하지 않는다
        assertThat(devices).isEmpty();
        then(deviceRepository).should(never()).findAllByUserIdInAndPushTokenIsNotNull(anyList());
    }

    @Test
    @DisplayName("수신자가 없으면 아무것도 조회하지 않는다")
    void resolve_skips_everything_when_no_recipients() {
        // when: 빈 목록으로 해석
        List<Device> devices = pushRecipientResolver.resolve(List.of(), NotificationType.CHAT);

        // then: 설정 조회조차 하지 않는다
        assertThat(devices).isEmpty();
        then(notificationSettingRepository).should(never())
                .findAllByUserIdInAndChannelAndType(anyList(), any(), any());
    }

    private Device device(Long userId) {
        return Device.create(userId, UUID.randomUUID(), DevicePlatform.IOS, "token-" + userId);
    }

    private NotificationSetting setting(Long userId, boolean enabled) {
        return NotificationSetting.create(userId, NotificationChannel.PUSH, NotificationType.CHAT, enabled);
    }
}
