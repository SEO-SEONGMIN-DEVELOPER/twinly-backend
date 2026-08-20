package com.nidus.twinly.simulation.integration;

import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.people.repository.EncounterPreferenceRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 채팅방 개설은 ChatRoomOpener 가 REQUIRES_NEW 로 별도 커넥션에서 처리한다.
 * 테스트 트랜잭션 안에서 만든 픽스처는 그 커넥션에 보이지 않아, 시즌 조회가 실패하거나
 * matches 의 FK 검사가 미커밋 행의 락을 기다리다 타임아웃한다.
 * 그래서 이 클래스만 테스트 트랜잭션을 끄고 픽스처를 실제 커밋한 뒤 직접 정리한다.
 * MySQL 컨테이너를 다른 REST 통합 테스트와 공유하므로 정리를 빠뜨리면 그쪽까지 오염된다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SimulationChatRoomIntegrationTest extends AbstractIntegrationTest {

    private static final int RAPPORT_OVER_THRESHOLD = 75;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    MatchRepository matchRepository;

    @Autowired
    ChatRoomRepository chatRoomRepository;

    @Autowired
    ChatRoomParticipationRepository chatRoomParticipationRepository;

    @Autowired
    AppNotificationFeedRepository appNotificationFeedRepository;

    @Autowired
    EncounterPreferenceRepository encounterPreferenceRepository;

    @Autowired
    EncounterRepository encounterRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    QuestionPartnerRepository questionPartnerRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Test
    @DisplayName("친밀도가 임계치를 넘으면 매치·채팅방·참여자와 매칭 알림이 실제로 만들어진다")
    void simulations_opens_chat_room_when_rapport_reaches_threshold() throws Exception {
        // given: REQUIRES_NEW 쪽 커넥션이 볼 수 있도록 시즌과 유저를 실제 커밋 상태로 둔다
        seasonRepository.save(Season.create(
                Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(30))));
        User me = saveUser();
        User partner = saveUser();

        // when: 임계치를 넘는 친밀도로 하루치 결과를 반영
        simulate(me, partner, RAPPORT_OVER_THRESHOLD);

        // then: 매치와 채팅방이 열리고 두 사람이 참여자로 들어간다
        Match match = matchRepository
                .findByUserAIdAndUserBId(Math.min(me.getId(), partner.getId()), Math.max(me.getId(), partner.getId()))
                .orElseThrow();
        ChatRoom room = chatRoomRepository.findByMatchId(match.getId()).orElseThrow();
        assertThat(chatRoomParticipationRepository.findAllByRoomId(room.getId()))
                .extracting(ChatRoomParticipation::getUserId)
                .containsExactlyInAnyOrder(me.getId(), partner.getId());

        // then: 매칭 알림이 양쪽에 남는다
        assertThat(appNotificationFeedRepository.findAll())
                .extracting(AppNotificationFeed::getUserId, AppNotificationFeed::getType)
                .contains(tuple(me.getId(), AppNotificationFeedType.MATCH),
                        tuple(partner.getId(), AppNotificationFeedType.MATCH));
    }

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(자식 → 부모)
        appNotificationFeedRepository.deleteAll();
        chatRoomParticipationRepository.deleteAll();
        chatRoomRepository.deleteAll();
        matchRepository.deleteAll();
        encounterPreferenceRepository.deleteAll();
        encounterRepository.deleteAll();
        relationshipRepository.deleteAll();
        questionPartnerRepository.deleteAll();
        questionRepository.deleteAll();
        scenePartnerRepository.deleteAll();
        sceneRepository.deleteAll();
        seasonRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void simulate(User me, User partner, int rapport) throws Exception {
        String payload = """
                {
                  "userId": "%d",
                  "date": "2026-08-18",
                  "scenes": [],
                  "questions": [],
                  "relationships": [
                    {
                      "partnerId": "%d",
                      "updateTime": "2026-08-18T22:00:00",
                      "rapport": %d,
                      "partnerModel": "model-v1"
                    }
                  ]
                }
                """.formatted(me.getId(), partner.getId(), rapport);

        mockMvc.perform(post("/internal/v1/users/{userId}/simulations", me.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }
}
