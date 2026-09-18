package com.nidus.twinly.user.integration;

import com.nidus.twinly.auth.event.UserSignedUpEvent;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonaSummaryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    PersonaElementRepository personaElementRepository;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("가입 트랜잭션이 커밋되면 비동기 스레드가 유저 페르소나로 요약을 만들어 SUMMARY 요소를 실제 DB에 저장한다")
    void summary_is_saved_after_signup_commit() {
        // given: 비동기 스레드가 읽을 수 있도록 유저와 페르소나 요소를 먼저 커밋해 둔다
        User user = saveUser();
        personaElementRepository.save(PersonaElement.create(user.getId(), PersonaDimension.INTEREST, "등산", Instant.now()));
        personaElementRepository.save(PersonaElement.create(user.getId(), PersonaDimension.DETAIL, "요즘 뭐에 빠져 있어?: 등산", Instant.now()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        given(bedrockService.converse(anyString())).willReturn("주말마다 산에 오르며 작은 순간에서 행복을 찾는 사람");

        try {
            // when: 가입 트랜잭션 안에서 이벤트가 발행되고 커밋됨
            transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new UserSignedUpEvent(user.getId())));

            // then: 커밋 이후 별도 스레드에서 SUMMARY 가 저장되고, 실제 DB ENUM 컬럼에도 SUMMARY 로 기록됨
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                List<PersonaElement> summaries = personaElementRepository
                        .findAllByUserIdAndDimensionOrderByIdAsc(user.getId(), PersonaDimension.SUMMARY);

                assertThat(summaries).hasSize(1);
                assertThat(summaries.getFirst().getExplanation()).isEqualTo("주말마다 산에 오르며 작은 순간에서 행복을 찾는 사람");
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT dimension FROM persona_elements WHERE id = ?", String.class, summaries.getFirst().getId()))
                        .isEqualTo("SUMMARY");
            });
        } finally {
            // 커밋한 데이터는 롤백되지 않으므로 직접 정리한다
            TestTransaction.start();
            personaElementRepository.deleteAll(personaElementRepository.findAllByUserIdOrderByIdAsc(user.getId()));
            userRepository.deleteById(user.getId());
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    @Test
    @DisplayName("가입 트랜잭션이 롤백되면 요약을 만들지 않는다")
    void summary_is_not_generated_when_signup_rolls_back() {
        // given
        User user = saveUser();
        personaElementRepository.save(PersonaElement.create(user.getId(), PersonaDimension.INTEREST, "등산", Instant.now()));
        TestTransaction.flagForCommit();
        TestTransaction.end();

        try {
            // when: 이벤트 발행 후 가입 트랜잭션이 롤백됨
            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(new UserSignedUpEvent(user.getId()));
                status.setRollbackOnly();
            });

            // then: 일정 시간 동안 모델 호출도 SUMMARY 저장도 일어나지 않음
            await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                then(bedrockService).should(never()).converse(anyString());
                assertThat(personaElementRepository
                        .findAllByUserIdAndDimensionOrderByIdAsc(user.getId(), PersonaDimension.SUMMARY)).isEmpty();
            });
        } finally {
            TestTransaction.start();
            personaElementRepository.deleteAll(personaElementRepository.findAllByUserIdOrderByIdAsc(user.getId()));
            userRepository.deleteById(user.getId());
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    @Test
    @DisplayName("AI 대화 종료 API가 커밋되면 비동기 스레드가 요약을 SUMMARY 요소로 저장하고, 종료를 다시 요청해도 요약은 한 번만 만든다")
    void summary_is_saved_once_after_ai_chat_complete() throws Exception {
        // given: 비동기 스레드가 읽을 수 있도록 유저와 페르소나 요소를 먼저 커밋해 둔다
        User user = saveUser();
        personaElementRepository.save(PersonaElement.create(user.getId(), PersonaDimension.INTEREST, "등산", Instant.now()));
        personaElementRepository.save(PersonaElement.create(user.getId(), PersonaDimension.DETAIL, "요즘 뭐에 빠져 있어?: 등산", Instant.now()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        given(bedrockService.converse(anyString())).willReturn("주말마다 산에 오르며 작은 순간에서 행복을 찾는 사람");

        try {
            // when: AI 대화 종료 API를 두 번 호출 (앱 재시도 상황)
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/api/v1/me/ai-chat/complete")
                                .header("Authorization", bearer(user.getId())))
                        .andExpect(status().isOk());
            }

            // then: 커밋 이후 별도 스레드에서 SUMMARY 가 1건 저장됨
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(personaElementRepository
                    .findAllByUserIdAndDimensionOrderByIdAsc(user.getId(), PersonaDimension.SUMMARY))
                    .extracting(PersonaElement::getExplanation)
                    .containsExactly("주말마다 산에 오르며 작은 순간에서 행복을 찾는 사람"));

            // then: 두 번째 종료 요청은 이벤트를 내지 않아 모델은 한 번만 호출됨
            await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    then(bedrockService).should(times(1)).converse(anyString()));
        } finally {
            TestTransaction.start();
            personaElementRepository.deleteAll(personaElementRepository.findAllByUserIdOrderByIdAsc(user.getId()));
            userRepository.deleteById(user.getId());
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }
}
