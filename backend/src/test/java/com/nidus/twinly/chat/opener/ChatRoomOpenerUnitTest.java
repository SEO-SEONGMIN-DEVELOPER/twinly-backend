package com.nidus.twinly.chat.opener;

import com.nidus.twinly.chat.config.ChatProperties;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.event.ChatChangedEvent;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatRoomOpenerUnitTest {

    private static final int THRESHOLD = 70;
    private static final Long ME = 1L;
    private static final Long PARTNER = 2L;
    private static final Long MATCH_ID = 100L;
    private static final Long ROOM_ID = 10L;
    private static final Long CURRENT_SEASON_ID = 5L;

    @Mock
    MatchRepository matchRepository;

    @Mock
    ChatRoomRepository chatRoomRepository;

    @Mock
    ChatRoomParticipationRepository chatRoomParticipationRepository;

    @Mock
    AppNotificationFeedWriter appNotificationFeedWriter;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    ApplicationEventPublisher eventPublisher;

    ChatRoomOpener chatRoomOpener;

    @BeforeEach
    void setUp() {
        chatRoomOpener = new ChatRoomOpener(matchRepository, chatRoomRepository, chatRoomParticipationRepository,
                appNotificationFeedWriter, currentSeasonReader, new ChatProperties(THRESHOLD), eventPublisher);
    }

    @Test
    @DisplayName("채팅방이 새로 열리면 match 알림 피드를 양쪽에 남긴다")
    void openIfEligible_writes_match_feed() {
        // given: 매칭·채팅방이 아직 없고 친밀도가 임계값을 넘음
        given(matchRepository.findByUserAIdAndUserBId(ME, PARTNER)).willReturn(Optional.empty());
        given(currentSeasonReader.read()).willReturn(season());
        given(matchRepository.save(any())).willReturn(match());
        given(chatRoomRepository.findByMatchId(MATCH_ID)).willReturn(Optional.empty());
        given(chatRoomRepository.save(any())).willReturn(room());

        // when: 채팅방 개설 시도
        chatRoomOpener.openIfEligible(ME, PARTNER, THRESHOLD);

        // then: 생성된 방을 가리키는 match 피드를 남기고 목록 갱신 이벤트를 발행
        then(appNotificationFeedWriter).should().writeMatch(ROOM_ID, ME, PARTNER);
        then(eventPublisher).should().publishEvent(any(ChatChangedEvent.class));
    }

    @Test
    @DisplayName("친밀도가 임계값에 못 미치면 방도 match 피드도 만들지 않는다")
    void openIfEligible_below_threshold_writes_nothing() {
        // when: 임계값 미만으로 개설 시도
        chatRoomOpener.openIfEligible(ME, PARTNER, THRESHOLD - 1);

        // then: 아무것도 만들지 않음
        then(chatRoomRepository).should(never()).save(any());
        then(appNotificationFeedWriter).should(never()).writeMatch(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("이미 채팅방이 있으면 match 피드를 중복으로 남기지 않는다")
    void openIfEligible_existing_room_writes_nothing() {
        // given: 해당 매칭의 채팅방이 이미 존재
        given(matchRepository.findByUserAIdAndUserBId(ME, PARTNER)).willReturn(Optional.of(match()));
        given(chatRoomRepository.findByMatchId(MATCH_ID)).willReturn(Optional.of(room()));

        // when: 채팅방 개설 시도
        chatRoomOpener.openIfEligible(ME, PARTNER, THRESHOLD);

        // then: 방도 피드도 추가로 만들지 않음
        then(chatRoomRepository).should(never()).save(any());
        then(appNotificationFeedWriter).should(never()).writeMatch(anyLong(), anyLong(), anyLong());
    }

    private Match match() {
        Match match = Match.create(ME, PARTNER, CURRENT_SEASON_ID);
        ReflectionTestUtils.setField(match, "id", MATCH_ID);
        return match;
    }

    private ChatRoom room() {
        ChatRoom room = ChatRoom.create(MATCH_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    private Season season() {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", CURRENT_SEASON_ID);
        return season;
    }
}
