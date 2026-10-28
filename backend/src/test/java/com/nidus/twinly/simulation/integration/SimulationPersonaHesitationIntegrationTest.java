package com.nidus.twinly.simulation.integration;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 페르소나 조회는 트랜잭션 없이(NOT_SUPPORTED) 실행되어 테스트 트랜잭션 안의 미커밋 픽스처를 보지 못한다.
 * 그래서 이 클래스만 테스트 트랜잭션을 끄고 픽스처를 실제 커밋한 뒤 직접 정리한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SimulationPersonaHesitationIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 3);

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    UserEntitlementRepository userEntitlementRepository;

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(자식 → 부모)
        questionRepository.deleteAll();
        userEntitlementRepository.deleteAll();
        agreementRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("내가 답한 망설임 질문만 날짜·시각 순으로 내려주고, 안 답한 것·건너뛴 것·약속 질문·남의 답은 뺀다")
    void persona_includes_only_my_answered_hesitations_in_date_time_order() throws Exception {
        // given: 시뮬레이션 권한과 동의가 있는 나와 다른 유저
        User me = saveUser();
        User other = saveUser();
        grantSimulationAccess(me);

        // given: 저장 순서와 상관없이 10/2 18시 → 10/1 20시 → 10/1 9시 순으로 답한 망설임 질문
        saveQuestion(me, LocalDate.of(2026, 10, 2), LocalTime.of(18, 0), QuestionType.PERSONA, "셋째", "C");
        saveQuestion(me, LocalDate.of(2026, 10, 1), LocalTime.of(20, 0), QuestionType.PERSONA, "둘째", "B");
        saveQuestion(me, LocalDate.of(2026, 10, 1), LocalTime.of(9, 0), QuestionType.PERSONA, "첫째", "A");

        // given: 빠져야 하는 질문들 — 안 답한 것, 건너뛴 것, 약속 질문, 남의 답
        saveQuestion(me, LocalDate.of(2026, 10, 1), LocalTime.of(10, 0), QuestionType.PERSONA, "안 답함", null);
        Question skipped = Question.create(me.getId(), LocalDate.of(2026, 10, 1), "v1", LocalDate.of(2026, 10, 1).atTime(11, 0),
                QuestionType.PERSONA, "건너뜀", List.of("A", "B"));
        skipped.skip();
        questionRepository.save(skipped);
        saveQuestion(me, LocalDate.of(2026, 10, 1), LocalTime.of(12, 0), QuestionType.PROMISE, "약속", "A");
        saveQuestion(other, LocalDate.of(2026, 10, 1), LocalTime.of(13, 0), QuestionType.PERSONA, "남의 답", "A");

        // when: 페르소나 조회
        var result = mockMvc.perform(get("/internal/v1/users/{userId}/persona", me.getId().toString())
                .param("date", DATE.toString()));

        // then: 내가 답한 망설임 질문 세 개만 날짜·시각 순으로 담긴다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.hesitationAnswers", hasSize(3)))
                .andExpect(jsonPath("$.hesitationAnswers[*].text", contains("첫째", "둘째", "셋째")))
                .andExpect(jsonPath("$.hesitationAnswers[*].choice", contains("A", "B", "C")))
                .andExpect(jsonPath("$.hesitationAnswers[0].date", is("2026-10-01")))
                .andExpect(jsonPath("$.hesitationAnswers[0].options", contains("A", "B", "C")));
    }

    private void grantSimulationAccess(User user) {
        userEntitlementRepository.save(UserEntitlement.create(user.getId(), EntitlementReader.SIMULATION_ACCESS,
                Instant.now().plus(Duration.ofDays(1)), Instant.now()));
        agreeRequiredParallelEntryPolicies(user.getId());
    }

    private void saveQuestion(User user, LocalDate date, LocalTime time, QuestionType type, String text, String choice) {
        Question question = Question.create(user.getId(), date, "v1", date.atTime(time), type, text, List.of("A", "B", "C"));
        if (choice != null) {
            question.answer(choice);
        }
        questionRepository.save(question);
    }
}
