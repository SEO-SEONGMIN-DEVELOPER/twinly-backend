package com.nidus.twinly.user.integration;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.UserSurveyAnswer;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "admin.api-token=" + UserAdminIntegrationTest.ADMIN_TOKEN)
class UserAdminIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "admin-integration-token";

    @Autowired
    PersonaElementRepository personaElementRepository;

    @Autowired
    UserSurveyAnswerRepository userSurveyAnswerRepository;

    @Autowired
    AiChatRepository aiChatRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("페르소나 초기화: 대상 유저의 설문·관심사·AI 대화가 지워져 상태 API 가 셋 다 false 를 주고, 다른 유저는 그대로다")
    void resetPersona_end_to_end() throws Exception {
        // given: 페르소나를 채운 대상 유저와 다른 유저
        User target = saveOnboardedUser();
        User other = saveOnboardedUser();
        entityManager.flush();
        entityManager.clear();

        // when: 관리자 토큰으로 대상 유저 페르소나 초기화
        mockMvc.perform(delete("/admin/users/{userId}/persona", target.getId())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk());
        entityManager.clear();

        // then: 대상 유저의 페르소나 데이터가 모두 비고 대화 완료 시각도 비워진다
        assertThat(personaElementRepository.existsByUserId(target.getId())).isFalse();
        assertThat(userSurveyAnswerRepository.existsByUserId(target.getId())).isFalse();
        assertThat(aiChatRepository.existsByUserId(target.getId())).isFalse();
        assertThat(userRepository.findById(target.getId()).orElseThrow().getAiChatCompletedAt()).isNull();

        // then: 상태 API 에서 persona 셋 다 false
        mockMvc.perform(get("/api/v1/me/status")
                        .header("Authorization", bearer(target.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona.isSurveyCompleted").value(false))
                .andExpect(jsonPath("$.persona.isInterestsCompleted").value(false))
                .andExpect(jsonPath("$.persona.isAiChatCompleted").value(false));

        // then: 다른 유저는 그대로 남는다
        assertThat(personaElementRepository.existsByUserId(other.getId())).isTrue();
        assertThat(userSurveyAnswerRepository.existsByUserId(other.getId())).isTrue();
        assertThat(aiChatRepository.existsByUserId(other.getId())).isTrue();
        assertThat(userRepository.findById(other.getId()).orElseThrow().getAiChatCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("페르소나 초기화: 없는 유저면 404 USER_NOT_FOUND")
    void resetPersona_unknownUser_returns_404() throws Exception {
        // when & then
        mockMvc.perform(delete("/admin/users/{userId}/persona", Long.MAX_VALUE)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.name()));
    }

    private User saveOnboardedUser() {
        User user = saveUser();
        Instant now = Instant.now();

        personaElementRepository.save(PersonaElement.create(user.getId(), PersonaDimension.INTEREST, "영화", now));
        userSurveyAnswerRepository.save(UserSurveyAnswer.create(user.getId(), 1, SurveyOptionName.A));
        aiChatRepository.save(AiChat.create(user.getId(), AiChatSender.AI, "질문", 0, now));
        userRepository.markAiChatCompleted(user.getId(), now);

        return user;
    }
}
