package com.nidus.twinly.notification.service;

import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.notification.sender.AppNotificationScheduleSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppNotificationScheduleServiceUnitTest {

    private static final Instant NOW = LocalDateTime.of(2026, 9, 30, 12, 0).toInstant(ZoneOffset.UTC);
    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);

    @Mock
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    @Mock
    AppNotificationScheduleSender appNotificationScheduleSender;

    @InjectMocks
    AppNotificationScheduleService appNotificationScheduleService;

    @Test
    @DisplayName("예약 시각이 지난 건을 하나씩 발송하고 실제로 나간 건수를 돌려준다")
    void sendDue_sends_each_due_schedule() {
        // given: 도래한 예약 두 건
        given(appNotificationScheduleRepository.findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of(schedule(1L), schedule(2L)));
        given(appNotificationScheduleSender.send(1L, NOW)).willReturn(true);
        given(appNotificationScheduleSender.send(2L, NOW)).willReturn(true);

        // when: 도래분 처리
        int sent = appNotificationScheduleService.sendDue(NOW);

        // then: 둘 다 발송된다
        assertThat(sent).isEqualTo(2);
    }

    @Test
    @DisplayName("한 건이 실패해도 멈추지 않고 다음 건을 계속 발송한다")
    void sendDue_continues_after_failure() {
        // given: 첫 건 발송이 예외로 끝나는 상황
        given(appNotificationScheduleRepository.findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of(schedule(1L), schedule(2L)));
        given(appNotificationScheduleSender.send(1L, NOW)).willThrow(new IllegalStateException("boom"));
        given(appNotificationScheduleSender.send(2L, NOW)).willReturn(true);

        // when: 도래분 처리
        int sent = appNotificationScheduleService.sendDue(NOW);

        // then: 한 사람의 실패가 같은 시각의 다른 사람 알림까지 막으면 안 된다
        assertThat(sent).isEqualTo(1);
        then(appNotificationScheduleSender).should().send(2L, NOW);
    }

    @Test
    @DisplayName("조회와 발송 사이에 지워졌거나 이미 나간 건은 건수에 세지 않는다")
    void sendDue_does_not_count_skipped() {
        // given: 발송기가 건너뛰었다고 알려 주는 예약
        given(appNotificationScheduleRepository.findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of(schedule(1L)));
        given(appNotificationScheduleSender.send(1L, NOW)).willReturn(false);

        // when: 도래분 처리
        int sent = appNotificationScheduleService.sendDue(NOW);

        // then: 나가지 않은 건은 세지 않는다
        assertThat(sent).isZero();
    }

    @Test
    @DisplayName("도래한 예약이 없으면 아무것도 발송하지 않는다")
    void sendDue_does_nothing_when_empty() {
        // given: 도래분 없음
        given(appNotificationScheduleRepository.findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of());

        // when: 도래분 처리
        int sent = appNotificationScheduleService.sendDue(NOW);

        // then: 발송 시도 자체가 없다
        assertThat(sent).isZero();
        then(appNotificationScheduleSender).should(never()).send(any(), any());
    }

    private AppNotificationSchedule schedule(Long id) {
        AppNotificationSchedule schedule = AppNotificationSchedule.create(
                10L, 20L, AppNotificationScheduleType.FRIEND, DATE, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }
}
