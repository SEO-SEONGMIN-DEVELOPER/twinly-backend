package com.nidus.twinly.onboarding.integration;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AnonSessionAiChat;
import com.nidus.twinly.aichat.repository.AnonSessionAiChatRepository;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 응답 생성이 실패했을 때의 경계를 고정한다.
 *
 * <p>사용자 답변과 DETAIL 페르소나를 먼저 저장하고 다음 질문을 생성하므로, 생성이 터지면 앞의 저장도
 * 함께 롤백되어야 한다. 답변만 남고 다음 질문이 없으면 그 턴은 영영 이어지지 않는다.
 *
 * <p>실제 롤백은 테스트 트랜잭션 안에서 관찰할 수 없어 트랜잭션을 끄고 검증한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiChatFailureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AnonSessionRepository anonSessionRepository;

    @Autowired
    AnonSessionAiChatRepository anonSessionAiChatRepository;

    @Autowired
    AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(자식 → 부모)
        anonSessionAiChatRepository.deleteAll();
        anonSessionPersonaElementRepository.deleteAll();
        anonSessionRepository.deleteAll();
    }

    @Test
    @DisplayName("다음 질문 생성이 실패하면 방금 받은 답변과 페르소나도 남기지 않는다")
    void ai_response_failure_rolls_back_saved_answer() throws Exception {
        // given: 0번 턴 AI 질문까지 저장된 상태에서 모델 호출이 장애인 상황
        AnonSession anonSession = anonSessionRepository.save(
                AnonSession.create(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(1))));
        anonSessionAiChatRepository.save(
                AnonSessionAiChat.create(anonSession.getId(), AiChatSender.AI, "요즘 뭐에 빠져 있어?", 0));
        willThrow(new IllegalStateException("Bedrock 장애")).given(bedrockService).converse(anyString());

        // when: 0번 턴에 답변
        mockMvc.perform(post("/api/v1/onboarding/ai-chat/messages")
                        .header("Authorization", "Bearer " + anonSession.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"등산에 빠져 있어","turnIndex":0}
                                """))
                .andExpect(status().isInternalServerError());

        // then: 사용자 답변이 남지 않는다 (남으면 멱등 분기에 걸려 다음 질문 없이 영영 막힌다)
        assertThat(anonSessionAiChatRepository
                .findByAnonSessionIdAndTurnIndexAndSender(anonSession.getId(), 0, AiChatSender.USER)).isEmpty();

        // then: DETAIL 페르소나도 남지 않는다
        assertThat(anonSessionPersonaElementRepository.findAllByAnonSessionId(anonSession.getId())).isEmpty();
    }
}
