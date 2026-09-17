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
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile({"prod", "stage", "local"})
@RequiredArgsConstructor
public class ScenarioCleaner {

    private final ScenePartnerRepository scenePartnerRepository;
    private final SceneRepository sceneRepository;
    private final QuestionPartnerRepository questionPartnerRepository;
    private final QuestionRepository questionRepository;
    private final RelationshipRepository relationshipRepository;
    private final ShowcaseRepository showcaseRepository;
    private final AppNotificationFeedRepository appNotificationFeedRepository;
    private final ChatRoomParticipationRepository chatRoomParticipationRepository;
    private final ChatRepository chatRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MatchRepository matchRepository;
    private final ChatRoomOpeningRepository chatRoomOpeningRepository;
    private final EncounterPreferenceRepository encounterPreferenceRepository;
    private final EncounterRepository encounterRepository;

    @Transactional
    public void clear(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }

        scenePartnerRepository.deleteAllBySceneUserIdIn(userIds);
        sceneRepository.deleteAllByUserIdIn(userIds);
        questionPartnerRepository.deleteAllByQuestionUserIdIn(userIds);
        questionRepository.deleteAllByUserIdIn(userIds);
        relationshipRepository.deleteAllByUserIdIn(userIds);
        showcaseRepository.deleteAllByTargetUserIdIn(userIds);
        appNotificationFeedRepository.deleteAllByUserIdInAndType(userIds, AppNotificationFeedType.FRIEND);

        appNotificationFeedRepository.deleteAllByTargetChatRoomBetweenUserIdsIn(userIds);
        chatRoomParticipationRepository.deleteAllByRoomBetweenUserIdsIn(userIds);
        chatRepository.deleteAllByRoomBetweenUserIdsIn(userIds);
        chatRoomRepository.deleteAllByMatchBetweenUserIdsIn(userIds);
        matchRepository.deleteAllBetweenUserIdsIn(userIds);
        chatRoomOpeningRepository.deleteAllBetweenUserIdsIn(userIds);
        encounterPreferenceRepository.deleteAllByEncounterBetweenUserIdsIn(userIds);
        encounterRepository.deleteAllBetweenUserIdsIn(userIds);
    }
}
