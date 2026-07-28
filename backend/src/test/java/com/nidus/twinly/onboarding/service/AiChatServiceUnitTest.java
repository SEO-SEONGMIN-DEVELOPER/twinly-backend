package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.aichat.service.AiChatService;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.onboarding.dto.command.OnboardingAiChatMessageCommand;
import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatMessageResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatStartResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AiChatServiceUnitTest {

    private static final Long ANON_SESSION_ID = 1L;
    private static final AnonSessionSnapshot ANON_SESSION = new AnonSessionSnapshot(
            ANON_SESSION_ID,
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            Instant.parse("2999-01-01T00:00:00Z"),
            "닉네임",
            "홍",
            "길동",
            Gender.MALE,
            "니두스대학교",
            "2024001",
            "2000-01-01",
            "01012345678",
            "phoneHash",
            "test@test.com",
            "emailHash",
            Instant.parse("2026-01-01T00:00:00Z")
    );

    @Mock
    BedrockService bedrockService;

    @Mock
    AiChatRepository aiChatRepository;

    @Mock
    AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;

    @InjectMocks
    AiChatService aiChatService;

    @Test
    @DisplayName("AI 채팅 시작은 소속·페르소나가 담긴 프롬프트로 첫 질문을 받아 0번 턴 AI 메시지로 저장한다")
    void aiChatStart_saves_first_ai_question() {
        // given: 세션의 페르소나 요소가 1건 있고 모델이 첫 질문을 반환
        given(anonSessionPersonaElementRepository.findAllByAnonSessionId(ANON_SESSION_ID))
                .willReturn(List.of(AnonSessionPersonaElement.create(
                        ANON_SESSION_ID, PersonaDimension.EXTRAVERSION, "사람 만나는 것을 좋아함")));
        given(bedrockService.converse(anyString())).willReturn("요즘 제일 자주 가는 곳은 어디야?");

        // when: AI 채팅 시작
        OnboardingAiChatStartResult result = aiChatService.aiChatStart(ANON_SESSION);

        // then: 프롬프트에 소속·페르소나가 포함되고, 첫 질문이 0번 턴 AI 메시지로 저장됨
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("니두스대학교", "사람 만나는 것을 좋아함");

        ArgumentCaptor<AiChat> chatCaptor = ArgumentCaptor.forClass(AiChat.class);
        then(aiChatRepository).should().save(chatCaptor.capture());
        assertThat(chatCaptor.getValue().getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(chatCaptor.getValue().getSender()).isEqualTo(AiChatSender.AI);
        assertThat(chatCaptor.getValue().getMessage()).isEqualTo("요즘 제일 자주 가는 곳은 어디야?");
        assertThat(chatCaptor.getValue().getTurnIndex()).isZero();

        assertThat(result.message()).isEqualTo("요즘 제일 자주 가는 곳은 어디야?");
        assertThat(result.turnIndex()).isZero();
        assertThat(result.isEnd()).isFalse();
    }

    @Test
    @DisplayName("해당 턴의 AI 질문이 없으면 AI_QUESTION_NOT_FOUND 예외가 발생하고 아무것도 저장하지 않는다")
    void aiChatMessage_when_ai_question_missing_throws() {
        // given: 0번 턴의 AI 질문이 없음
        given(aiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 0, AiChatSender.AI))
                .willReturn(Optional.empty());

        // when & then: AI_QUESTION_NOT_FOUND 예외 발생 + 저장 없음
        assertThatThrownBy(() -> aiChatService.aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("응 좋아", 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_QUESTION_NOT_FOUND);

        then(aiChatRepository).should(never()).save(any());
        then(anonSessionPersonaElementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("마지막 턴 이전이면 사용자 답변을 저장하고 DETAIL 페르소나를 남긴 뒤 다음 턴 질문을 반환한다")
    void aiChatMessage_middle_turn_returns_next_question() {
        // given: 0번 턴 AI 질문이 존재하고 모델이 다음 질문을 반환
        given(aiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 0, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ANON_SESSION_ID, AiChatSender.AI, "요즘 뭐 하고 지내?", 0)));
        given(anonSessionPersonaElementRepository.findAllByAnonSessionId(ANON_SESSION_ID)).willReturn(List.of());
        given(bedrockService.converse(anyString())).willReturn("그럼 거기서 뭘 주로 해?");

        // when: 0번 턴에 답변 전송
        OnboardingAiChatMessageResult result = aiChatService.aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("한강에 자주 가", 0));

        // then: 사용자 답변(0턴)과 다음 AI 질문(1턴)이 저장되고, 질문+답변이 DETAIL 페르소나로 남음
        ArgumentCaptor<AiChat> chatCaptor = ArgumentCaptor.forClass(AiChat.class);
        then(aiChatRepository).should(times(2)).save(chatCaptor.capture());
        AiChat userChat = chatCaptor.getAllValues().get(0);
        AiChat nextAiChat = chatCaptor.getAllValues().get(1);
        assertThat(userChat.getSender()).isEqualTo(AiChatSender.USER);
        assertThat(userChat.getMessage()).isEqualTo("한강에 자주 가");
        assertThat(userChat.getTurnIndex()).isZero();
        assertThat(nextAiChat.getSender()).isEqualTo(AiChatSender.AI);
        assertThat(nextAiChat.getMessage()).isEqualTo("그럼 거기서 뭘 주로 해?");
        assertThat(nextAiChat.getTurnIndex()).isEqualTo(1);

        ArgumentCaptor<AnonSessionPersonaElement> personaCaptor = ArgumentCaptor.forClass(AnonSessionPersonaElement.class);
        then(anonSessionPersonaElementRepository).should().save(personaCaptor.capture());
        assertThat(personaCaptor.getValue().getDimension()).isEqualTo(PersonaDimension.DETAIL);
        assertThat(personaCaptor.getValue().getExplanation()).isEqualTo("요즘 뭐 하고 지내?: 한강에 자주 가");

        assertThat(result.turnIndex()).isEqualTo(1);
        assertThat(result.isEnd()).isFalse();
    }

    @Test
    @DisplayName("마지막 턴(7)에 답하면 모델 호출 없이 종료 메시지와 isEnd=true를 반환한다")
    void aiChatMessage_last_turn_ends_conversation() {
        // given: 7번 턴 AI 질문이 존재
        given(aiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 7, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ANON_SESSION_ID, AiChatSender.AI, "마지막 질문", 7)));

        // when: 7번 턴에 답변 전송
        OnboardingAiChatMessageResult result = aiChatService.aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("재밌었어", 7));

        // then: 종료 응답 + 모델 호출 없음 + 사용자 답변만 1건 저장
        assertThat(result.message()).isEqualTo("지금까지 이야기 들려줘서 고마워!");
        assertThat(result.turnIndex()).isEqualTo(7);
        assertThat(result.isEnd()).isTrue();
        then(bedrockService).should(never()).converse(anyString());
        then(aiChatRepository).should(times(1)).save(any());
    }
}
