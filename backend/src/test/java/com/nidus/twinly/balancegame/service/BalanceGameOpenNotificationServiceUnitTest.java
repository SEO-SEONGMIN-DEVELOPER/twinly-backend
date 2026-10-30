package com.nidus.twinly.balancegame.service;

import com.nidus.twinly.season.domain.SeasonScope;
import java.time.LocalDate;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import org.junit.jupiter.api.BeforeEach;
import com.nidus.twinly.common.fcm.BalanceGameOpenedPushContent;
import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.fcm.PushType;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class BalanceGameOpenNotificationServiceUnitTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 12, 0);

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    SeasonRepository seasonRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    PushRecipientResolver pushRecipientResolver;

    @Mock
    PushMessageBuilder pushMessageBuilder;

    @Mock
    FcmSender fcmSender;

    @InjectMocks
    BalanceGameOpenNotificationService balanceGameOpenNotificationService;

    @BeforeEach
    void setUpSeasonStartDate() {
        lenient().when(currentSeasonReader.startDateOf(any())).thenReturn(LocalDate.EPOCH);
        lenient().when(currentSeasonReader.scopeOf(any())).thenReturn(SeasonScope.UNBOUNDED);
    }

    @Test
    @DisplayName("지금 사람 목록에 트윈이 있는 유저의, 이벤트 알림을 켠 기기로 출제 알림을 보낸다")
    void sends_to_users_with_visible_partner() {
        // given: 진행 중인 시즌, 트윈이 보이는 유저 10·20, 그중 기기가 있는 건 10
        givenActiveSeason();
        given(relationshipRepository.findUserIdsWithVisiblePartner(null, LocalDate.EPOCH, NOW)).willReturn(List.of(10L, 20L));
        Device device = Device.create(10L, UUID.randomUUID(), DevicePlatform.IOS, "token-10");
        given(pushRecipientResolver.resolve(List.of(10L, 20L), NotificationType.EVENT)).willReturn(List.of(device));
        PushMessage message = new PushMessage(10L, PushType.BALANCE_GAME_OPENED, "token-10", null);
        given(pushMessageBuilder.balanceGameOpened(any(), any())).willReturn(List.of(message));

        // when
        balanceGameOpenNotificationService.notifyOpenedRound(NOW);

        // then: 그 기기로 출제 문구를 담아 한 번 보낸다
        ArgumentCaptor<BalanceGameOpenedPushContent> content = ArgumentCaptor.forClass(BalanceGameOpenedPushContent.class);
        then(pushMessageBuilder).should().balanceGameOpened(eq(List.of(device)), content.capture());
        assertThat(content.getValue().title()).isEqualTo("새 밸런스 게임이 열렸어요");
        assertThat(content.getValue().body()).isEqualTo("트윈들과 같은 답을 골라 친밀도를 올려 보세요.");
        then(fcmSender).should().send(List.of(message));
    }

    @Test
    @DisplayName("보낼 메시지가 500개를 넘으면 500개씩 나눠 보낸다")
    void sends_in_batches_of_500() {
        // given: 메시지 1001개
        givenActiveSeason();
        given(relationshipRepository.findUserIdsWithVisiblePartner(null, LocalDate.EPOCH, NOW)).willReturn(List.of(10L));
        given(pushRecipientResolver.resolve(anyList(), any())).willReturn(List.of());
        List<PushMessage> messages = LongStream.rangeClosed(1, 1001)
                .mapToObj(id -> new PushMessage(id, PushType.BALANCE_GAME_OPENED, "token-" + id, null))
                .toList();
        given(pushMessageBuilder.balanceGameOpened(any(), any())).willReturn(messages);

        // when
        balanceGameOpenNotificationService.notifyOpenedRound(NOW);

        // then: 500·500·1
        then(fcmSender).should(times(3)).send(anyList());
        then(fcmSender).should().send(messages.subList(1000, 1001));
    }

    @Test
    @DisplayName("보낼 기기가 없으면 FCM 을 호출하지 않는다")
    void skips_send_without_devices() {
        // given: 트윈이 보이는 유저가 없다
        givenActiveSeason();
        given(relationshipRepository.findUserIdsWithVisiblePartner(null, LocalDate.EPOCH, NOW)).willReturn(List.of());
        given(pushRecipientResolver.resolve(List.of(), NotificationType.EVENT)).willReturn(List.of());
        given(pushMessageBuilder.balanceGameOpened(any(), any())).willReturn(List.of());

        // when
        balanceGameOpenNotificationService.notifyOpenedRound(NOW);

        // then
        then(fcmSender).should(never()).send(anyList());
    }

    @Test
    @DisplayName("진행 중인 시즌이 없으면 게임도 없으므로 대상 조회·발송을 하지 않는다")
    void skips_without_active_season() {
        // given
        given(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()).willReturn(Optional.empty());

        // when
        balanceGameOpenNotificationService.notifyOpenedRound(NOW);

        // then
        then(relationshipRepository).should(never()).findUserIdsWithVisiblePartner(any(), any(), any());
        then(fcmSender).should(never()).send(anyList());
    }

    private void givenActiveSeason() {
        given(seasonRepository.findFirstByIsActiveTrueOrderByIdDesc())
                .willReturn(Optional.of(BeanUtils.instantiateClass(Season.class)));
    }
}
