package com.nidus.twinly.app.integration;

import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "admin.api-token=" + AppFeatureFlagIntegrationTest.ADMIN_TOKEN)
class AppFeatureFlagIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "app-feature-flag-integration-token";

    private static final String DOT_WORLD_KEY = "app:feature:dot-world";
    private static final String DOT_WORLD_PATH = "/admin/app/feature-flags/dot-world";

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    SeasonRepository seasonRepository;

    @AfterEach
    void turnOff() throws Exception {
        changeDotWorld(false);
        redisTemplate.delete(DOT_WORLD_KEY);
    }

    @Test
    @DisplayName("도트 세계를 켜면 Redis에 저장되고 메인 탭 응답에 바로 켜짐으로 내려간다")
    void updateDotWorld_reflectedInMainTab() throws Exception {
        // given: 메인 탭을 조회할 유저와 현재 시즌
        User me = saveUser();
        seasonRepository.save(Season.create(Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(30))));

        // when: 관리자 API로 켠다
        changeDotWorld(true);

        // then: Redis에 저장되고 관리자 조회와 메인 탭 모두 켜짐이다
        assertThat(redisTemplate.opsForValue().get(DOT_WORLD_KEY)).isEqualTo("true");
        mockMvc.perform(get("/admin/app/feature-flags").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dotWorldEnabled").value(true));
        mockMvc.perform(get("/api/v1/main").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dotWorldEnabled").value(true));
    }

    @Test
    @DisplayName("켰다가 끄면 메인 탭 응답에 바로 꺼짐으로 내려간다")
    void updateDotWorld_turnOff_reflectedInMainTab() throws Exception {
        // given: 메인 탭을 조회할 유저와 현재 시즌, 켜 둔 스위치
        User me = saveUser();
        seasonRepository.save(Season.create(Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(30))));
        changeDotWorld(true);

        // when: 관리자 API로 끈다
        changeDotWorld(false);

        // then: 메인 탭이 꺼짐이다
        mockMvc.perform(get("/api/v1/main").header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dotWorldEnabled").value(false));
    }

    private void changeDotWorld(boolean enabled) throws Exception {
        mockMvc.perform(put(DOT_WORLD_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }
}
