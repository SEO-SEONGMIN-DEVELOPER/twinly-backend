package com.nidus.twinly.balancegame.notifier;

import com.nidus.twinly.balancegame.event.BalanceGameSummaryEvent;
import com.nidus.twinly.common.fcm.BalanceGameSummaryPushContent;
import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.fcm.PushType;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BalanceGamePushNotifierUnitTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-03T02:00:10Z");

    @Mock
    PushRecipientResolver pushRecipientResolver;

    @Mock
    PushMessageBuilder pushMessageBuilder;

    @Mock
    FcmSender fcmSender;

    @InjectMocks
    BalanceGamePushNotifier balanceGamePushNotifier;

    @Test
    @DisplayName("유저마다 자기 기기로, 자기와 같은 답을 고른 인원 수를 담아 한 번에 보낸다")
    void sends_each_user_own_matched_count() {
        // given: 10번은 3명, 20번은 1명과 일치했고 둘 다 기기가 있다
        Device first = Device.create(10L, UUID.randomUUID(), DevicePlatform.IOS, "token-10");
        Device second = Device.create(20L, UUID.randomUUID(), DevicePlatform.ANDROID, "token-20");
        given(pushRecipientResolver.resolve(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(10L, 20L))), any()))
                .willReturn(List.of(first, second));
        PushMessage firstMessage = new PushMessage(10L, PushType.INTIMACY_QUIZ, "token-10", null);
        PushMessage secondMessage = new PushMessage(20L, PushType.INTIMACY_QUIZ, "token-20", null);
        given(pushMessageBuilder.balanceGameSummary(List.of(first), new BalanceGameSummaryPushContent(
                100L, 3L, "밸런스 게임 결과가 나왔어요", "이번 질문에서 3명과 같은 답을 골랐어요! 친밀도가 올랐어요", CREATED_AT)))
                .willReturn(List.of(firstMessage));
        given(pushMessageBuilder.balanceGameSummary(List.of(second), new BalanceGameSummaryPushContent(
                100L, 1L, "밸런스 게임 결과가 나왔어요", "이번 질문에서 1명과 같은 답을 골랐어요! 친밀도가 올랐어요", CREATED_AT)))
                .willReturn(List.of(secondMessage));

        // when
        balanceGamePushNotifier.onBalanceGameSummary(new BalanceGameSummaryEvent(100L, Map.of(10L, 3L, 20L, 1L), CREATED_AT));

        // then: 이벤트 알림 설정을 따르고, 두 사람 메시지를 한 번에 보낸다
        then(pushRecipientResolver).should().resolve(anyList(), eq(NotificationType.EVENT));
        then(fcmSender).should().send(argThat(messages -> messages.size() == 2
                && messages.containsAll(List.of(firstMessage, secondMessage))));
    }

    @Test
    @DisplayName("푸시를 껐거나 기기가 없으면 메시지를 만들지도 보내지도 않는다")
    void skips_when_no_devices() {
        // given
        given(pushRecipientResolver.resolve(List.of(10L), NotificationType.EVENT)).willReturn(List.of());

        // when
        balanceGamePushNotifier.onBalanceGameSummary(new BalanceGameSummaryEvent(100L, Map.of(10L, 3L), CREATED_AT));

        // then
        then(pushMessageBuilder).should(never()).balanceGameSummary(anyList(), any());
        then(fcmSender).should(never()).send(anyList());
    }
}
