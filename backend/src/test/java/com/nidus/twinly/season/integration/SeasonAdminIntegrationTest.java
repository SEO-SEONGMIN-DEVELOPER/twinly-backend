package com.nidus.twinly.season.integration;

import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "admin.api-token=" + SeasonAdminIntegrationTest.ADMIN_TOKEN)
class SeasonAdminIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "admin-integration-token";

    private static final String ADMIN_SEASON_PATH = "/admin/season";

    @Autowired
    SeasonRepository seasonRepository;

    @Test
    @DisplayName("시즌 전환: 관리자 토큰으로 호출하면 기존 활성 시즌이 꺼지고 새 시즌 행이 활성으로 생성된다")
    void changeSeason_end_to_end() throws Exception {
        // given: 이미 활성 시즌이 하나 있는 상태
        Season previous = seasonRepository.save(
                Season.create(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-01T00:00:00Z")));

        // when
        var result = mockMvc.perform(post(ADMIN_SEASON_PATH)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt":"2026-09-01T00:00:00Z","endedAt":"2026-12-01T00:00:00Z"}
                        """));

        // then: 새 시즌 id가 문자열로 응답되고, DB에는 활성 시즌이 새 것 하나만 남는다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonId").isString());

        List<Season> active = seasonRepository.findAllByIsActiveTrue();

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().getStartedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(active.getFirst().getId()).isNotEqualTo(previous.getId());
        assertThat(seasonRepository.findById(previous.getId()).orElseThrow().getIsActive()).isFalse();
    }

    @Test
    @DisplayName("시즌 전환 후 유저 API가 새 시즌을 현재 시즌으로 바라본다")
    void changeSeason_switchesCurrentSeasonForUsers() throws Exception {
        // given: 전환 이후
        mockMvc.perform(post(ADMIN_SEASON_PATH)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt":"2026-09-01T00:00:00Z","endedAt":"2026-12-01T00:00:00Z"}
                        """));

        Long newSeasonId = seasonRepository.findAllByIsActiveTrue().getFirst().getId();

        // when & then: 유저의 시즌 참가 조회가 새 시즌을 가리킨다
        mockMvc.perform(get("/api/v1/season/participation")
                        .header("Authorization", bearer(saveUser().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeasonId").value(String.valueOf(newSeasonId)));
    }

    @Test
    @DisplayName("시즌 전환 실패: 시작이 종료보다 뒤면 422와 INVALID_SEASON_PERIOD를 반환하고 아무 행도 만들지 않는다")
    void changeSeason_invalidPeriod_returns422() throws Exception {
        long before = seasonRepository.count();

        mockMvc.perform(post(ADMIN_SEASON_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-12-01T00:00:00Z","endedAt":"2026-09-01T00:00:00Z"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_SEASON_PERIOD.name()));

        assertThat(seasonRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("시즌 전환은 관리자 토큰 없이는 호출할 수 없다")
    void changeSeason_requiresAdminToken() throws Exception {
        mockMvc.perform(post(ADMIN_SEASON_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-09-01T00:00:00Z","endedAt":"2026-12-01T00:00:00Z"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        assertThat(seasonRepository.findAllByIsActiveTrue()).isEmpty();
    }
}
