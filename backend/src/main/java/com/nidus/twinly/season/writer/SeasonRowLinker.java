package com.nidus.twinly.season.writer;

import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.relationship.repository.IntimacyBonusRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeasonRowLinker {

    private final RelationshipRepository relationshipRepository;
    private final IntimacyBonusRepository intimacyBonusRepository;
    private final SceneRepository sceneRepository;

    public void link(Season season) {
        relationshipRepository.fillSeasonId(season.getId(), season.startDate(), season.nextStartDate());
        sceneRepository.fillSeasonId(season.getId(), season.startDate(), season.nextStartDate());
        intimacyBonusRepository.fillSeasonId(season.getId(), season.getStartedAt(), KstTimes.toInstant(season.nextStartDate().atStartOfDay()));
    }

    public void unlink(Season season) {
        relationshipRepository.clearSeasonId(season.getId());
        sceneRepository.clearSeasonId(season.getId());
        intimacyBonusRepository.clearSeasonId(season.getId());
    }
}
