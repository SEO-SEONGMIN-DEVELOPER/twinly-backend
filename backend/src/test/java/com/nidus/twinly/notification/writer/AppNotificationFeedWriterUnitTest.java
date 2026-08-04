package com.nidus.twinly.notification.writer;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppNotificationFeedWriterUnitTest {

    private static final Long ME = 1L;
    private static final Long PARTNER = 2L;
    private static final Long ROOM_ID = 10L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    @Mock
    AppNotificationFeedRepository appNotificationFeedRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    AppNotificationFeedWriter appNotificationFeedWriter;

    @Test
    @DisplayName("match 피드는 양쪽 유저에게 각각 상대 실명을 제목에 담아 채팅방을 가리키도록 저장된다")
    void writeMatch_saves_chat_target_feed_for_both_users() {
        // given: 양쪽 유저 실명 조회 가능
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, "김", "나야")));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "박", "상대")));

        // when: 채팅방 생성 알림 기록
        appNotificationFeedWriter.writeMatch(ROOM_ID, ME, PARTNER);

        // then: 2건이 저장되고 각각 제목에 상대 실명 + CHAT 타깃(roomId)을 가짐
        List<AppNotificationFeed> feeds = savedFeeds();
        assertThat(feeds).hasSize(2);

        AppNotificationFeed mine = feedOf(feeds, ME);
        assertThat(mine.getType()).isEqualTo(AppNotificationFeedType.MATCH);
        assertThat(mine.getTargetKind()).isEqualTo(AppNotificationFeedTargetType.CHAT);
        assertThat(mine.getTargetChatRoomId()).isEqualTo(ROOM_ID);
        assertThat(mine.getTargetUserId()).isNull();
        assertThat(mine.getTitle()).isEqualTo("박상대님과 채팅방이 열렸어요.");
        assertThat(mine.getBody()).isEqualTo("채팅 탭에서 입장하기를 누르면, 상대가 동의한 뒤 대화가 시작돼요.");
        assertThat(mine.getReadAt()).isNull();
        assertThat(mine.getCreatedAt()).isNotNull();

        assertThat(feedOf(feeds, PARTNER).getTitle()).isEqualTo("김나야님과 채팅방이 열렸어요.");
    }

    @Test
    @DisplayName("chat_ready 피드는 양쪽 유저에게 각각 상대 실명을 제목에 담아 채팅방을 가리키도록 저장된다")
    void writeChatReady_saves_chat_target_feed_for_both_users() {
        // given: 양쪽 유저 실명 조회 가능
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, "김", "나야")));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "박", "상대")));

        // when: 양쪽 입장 동의 완료 알림 기록
        appNotificationFeedWriter.writeChatReady(ROOM_ID, ME, PARTNER);

        // then: 2건이 CHAT_READY 타입으로 저장됨
        List<AppNotificationFeed> feeds = savedFeeds();
        assertThat(feeds).hasSize(2);
        assertThat(feeds).allSatisfy(feed -> {
            assertThat(feed.getType()).isEqualTo(AppNotificationFeedType.CHAT_READY);
            assertThat(feed.getTargetKind()).isEqualTo(AppNotificationFeedTargetType.CHAT);
            assertThat(feed.getTargetChatRoomId()).isEqualTo(ROOM_ID);
        });
        assertThat(feedOf(feeds, ME).getTitle()).isEqualTo("박상대님과 채팅할 수 있어요.");
        assertThat(feedOf(feeds, ME).getBody()).isEqualTo("두 사람 모두 입장하기를 눌렀어요. 이제 대화를 시작할 수 있어요.");
        assertThat(feedOf(feeds, PARTNER).getTitle()).isEqualTo("김나야님과 채팅할 수 있어요.");
    }

    @Test
    @DisplayName("friend 피드는 같은 날짜로 만든 기존 피드를 지운 뒤 상대 프로필을 가리키도록 1건만 저장된다")
    void writeFriend_replaces_same_date_feed_and_saves_profile_target() {
        // given: 상대 실명 조회 가능
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "박", "상대")));

        // when: 친구 승격 알림 기록
        appNotificationFeedWriter.writeFriend(ME, PARTNER, DATE);

        // then: 같은 (유저, 타입, 상대, 날짜) 피드를 먼저 삭제
        then(appNotificationFeedRepository).should()
                .deleteAllByUserIdAndTypeAndTargetUserIdAndSimulationDate(ME, AppNotificationFeedType.FRIEND, PARTNER, DATE);

        // then: 나에게만 1건, PROFILE 타깃(상대 userId) + simulationDate가 채워져 저장됨
        ArgumentCaptor<AppNotificationFeed> captor = ArgumentCaptor.forClass(AppNotificationFeed.class);
        then(appNotificationFeedRepository).should().save(captor.capture());

        AppNotificationFeed feed = captor.getValue();
        assertThat(feed.getUserId()).isEqualTo(ME);
        assertThat(feed.getType()).isEqualTo(AppNotificationFeedType.FRIEND);
        assertThat(feed.getTargetKind()).isEqualTo(AppNotificationFeedTargetType.PROFILE);
        assertThat(feed.getTargetUserId()).isEqualTo(PARTNER);
        assertThat(feed.getTargetChatRoomId()).isNull();
        assertThat(feed.getSimulationDate()).isEqualTo(DATE);
        assertThat(feed.getTitle()).isEqualTo("박상대님과 친구가 되었어요.");
        assertThat(feed.getBody()).isEqualTo("평행세계에서 박상대님과 친구가 되었어요.");
    }

    @Test
    @DisplayName("탈퇴한 상대의 실명은 제목·본문 어디에도 노출되지 않는다")
    void writeFriend_uses_withdrawn_display_name() {
        // given: 탈퇴한 상대
        User withdrawn = user(PARTNER, "박", "상대");
        ReflectionTestUtils.setField(withdrawn, "deletedAt", java.time.Instant.now());
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(withdrawn));

        // when: 친구 승격 알림 기록
        appNotificationFeedWriter.writeFriend(ME, PARTNER, DATE);

        // then: 제목·본문 어디에도 원래 실명이 노출되지 않음
        ArgumentCaptor<AppNotificationFeed> captor = ArgumentCaptor.forClass(AppNotificationFeed.class);
        then(appNotificationFeedRepository).should().save(captor.capture());
        assertThat(captor.getValue().getTitle()).doesNotContain("박상대");
        assertThat(captor.getValue().getBody()).doesNotContain("박상대");
    }

    @Test
    @DisplayName("실명을 채울 상대가 없으면 USER_NOT_FOUND 예외가 발생하고 피드를 저장하지 않는다")
    void writeMatch_partner_not_found_throws() {
        // given: 상대 유저 행이 없음
        given(userRepository.findById(PARTNER)).willReturn(Optional.empty());

        // when & then: 예외가 나고 저장이 일어나지 않음
        assertThatThrownBy(() -> appNotificationFeedWriter.writeMatch(ROOM_ID, ME, PARTNER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(appNotificationFeedRepository).should(never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<AppNotificationFeed> savedFeeds() {
        ArgumentCaptor<List<AppNotificationFeed>> captor = ArgumentCaptor.forClass(List.class);
        then(appNotificationFeedRepository).should().saveAll(captor.capture());
        return captor.getValue();
    }

    private AppNotificationFeed feedOf(List<AppNotificationFeed> feeds, Long userId) {
        return feeds.stream()
                .filter(feed -> feed.getUserId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    private User user(Long id, String familyName, String givenName) {
        User user = User.create(
                "nickname",
                familyName, "familyHash",
                givenName, "givenHash",
                Gender.MALE,
                "aff", "affHash",
                "affNo", "affNoHash",
                "2000-01-01", "birthHash",
                "phone", "phoneHash",
                "email", "emailHash"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
