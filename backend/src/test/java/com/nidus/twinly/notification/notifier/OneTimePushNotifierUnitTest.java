package com.nidus.twinly.notification.notifier;

import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.OneTimePushContent;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.fcm.PushType;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.event.OneTimePushEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OneTimePushNotifierUnitTest {

    private static final Long ME = 10L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-30T03:00:00Z");

    @Mock
    PushRecipientResolver pushRecipientResolver;

    @Mock
    PushMessageBuilder pushMessageBuilder;

    @Mock
    FcmSender fcmSender;

    @InjectMocks
    OneTimePushNotifier oneTimePushNotifier;

    @Test
    @DisplayName("이벤트의 제목·본문을 그대로 담아 EVENT 설정을 켠 기기로 발송한다")
    void sends_to_event_enabled_devices() {
        // given: 이벤트 알림을 받는 기기 한 대
        List<Device> devices = List.of(Device.create(ME, UUID.randomUUID(), DevicePlatform.IOS, "token"));
        List<PushMessage> built = List.of(new PushMessage(ME, PushType.ONE_TIME_PUSH_ONLY, "token", null));
        given(pushRecipientResolver.resolve(List.of(ME), NotificationType.EVENT)).willReturn(devices);
        given(pushMessageBuilder.oneTimePushOnly(devices, new OneTimePushContent("제목", "본문", CREATED_AT)))
                .willReturn(built);

        // when: 일회성 푸시 이벤트 수신
        oneTimePushNotifier.onOneTimePush(new OneTimePushEvent(ME, "제목", "본문", CREATED_AT));

        // then: 친구·매칭 알림과 같은 수신 설정을 따른다
        then(fcmSender).should().send(built);
    }

    @Test
    @DisplayName("푸시를 껐거나 기기가 없으면 메시지를 만들지도 보내지도 않는다")
    void skips_when_no_devices() {
        // given: 받을 기기가 없음
        given(pushRecipientResolver.resolve(List.of(ME), NotificationType.EVENT)).willReturn(List.of());

        // when: 일회성 푸시 이벤트 수신
        oneTimePushNotifier.onOneTimePush(new OneTimePushEvent(ME, "제목", "본문", CREATED_AT));

        // then: 피드가 없는 알림이라 여기서 끝난다
        then(pushMessageBuilder).should(never()).oneTimePushOnly(anyList(), any());
        then(fcmSender).should(never()).send(anyList());
    }
}
