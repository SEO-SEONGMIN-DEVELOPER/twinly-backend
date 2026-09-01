package com.nidus.twinly.season.integration;

import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeasonIntegrationTest extends AbstractIntegrationTest {

    /** 활성 시즌으로 넣을 시즌 id. */
    private static final long CURRENT_SEASON_ID = 1L;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("시즌 참가 조회 성공: 참가 이력이 있으면 현재 시즌 id와 참가 시각을 응답한다")
    void participation_when_participated_end_to_end() throws Exception {
        // given: 현재 시즌과 유저를 저장하고 고정된 참가 시각으로 참가 이력을 DB에 만든다
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveUser();
        SeasonParticipation participation = SeasonParticipation.create(user.getId(), CURRENT_SEASON_ID);
        ReflectionTestUtils.setField(participation, "participatedInAt", Instant.parse("2026-07-01T00:00:00Z"));
        seasonParticipationRepository.save(participation);

        // when: 해당 유저의 실제 액세스 토큰으로 시즌 참가 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/participation")
                .header("Authorization", bearer(user.getId())));

        // then: 현재 시즌 id는 문자열로, 참가 시각은 KstInstantSerializer가 KST 오프셋으로 직렬화하여 응답
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeasonId").value(String.valueOf(CURRENT_SEASON_ID)))
                .andExpect(jsonPath("$.participatedInAt").value("2026-07-01T09:00:00+09:00"));
    }

    @Test
    @DisplayName("시즌 참가 조회 성공: 참가 이력이 없으면 participatedInAt이 null로 응답된다")
    void participation_when_not_participated_end_to_end() throws Exception {
        // given: 활성 시즌만 있고 참가 이력이 없는 실제 유저
        Instant now = Instant.now();
        saveCurrentSeason(now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(30)));
        User user = saveUser();

        // when: 해당 유저의 실제 액세스 토큰으로 시즌 참가 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/participation")
                .header("Authorization", bearer(user.getId())));

        // then: 현재 시즌 id는 채워지고 참가 시각은 null
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeasonId").value(String.valueOf(CURRENT_SEASON_ID)))
                .andExpect(jsonPath("$.participatedInAt", nullValue()));
    }

    /**
     * seasons는 마이그레이션 시드가 없고 엔티티에 생성 팩토리도 없으므로,
     * id와 is_active를 명시해 활성 시즌을 직접 insert 한다. (테스트 트랜잭션 롤백으로 정리됨)
     */
    private void saveCurrentSeason(Instant startedAt, Instant endedAt) {
        entityManager.createNativeQuery(
                        "INSERT INTO seasons (id, started_at, ended_at, is_active) VALUES (?1, ?2, ?3, 1)")
                .setParameter(1, CURRENT_SEASON_ID)
                .setParameter(2, Timestamp.from(startedAt))
                .setParameter(3, Timestamp.from(endedAt))
                .executeUpdate();
    }
}
