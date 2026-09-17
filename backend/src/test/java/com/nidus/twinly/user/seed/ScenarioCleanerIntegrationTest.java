package com.nidus.twinly.user.seed;

import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomOpening;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomOpeningRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.people.entity.Encounter;
import com.nidus.twinly.people.entity.EncounterPreference;
import com.nidus.twinly.people.repository.EncounterPreferenceRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.showcase.repository.ShowcaseRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioCleanerIntegrationTest extends AbstractIntegrationTest {

    @Autowired ScenePartnerRepository scenePartnerRepository;
    @Autowired SceneRepository sceneRepository;
    @Autowired QuestionPartnerRepository questionPartnerRepository;
    @Autowired QuestionRepository questionRepository;
    @Autowired RelationshipRepository relationshipRepository;
    @Autowired ShowcaseRepository showcaseRepository;
    @Autowired AppNotificationFeedRepository appNotificationFeedRepository;
    @Autowired ChatRoomParticipationRepository chatRoomParticipationRepository;
    @Autowired ChatRepository chatRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired ChatRoomOpeningRepository chatRoomOpeningRepository;
    @Autowired EncounterPreferenceRepository encounterPreferenceRepository;
    @Autowired EncounterRepository encounterRepository;
    @Autowired SeasonRepository seasonRepository;
    @Autowired EntityManager entityManager;

    ScenarioCleaner scenarioCleaner;

    Season season;

    @BeforeEach
    void setUp() {
        // given: 운영 프로필 전용 빈이라 테스트 프로필에서는 실제 리포지토리로 직접 조립한다
        scenarioCleaner = new ScenarioCleaner(scenePartnerRepository, sceneRepository, questionPartnerRepository,
                questionRepository, relationshipRepository, showcaseRepository, appNotificationFeedRepository,
                chatRoomParticipationRepository, chatRepository, chatRoomRepository, matchRepository,
                chatRoomOpeningRepository, encounterPreferenceRepository, encounterRepository);
        season = seasonRepository.save(Season.create(Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(30))));
    }

    @Test
    @DisplayName("쇼케이스 유저끼리의 채팅방·메시지·매치·개설 예약·인연은 지우고, 실제 사용자가 낀 쌍은 남긴다")
    void clear_deletes_only_pairs_between_showcase_users() {
        // given: 쇼케이스 유저 둘과 실제 사용자 하나
        User showcaseA = saveUser();
        User showcaseB = saveUser();
        User realUser = saveUser();

        // given: 쇼케이스 쌍과, 쇼케이스 유저와 실제 사용자 쌍에 같은 종류의 데이터를 만든다
        PairData showcasePair = createPairData(showcaseA, showcaseB);
        PairData mixedPair = createPairData(showcaseA, realUser);
        entityManager.flush();
        entityManager.clear();

        // when: 쇼케이스 유저 목록으로 정리
        scenarioCleaner.clear(List.of(showcaseA.getId(), showcaseB.getId()));
        entityManager.flush();
        entityManager.clear();

        // then: 쇼케이스 쌍의 데이터는 모두 사라진다
        assertThat(appNotificationFeedRepository.findById(showcasePair.feedId())).isEmpty();
        assertThat(chatRoomParticipationRepository.findAllByRoomId(showcasePair.roomId())).isEmpty();
        assertThat(chatRepository.findById(showcasePair.chatId())).isEmpty();
        assertThat(chatRoomRepository.findById(showcasePair.roomId())).isEmpty();
        assertThat(matchRepository.findById(showcasePair.matchId())).isEmpty();
        assertThat(chatRoomOpeningRepository.findById(showcasePair.openingId())).isEmpty();
        assertThat(encounterPreferenceRepository.findById(showcasePair.encounterPreferenceId())).isEmpty();
        assertThat(encounterRepository.findById(showcasePair.encounterId())).isEmpty();

        // then: 실제 사용자가 낀 쌍의 데이터는 그대로 남는다
        assertThat(appNotificationFeedRepository.findById(mixedPair.feedId())).isPresent();
        assertThat(chatRoomParticipationRepository.findAllByRoomId(mixedPair.roomId())).hasSize(2);
        assertThat(chatRepository.findById(mixedPair.chatId())).isPresent();
        assertThat(chatRoomRepository.findById(mixedPair.roomId())).isPresent();
        assertThat(matchRepository.findById(mixedPair.matchId())).isPresent();
        assertThat(chatRoomOpeningRepository.findById(mixedPair.openingId())).isPresent();
        assertThat(encounterPreferenceRepository.findById(mixedPair.encounterPreferenceId())).isPresent();
        assertThat(encounterRepository.findById(mixedPair.encounterId())).isPresent();
    }

    private PairData createPairData(User user, User partner) {
        Match match = matchRepository.save(Match.create(user.getId(), partner.getId(), season.getId()));
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(match.getId()));
        chatRoomParticipationRepository.saveAll(List.of(
                ChatRoomParticipation.create(room.getId(), user.getId()),
                ChatRoomParticipation.create(room.getId(), partner.getId())));
        Chat chat = chatRepository.save(Chat.create(UUID.randomUUID().toString(), room.getId(), user.getId(), partner.getId(),
                ChatMessageType.TEXT, "안녕"));
        entityManager.flush();
        chatRoomParticipationRepository.advanceReadPointer(room.getId(), partner.getId(), chat.getId());
        AppNotificationFeed feed = appNotificationFeedRepository.save(AppNotificationFeed.createChatTarget(
                user.getId(), AppNotificationFeedType.MATCH, "매칭", "매칭됐어요", room.getId()));
        ChatRoomOpening opening = chatRoomOpeningRepository.save(ChatRoomOpening.create(user.getId(), partner.getId(),
                Instant.now().plus(Duration.ofDays(1))));
        Encounter encounter = encounterRepository.save(Encounter.create(user.getId(), partner.getId()));
        EncounterPreference preference = encounterPreferenceRepository.save(EncounterPreference.create(encounter.getId(), user.getId()));

        return new PairData(match.getId(), room.getId(), chat.getId(), feed.getId(), opening.getId(), encounter.getId(), preference.getId());
    }

    private record PairData(Long matchId, Long roomId, Long chatId, Long feedId, Long openingId, Long encounterId,
                            Long encounterPreferenceId) {
    }
}
