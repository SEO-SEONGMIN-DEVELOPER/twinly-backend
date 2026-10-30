package com.nidus.twinly.chat.opener;

import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.event.ChatChangedEvent;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.relationship.domain.RelationshipType;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatRoomOpenerUnitTest {

    private static final int THRESHOLD = RelationshipType.BEST_FRIEND.minIntimacy();
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
                appNotificationFeedWriter, currentSeasonReader, eventPublisher);
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
        // given: 현재 시즌 매칭의 채팅방이 이미 존재
        given(currentSeasonReader.read()).willReturn(season());
        given(matchRepository.findByUserAIdAndUserBId(ME, PARTNER)).willReturn(Optional.of(match()));
        given(chatRoomRepository.findByMatchId(MATCH_ID)).willReturn(Optional.of(room()));

        // when: 채팅방 개설 시도
        chatRoomOpener.openIfEligible(ME, PARTNER, THRESHOLD);

        // then: 방도 피드도 추가로 만들지 않음
        then(chatRoomRepository).should(never()).save(any());
        then(appNotificationFeedWriter).should(never()).writeMatch(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("이전 시즌에 열린 방이 둘 다 입장 전이면 현재 시즌으로 옮기고 match 피드를 다시 남긴다")
    void open_moves_unentered_previous_season_room_to_current_season() {
        // given: 이전 시즌 매칭의 방이 있고 상대가 아직 입장 전
        Match previousMatch = match(CURRENT_SEASON_ID - 1);
        given(currentSeasonReader.read()).willReturn(season());
        given(matchRepository.findByUserAIdAndUserBId(ME, PARTNER)).willReturn(Optional.of(previousMatch));
        given(chatRoomRepository.findByMatchId(MATCH_ID)).willReturn(Optional.of(room()));
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(entered(ME), ChatRoomParticipation.create(ROOM_ID, PARTNER)));

        // when: 채팅방 개설 시도
        chatRoomOpener.open(ME, PARTNER);

        // then: 새 방 없이 매칭을 현재 시즌으로 옮기고 피드·목록 갱신 이벤트를 남김
        assertThat(previousMatch.getSeasonId()).isEqualTo(CURRENT_SEASON_ID);
        then(chatRoomRepository).should(never()).save(any());
        then(appNotificationFeedWriter).should().writeMatch(ROOM_ID, ME, PARTNER);
        then(eventPublisher).should().publishEvent(any(ChatChangedEvent.class));
    }

    @Test
    @DisplayName("이전 시즌에 열린 방이 둘 다 입장했으면 시즌을 옮기지 않고 아무것도 남기지 않는다")
    void open_keeps_entered_previous_season_room() {
        // given: 이전 시즌 매칭의 방에 둘 다 입장함
        Long previousSeasonId = CURRENT_SEASON_ID - 1;
        Match previousMatch = match(previousSeasonId);
        given(currentSeasonReader.read()).willReturn(season());
        given(matchRepository.findByUserAIdAndUserBId(ME, PARTNER)).willReturn(Optional.of(previousMatch));
        given(chatRoomRepository.findByMatchId(MATCH_ID)).willReturn(Optional.of(room()));
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID)).willReturn(List.of(entered(ME), entered(PARTNER)));

        // when: 채팅방 개설 시도
        chatRoomOpener.open(ME, PARTNER);

        // then: 시즌·피드·이벤트 모두 그대로
        assertThat(previousMatch.getSeasonId()).isEqualTo(previousSeasonId);
        then(appNotificationFeedWriter).should(never()).writeMatch(anyLong(), anyLong(), anyLong());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    private ChatRoomParticipation entered(Long userId) {
        ChatRoomParticipation participation = ChatRoomParticipation.create(ROOM_ID, userId);
        participation.agree();
        return participation;
    }

    private Match match() {
        return match(CURRENT_SEASON_ID);
    }

    private Match match(Long seasonId) {
        Match match = Match.create(ME, PARTNER, seasonId);
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
