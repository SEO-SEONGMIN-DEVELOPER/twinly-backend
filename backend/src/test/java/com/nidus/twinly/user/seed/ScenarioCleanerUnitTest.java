package com.nidus.twinly.user.seed;

import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomOpeningRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.people.repository.EncounterPreferenceRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.showcase.repository.ShowcaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ScenarioCleanerUnitTest {

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    SceneRepository sceneRepository;

    @Mock
    QuestionPartnerRepository questionPartnerRepository;

    @Mock
    QuestionRepository questionRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    ShowcaseRepository showcaseRepository;

    @Mock
    AppNotificationFeedRepository appNotificationFeedRepository;

    @Mock
    ChatRoomParticipationRepository chatRoomParticipationRepository;

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatRoomRepository chatRoomRepository;

    @Mock
    MatchRepository matchRepository;

    @Mock
    ChatRoomOpeningRepository chatRoomOpeningRepository;

    @Mock
    EncounterPreferenceRepository encounterPreferenceRepository;

    @Mock
    EncounterRepository encounterRepository;

    @InjectMocks
    ScenarioCleaner scenarioCleaner;

    @Test
    @DisplayName("시나리오 테이블과 친구 알림 피드, 쇼케이스 유저끼리의 채팅·매치·인연을 자식부터 부모 순서로 지운다")
    void clear_deletes_scenario_tables_and_friend_feeds_in_order() {
        // given: 정리 대상 유저 목록
        List<Long> userIds = List.of(1L, 2L);

        // when: 정리 실행
        scenarioCleaner.clear(userIds);

        // then: FK 자식(partner)이 먼저, 부모가 나중에 지워지고 친구 알림 피드까지 함께 지운다
        InOrder inOrder = inOrder(scenePartnerRepository, sceneRepository, questionPartnerRepository, questionRepository,
                relationshipRepository, showcaseRepository, appNotificationFeedRepository, chatRoomParticipationRepository,
                chatRepository, chatRoomRepository, matchRepository, chatRoomOpeningRepository, encounterPreferenceRepository,
                encounterRepository);
        inOrder.verify(scenePartnerRepository).deleteAllBySceneUserIdIn(userIds);
        inOrder.verify(sceneRepository).deleteAllByUserIdIn(userIds);
        inOrder.verify(questionPartnerRepository).deleteAllByQuestionUserIdIn(userIds);
        inOrder.verify(questionRepository).deleteAllByUserIdIn(userIds);
        inOrder.verify(relationshipRepository).deleteAllByUserIdIn(userIds);
        inOrder.verify(showcaseRepository).deleteAllByTargetUserIdIn(userIds);
        inOrder.verify(appNotificationFeedRepository).deleteAllByUserIdInAndType(userIds, AppNotificationFeedType.FRIEND);

        // then: 시나리오로 열린 채팅은 알림 → 참여자 → 메시지 → 방 → 매치 순으로, 개설 예약과 인연까지 지운다
        inOrder.verify(appNotificationFeedRepository).deleteAllByTargetChatRoomBetweenUserIdsIn(userIds);
        inOrder.verify(chatRoomParticipationRepository).deleteAllByRoomBetweenUserIdsIn(userIds);
        inOrder.verify(chatRepository).deleteAllByRoomBetweenUserIdsIn(userIds);
        inOrder.verify(chatRoomRepository).deleteAllByMatchBetweenUserIdsIn(userIds);
        inOrder.verify(matchRepository).deleteAllBetweenUserIdsIn(userIds);
        inOrder.verify(chatRoomOpeningRepository).deleteAllBetweenUserIdsIn(userIds);
        inOrder.verify(encounterPreferenceRepository).deleteAllByEncounterBetweenUserIdsIn(userIds);
        inOrder.verify(encounterRepository).deleteAllBetweenUserIdsIn(userIds);
    }

    @Test
    @DisplayName("대상 유저가 없으면 아무 테이블도 건드리지 않는다")
    void clear_skips_when_no_user() {
        // when: 빈 목록으로 정리 실행
        scenarioCleaner.clear(List.of());

        // then: 어떤 삭제도 일어나지 않는다
        verifyNoInteractions(scenePartnerRepository, sceneRepository, questionPartnerRepository, questionRepository,
                relationshipRepository, showcaseRepository, appNotificationFeedRepository, chatRoomParticipationRepository,
                chatRepository, chatRoomRepository, matchRepository, chatRoomOpeningRepository, encounterPreferenceRepository,
                encounterRepository);
    }
}
