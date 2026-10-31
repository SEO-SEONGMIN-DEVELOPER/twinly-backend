package com.nidus.twinly.notification.sender;

import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.event.OneTimePushEvent;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.domain.SeasonScope;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppNotificationScheduleSenderUnitTest {

    private static final Long SCHEDULE_ID = 1L;
    private static final Long ME = 10L;
    private static final Long PARTNER = 20L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);
    private static final Instant NOW = LocalDateTime.of(2026, 9, 30, 12, 0).toInstant(ZoneOffset.UTC);
    private static final SeasonScope SEASON_SCOPE = new SeasonScope(1L, LocalDate.of(2026, 9, 1));

    @Mock
    AppNotificationScheduleRepository appNotificationScheduleRepository;

    @Mock
    AppNotificationFeedWriter appNotificationFeedWriter;

    @Mock
    UserRepository userRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    BlockRepository blockRepository;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    AppNotificationScheduleSender appNotificationScheduleSender;

    @BeforeEach
    void setUpReceiver() {
        lenient().when(userRepository.findById(ME)).thenReturn(Optional.of(user("나")));
    }

    @Test
    @DisplayName("친구 예약은 시뮬레이션 날짜를 출처로 friend 피드를 남기고 발송 완료로 표시한다")
    void send_friend_writes_feed_and_marks_sent() {
        // given: 도래한 친구 예약
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.FRIEND);
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        // when: 발송
        boolean sent = appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 즉시 발송과 같은 피드 경로를 타고, 다음 주기에 다시 잡히지 않도록 표시가 남는다
        assertThat(sent).isTrue();
        then(appNotificationFeedWriter).should().writeFriend(ME, PARTNER, DATE);
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("첫 만남 예약은 피드를 남기지 않고 상대 닉네임을 담은 푸시 이벤트만 발행한다")
    void send_first_meeting_publishes_push_event_only() {
        // given: 도래한 첫 만남 예약과 조회 가능한 상대
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.FIRST_MEETING);
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user("상대")));

        // when: 발송
        boolean sent = appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 알림 목록에는 남기지 않는 알림이므로 피드 작성기를 거치지 않는다
        assertThat(sent).isTrue();
        ArgumentCaptor<OneTimePushEvent> captor = ArgumentCaptor.forClass(OneTimePushEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(ME);
        assertThat(captor.getValue().title()).isEqualTo("상대님을 처음 만났어요.");
        assertThat(captor.getValue().body()).isEqualTo("평행세계에서 상대님과 첫 대화를 시작했어요.");
        assertThat(captor.getValue().createdAt()).isEqualTo(NOW);
        then(appNotificationFeedWriter).should(never()).writeFriend(any(), any(), any());
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("첫 만남 상대가 그 사이 탈퇴했으면 푸시를 보내지 않고 예약만 닫는다")
    void send_first_meeting_skips_push_for_withdrawn_partner() {
        // given: 예약 이후 탈퇴한 상대
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.FIRST_MEETING);
        User withdrawn = user("상대");
        ReflectionTestUtils.setField(withdrawn, "deletedAt", Instant.now());
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(withdrawn));

        // when: 발송
        appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 이름 없는 상대를 만났다고 알리지 않되, 매 주기 다시 시도하지 않도록 표시는 남긴다
        then(eventPublisher).should(never()).publishEvent(any());
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("시작 인연 공개 예약은 피드 없이 상대 닉네임을 담은 푸시 이벤트만 발행한다")
    void send_starter_reveal_publishes_push_event_only() {
        // given: 공개 시각이 지난 시작 인연
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.STARTER_REVEAL);
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user("상대")));
        givenLatestRelationship(Relationship.STARTER_VERSION);

        // when: 발송
        boolean sent = appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 첫 만남과 같은 푸시 전용 경로로 나간다
        assertThat(sent).isTrue();
        ArgumentCaptor<OneTimePushEvent> captor = ArgumentCaptor.forClass(OneTimePushEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(ME);
        assertThat(captor.getValue().title()).isEqualTo("상대님을 만날지도 몰라요.");
        assertThat(captor.getValue().body()).isEqualTo("만날 가능성이 높은 사람이 공개됐어요.");
        assertThat(captor.getValue().createdAt()).isEqualTo(NOW);
        then(appNotificationFeedWriter).should(never()).writeFriend(any(), any(), any());
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("시작 인연 상대가 그 사이 탈퇴했으면 푸시를 보내지 않고 예약만 닫는다")
    void send_starter_reveal_skips_withdrawn_partner() {
        // given: 공개 전에 탈퇴한 상대
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.STARTER_REVEAL);
        User withdrawn = user("상대");
        ReflectionTestUtils.setField(withdrawn, "deletedAt", Instant.now());
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(withdrawn));

        // when: 발송
        appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 인연탭에 없는 사람을 알리지 않는다
        then(eventPublisher).should(never()).publishEvent(any());
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("받는 사람이 그 사이 탈퇴 신청했으면 푸시를 보내지 않고 예약만 닫는다")
    void send_starter_reveal_skips_withdrawn_receiver() {
        // given: 공개 전에 탈퇴 신청한 수신자
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.STARTER_REVEAL);
        User withdrawn = user("나");
        ReflectionTestUtils.setField(withdrawn, "withdrawalRequestedAt", Instant.now());
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user("상대")));
        given(userRepository.findById(ME)).willReturn(Optional.of(withdrawn));

        // when: 발송
        appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 떠나는 사람에게 새 인연을 알리지 않는다
        then(eventPublisher).should(never()).publishEvent(any());
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("시즌이 바뀌어 시작 인연이 보이지 않으면 푸시를 보내지 않고 예약만 닫는다")
    void send_starter_reveal_skips_when_relationship_not_visible_in_season() {
        // given: 공개 전에 시즌이 바뀌어 현재 시즌 범위에서 조회되지 않는 시작 인연
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.STARTER_REVEAL);
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user("상대")));
        given(currentSeasonReader.scopeOf(any())).willReturn(SEASON_SCOPE);
        given(relationshipRepository.findLatestInSeasonByUserIdAndPartnerUserId(eq(ME), eq(PARTNER), any(), any(), any()))
                .willReturn(Optional.empty());

        // when: 발송
        appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 볼 수 없는 인연을 알리지 않는다
        then(eventPublisher).should(never()).publishEvent(any());
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("시뮬레이션에서 이미 만나 최신 관계가 시작 인연이 아니면 푸시를 보내지 않는다")
    void send_starter_reveal_skips_when_already_met() {
        // given: 공개 전에 시뮬레이션 관계 행이 먼저 보이기 시작한 상대
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.STARTER_REVEAL);
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user("상대")));
        givenLatestRelationship("v1");

        // when: 발송
        appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 이미 만난 상대를 만날지도 모른다고 알리지 않는다
        then(eventPublisher).should(never()).publishEvent(any());
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("상대가 나를 차단했으면 푸시를 보내지 않고 예약만 닫는다")
    void send_starter_reveal_skips_when_blocked_by_partner() {
        // given: 공개 전에 상대가 나를 차단함
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.STARTER_REVEAL);
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user("상대")));
        givenLatestRelationship(Relationship.STARTER_VERSION);
        given(blockRepository.existsByUserIdAndBlockedUserId(ME, PARTNER)).willReturn(false);
        given(blockRepository.existsByUserIdAndBlockedUserId(PARTNER, ME)).willReturn(true);

        // when: 발송
        appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 차단 관계의 상대 이름을 알리지 않는다
        then(eventPublisher).should(never()).publishEvent(any());
        assertThat(schedule.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("조회와 발송 사이에 예약이 지워졌으면 아무것도 보내지 않는다")
    void send_skips_when_schedule_is_gone() {
        // given: 같은 날짜 결과가 다시 저장되며 예약이 지워진 상황
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.empty());

        // when: 발송
        boolean sent = appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 바뀐 결과에 없는 일을 알리지 않는다
        assertThat(sent).isFalse();
        then(appNotificationFeedWriter).should(never()).writeFriend(any(), any(), any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 발송 완료로 표시된 예약은 다시 보내지 않는다")
    void send_skips_when_already_sent() {
        // given: 앞서 발송된 예약
        AppNotificationSchedule schedule = schedule(AppNotificationScheduleType.FRIEND);
        Instant firstSentAt = NOW.minusSeconds(60);
        schedule.markSent(firstSentAt);
        given(appNotificationScheduleRepository.findWithLockById(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        // when: 발송
        boolean sent = appNotificationScheduleSender.send(SCHEDULE_ID, NOW);

        // then: 같은 알림이 두 번 나가지 않고 처음 발송 시각도 덮어쓰지 않는다
        assertThat(sent).isFalse();
        then(appNotificationFeedWriter).should(never()).writeFriend(any(), any(), any());
        assertThat(schedule.getSentAt()).isEqualTo(firstSentAt);
    }

    private void givenLatestRelationship(String version) {
        given(currentSeasonReader.scopeOf(any())).willReturn(SEASON_SCOPE);
        given(relationshipRepository.findLatestInSeasonByUserIdAndPartnerUserId(eq(ME), eq(PARTNER), any(), any(), any()))
                .willReturn(Optional.of(Relationship.create(ME, DATE, version, PARTNER, 0, "model", DATE.atStartOfDay(), NOW)));
    }

    private AppNotificationSchedule schedule(AppNotificationScheduleType type) {
        AppNotificationSchedule schedule = AppNotificationSchedule.create(ME, PARTNER, type, DATE, NOW.minusSeconds(30));
        ReflectionTestUtils.setField(schedule, "id", SCHEDULE_ID);
        return schedule;
    }

    private User user(String nickname) {
        User user = User.create(
                nickname,
                "홍", "familyHash",
                "실명", "givenHash",
                Gender.MALE,
                "organization", "organizationHash",
                "aff", "affHash",
                "affNo", "affNoHash",
                "2000-01-01", "birthHash",
                "phone", "phoneHash",
                "email", "emailHash", null, null
        , null, null);
        ReflectionTestUtils.setField(user, "id", PARTNER);
        return user;
    }
}
