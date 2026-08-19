package com.nidus.twinly.simulation.integration;

import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulationIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 18);

    @Autowired
    SceneRepository sceneRepository;

    @Autowired
    ScenePartnerRepository scenePartnerRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    QuestionPartnerRepository questionPartnerRepository;

    @Test
    @DisplayName("같은 날짜로 다시 반영하면 이전 장면·질문과 그 자식 행까지 정리되고 새 결과로 교체된다")
    void simulations_replaces_previous_result_of_same_date() throws Exception {
        // given: 1차 반영. 픽스처를 직접 save하지 않고 API로 만들어야, 실제 코드가 만드는 자식 행 조합이 그대로 재현된다
        User me = saveUser();
        User partner = saveUser();
        simulate(me, partner, "1교시 교실", "같이 갈래?");

        List<Long> previousSceneIds = ids(sceneRepository.findAllByUserIdAndDate(me.getId(), DATE), Scene::getId);
        List<Long> previousQuestionIds = ids(questionRepository.findAllByUserIdAndDate(me.getId(), DATE), Question::getId);

        // given: 자식 행이 실제로 존재해야 FK 제약이 걸린다. 없으면 이 테스트는 아무것도 검증하지 못한다
        assertThat(scenePartnerRepository.findAllBySceneIdIn(previousSceneIds)).isNotEmpty();
        assertThat(questionPartnerRepository.findAllByQuestionIdIn(previousQuestionIds)).isNotEmpty();

        // when: 같은 날짜로 2차 반영
        simulate(me, partner, "점심 식당", "밥 먹자");

        // then: 이전 부모 행이 사라지고 자식 행도 함께 정리된다 (삭제 순서가 틀리면 FK 위반으로 여기서 드러난다)
        assertThat(sceneRepository.findAllById(previousSceneIds)).isEmpty();
        assertThat(questionRepository.findAllById(previousQuestionIds)).isEmpty();
        assertThat(scenePartnerRepository.findAllBySceneIdIn(previousSceneIds)).isEmpty();
        assertThat(questionPartnerRepository.findAllByQuestionIdIn(previousQuestionIds)).isEmpty();

        // then: 새 결과만 남고, 새 부모의 자식 행은 다시 만들어진다
        List<Scene> scenes = sceneRepository.findAllByUserIdAndDate(me.getId(), DATE);
        assertThat(scenes).extracting(Scene::getPlace).containsExactly("점심 식당");
        assertThat(scenePartnerRepository.findAllBySceneIdIn(ids(scenes, Scene::getId)))
                .extracting(scenePartner -> scenePartner.getUserId())
                .containsExactly(partner.getId());
    }

    private void simulate(User me, User partner, String place, String line) throws Exception {
        String payload = """
                {
                  "userId": "%d",
                  "date": "2026-08-18",
                  "scenes": [
                    {
                      "type": "dialogue",
                      "start": "2026-08-18T09:00:00",
                      "end": "2026-08-18T09:30:00",
                      "place": "%s",
                      "with": ["%d"],
                      "lines": [
                        {"t": "bubble", "userId": "%d", "text": "%s", "occursAt": "2026-08-18T09:10:00"}
                      ]
                    }
                  ],
                  "questions": [
                    {
                      "time": "2026-08-18T21:00:00",
                      "qtype": "promise",
                      "partnerId": ["%d"],
                      "text": "오늘 어땠어?",
                      "options": ["좋았어", "그저 그랬어"]
                    }
                  ],
                  "relationships": [
                    {
                      "partnerId": "%d",
                      "updateTime": "2026-08-18T22:00:00",
                      "rapport": 20,
                      "partnerModel": "model-v1"
                    }
                  ]
                }
                """.formatted(me.getId(), place, partner.getId(), partner.getId(), line,
                partner.getId(), partner.getId());

        mockMvc.perform(post("/internal/v1/users/{userId}/simulations", me.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private <T> List<Long> ids(List<T> entities, java.util.function.Function<T, Long> idGetter) {
        return entities.stream().map(idGetter).toList();
    }
}
