package com.nidus.twinly.user.seed;

import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.showcase.repository.ShowcaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile({"stage", "local"})
@RequiredArgsConstructor
public class ScenarioCleaner {

    private final ScenePartnerRepository scenePartnerRepository;
    private final SceneRepository sceneRepository;
    private final QuestionPartnerRepository questionPartnerRepository;
    private final QuestionRepository questionRepository;
    private final RelationshipRepository relationshipRepository;
    private final ShowcaseRepository showcaseRepository;

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
    }
}
