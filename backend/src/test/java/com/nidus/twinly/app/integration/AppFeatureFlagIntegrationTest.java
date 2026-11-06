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
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import org.springframework.test.web.servlet.ResultActions;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@TestPropertySource(properties = "admin.api-token=" + AppFeatureFlagIntegrationTest.ADMIN_TOKEN)
class AppFeatureFlagIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "app-feature-flag-integration-token";

    private static final String DOT_WORLD_KEY = "app:feature:dot-world";
    private static final String DOT_WORLD_PATH = "/admin/app/feature-flags/dot-world";
    private static final String COMPANY_SIGNUP_KEY = "app:feature:company-signup";
    private static final String COMPANY_SIGNUP_PATH = "/admin/app/feature-flags/company-signup";

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    AnonSessionRepository anonSessionRepository;

    @AfterEach
    void turnOff() throws Exception {
        changeDotWorld(false);
        redisTemplate.delete(DOT_WORLD_KEY);
        changeCompanySignup(false);
        redisTemplate.delete(COMPANY_SIGNUP_KEY);
    }

    @Test
    @DisplayName("직장인 가입 스위치를 켜면 조회에 켜짐으로 나오고 회사 이메일 발송이 통과하며, 끄면 바로 다시 422 로 거절된다")
    void updateCompanySignup_gates_company_email() throws Exception {
        // given: 실제 익명 세션과, 메일 발송을 막은 목
        UUID anonToken = UUID.randomUUID();
        anonSessionRepository.save(AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1))));
        willDoNothing().given(sesService).send(anyString(), anyString(), anyString());

        // when: 관리자가 직장인 가입을 켠다
        changeCompanySignup(true);

        // then: 스위치 조회에 켜짐으로 나오고, 회사 이메일 발송이 통과한다
        mockMvc.perform(get("/admin/app/feature-flags").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companySignupEnabled").value(true));
        sendCompanyEmail(anonToken).andExpect(status().isOk());

        // when: 다시 끈다
        changeCompanySignup(false);

        // then: 같은 서버에서는 캐시를 기다리지 않고 바로 거절된다
        sendCompanyEmail(anonToken)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_NOT_SUPPORTED"));
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

    private ResultActions sendCompanyEmail(UUID anonToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/onboarding/email/send")
                .header("Authorization", "Bearer " + anonToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"worker@twinly-company.co.kr"}
                        """));
    }

    private void changeCompanySignup(boolean enabled) throws Exception {
        mockMvc.perform(put(COMPANY_SIGNUP_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }

    private void changeDotWorld(boolean enabled) throws Exception {
        mockMvc.perform(put(DOT_WORLD_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }
}
