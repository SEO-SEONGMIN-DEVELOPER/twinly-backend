package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AnonSessionAiChat;
import com.nidus.twinly.aichat.repository.AnonSessionAiChatRepository;
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
            "트윈리대학교",
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
    AnonSessionAiChatRepository anonSessionAiChatRepository;

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
        assertThat(promptCaptor.getValue()).contains("트윈리대학교", "사람 만나는 것을 좋아함");

        ArgumentCaptor<AnonSessionAiChat> chatCaptor = ArgumentCaptor.forClass(AnonSessionAiChat.class);
        then(anonSessionAiChatRepository).should().save(chatCaptor.capture());
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
        given(anonSessionAiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 0, AiChatSender.AI))
                .willReturn(Optional.empty());

        // when & then: AI_QUESTION_NOT_FOUND 예외 발생 + 저장 없음
        assertThatThrownBy(() -> aiChatService.aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("응 좋아", 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_QUESTION_NOT_FOUND);

        then(anonSessionAiChatRepository).should(never()).save(any());
        then(anonSessionPersonaElementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("마지막 턴 이전이면 사용자 답변을 저장하고 DETAIL 페르소나를 남긴 뒤 다음 턴 질문을 반환한다")
    void aiChatMessage_middle_turn_returns_next_question() {
        // given: 0번 턴 AI 질문이 존재하고 모델이 다음 질문을 반환
        given(anonSessionAiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 0, AiChatSender.AI))
                .willReturn(Optional.of(AnonSessionAiChat.create(ANON_SESSION_ID, AiChatSender.AI, "요즘 뭐 하고 지내?", 0)));
        given(anonSessionPersonaElementRepository.findAllByAnonSessionId(ANON_SESSION_ID)).willReturn(List.of());
        given(bedrockService.converse(anyString())).willReturn("그럼 거기서 뭘 주로 해?");

        // when: 0번 턴에 답변 전송
        OnboardingAiChatMessageResult result = aiChatService.aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("한강에 자주 가", 0));

        // then: 사용자 답변(0턴)과 다음 AI 질문(1턴)이 저장되고, 질문+답변이 DETAIL 페르소나로 남음
        ArgumentCaptor<AnonSessionAiChat> chatCaptor = ArgumentCaptor.forClass(AnonSessionAiChat.class);
        then(anonSessionAiChatRepository).should(times(2)).save(chatCaptor.capture());
        AnonSessionAiChat userChat = chatCaptor.getAllValues().get(0);
        AnonSessionAiChat nextAiChat = chatCaptor.getAllValues().get(1);
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
    @DisplayName("AI 채팅 시작 프롬프트는 관심사 중 하나를 골라 질문하도록 지시한다")
    void aiChatStart_prompt_asks_about_interest() {
        // given: 세션의 관심사가 2건 있음
        given(anonSessionPersonaElementRepository.findAllByAnonSessionId(ANON_SESSION_ID))
                .willReturn(List.of(
                        AnonSessionPersonaElement.create(ANON_SESSION_ID, PersonaDimension.INTEREST, "등산"),
                        AnonSessionPersonaElement.create(ANON_SESSION_ID, PersonaDimension.INTEREST, "재즈")));
        given(bedrockService.converse(anyString())).willReturn("등산은 어디로 자주 가?");

        // when: AI 채팅 시작
        aiChatService.aiChatStart(ANON_SESSION);

        // then: 프롬프트에 관심사 목록과 관심사 질문 지시가 포함됨
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("[관심사]", "등산", "재즈", "위 관심사 중 하나를 골라");
    }

    @Test
    @DisplayName("5번째 턴(인덱스 4) 질문은 직전 답변을 잇지 않고 관심사로 새 대화를 시작한다")
    void aiChatMessage_restart_turn_asks_new_interest_question() {
        // given: 3번 턴 AI 질문이 존재하고 0~3번 턴 AI 질문이 이미 쌓여 있음
        given(anonSessionAiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 3, AiChatSender.AI))
                .willReturn(Optional.of(AnonSessionAiChat.create(ANON_SESSION_ID, AiChatSender.AI, "그 산 정상에서 뭐 했어?", 3)));
        given(anonSessionAiChatRepository.findByAnonSessionIdOrderByTurnIndexAscSenderDesc(ANON_SESSION_ID))
                .willReturn(List.of(
                        AnonSessionAiChat.create(ANON_SESSION_ID, AiChatSender.AI, "등산은 어디로 자주 가?", 0),
                        AnonSessionAiChat.create(ANON_SESSION_ID, AiChatSender.USER, "북한산", 0),
                        AnonSessionAiChat.create(ANON_SESSION_ID, AiChatSender.AI, "그 산 정상에서 뭐 했어?", 3)));
        given(anonSessionPersonaElementRepository.findAllByAnonSessionId(ANON_SESSION_ID))
                .willReturn(List.of(
                        AnonSessionPersonaElement.create(ANON_SESSION_ID, PersonaDimension.INTEREST, "등산"),
                        AnonSessionPersonaElement.create(ANON_SESSION_ID, PersonaDimension.INTEREST, "재즈")));
        given(bedrockService.converse(anyString())).willReturn("재즈는 어떤 아티스트 좋아해?");

        // when: 3번 턴에 답변 전송
        OnboardingAiChatMessageResult result = aiChatService.aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("사진 찍었어", 3));

        // then: 후속 질문 프롬프트가 아니라 관심사 시작 질문 프롬프트로 4번 턴 질문을 생성함
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("위 관심사 중 하나를 골라", "[이미 물어본 질문]", "등산은 어디로 자주 가?", "완전히 새로운 주제")
                .doesNotContain("[방금 나눈 대화]", "사진 찍었어");

        assertThat(result.message()).isEqualTo("재즈는 어떤 아티스트 좋아해?");
        assertThat(result.turnIndex()).isEqualTo(4);
        assertThat(result.isEnd()).isFalse();
    }

    @Test
    @DisplayName("마지막 턴(7)에 답하면 다음 질문 대신 대화 전체를 요약한 SUMMARY 요소를 저장하고 isEnd=true를 반환한다")
    void aiChatMessage_last_turn_saves_summary_and_ends_conversation() {
        // given: 7번 턴 AI 질문이 존재하고, 세션에 관심사·성격·지난 대화 요소가 쌓여 있으며 모델이 요약 문장을 반환
        given(anonSessionAiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 7, AiChatSender.AI))
                .willReturn(Optional.of(AnonSessionAiChat.create(ANON_SESSION_ID, AiChatSender.AI, "마지막 질문", 7)));
        given(anonSessionPersonaElementRepository.findAllByAnonSessionId(ANON_SESSION_ID))
                .willReturn(List.of(
                        AnonSessionPersonaElement.create(ANON_SESSION_ID, PersonaDimension.INTEREST, "등산"),
                        AnonSessionPersonaElement.create(ANON_SESSION_ID, PersonaDimension.EXTRAVERSION, "사람 만나는 것을 좋아함"),
                        AnonSessionPersonaElement.create(ANON_SESSION_ID, PersonaDimension.DETAIL, "등산은 어디로 자주 가?: 북한산")));
        given(bedrockService.converse(anyString())).willReturn("  주말마다 북한산에 오르며 사진으로 순간을 남기는 사람\n");

        // when: 7번 턴에 답변 전송
        OnboardingAiChatMessageResult result = aiChatService.aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("재밌었어", 7));

        // then: 종료 응답 + 사용자 답변만 1건 저장(다음 AI 질문 없음)
        assertThat(result.message()).isEqualTo("지금까지 이야기 들려줘서 고마워!");
        assertThat(result.turnIndex()).isEqualTo(7);
        assertThat(result.isEnd()).isTrue();
        then(anonSessionAiChatRepository).should(times(1)).save(any());

        // then: 요약 프롬프트에 소속·관심사·성격·지난 대화가 모두 담기고 "~한 사람" 형식을 지시함
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("트윈리대학교", "[관심사]", "등산", "[성격 특성]", "사람 만나는 것을 좋아함",
                        "[나눈 대화]", "등산은 어디로 자주 가?: 북한산", "~한 사람");

        // then: DETAIL 다음에 공백이 정리된 SUMMARY 요소가 저장됨
        ArgumentCaptor<AnonSessionPersonaElement> personaCaptor = ArgumentCaptor.forClass(AnonSessionPersonaElement.class);
        then(anonSessionPersonaElementRepository).should(times(2)).save(personaCaptor.capture());
        AnonSessionPersonaElement detail = personaCaptor.getAllValues().get(0);
        AnonSessionPersonaElement summary = personaCaptor.getAllValues().get(1);
        assertThat(detail.getDimension()).isEqualTo(PersonaDimension.DETAIL);
        assertThat(detail.getExplanation()).isEqualTo("마지막 질문: 재밌었어");
        assertThat(summary.getAnonSessionId()).isEqualTo(ANON_SESSION_ID);
        assertThat(summary.getDimension()).isEqualTo(PersonaDimension.SUMMARY);
        assertThat(summary.getExplanation()).isEqualTo("주말마다 북한산에 오르며 사진으로 순간을 남기는 사람");
    }

    @Test
    @DisplayName("마지막 턴(7)에 이미 답한 뒤 같은 요청이 다시 오면 요약을 다시 만들지 않고 종료 응답만 반환한다")
    void aiChatMessage_last_turn_is_idempotent() {
        // given: 7번 턴의 AI 질문과 사용자 답변이 모두 존재
        given(anonSessionAiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 7, AiChatSender.AI))
                .willReturn(Optional.of(AnonSessionAiChat.create(ANON_SESSION_ID, AiChatSender.AI, "마지막 질문", 7)));
        given(anonSessionAiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(ANON_SESSION_ID, 7, AiChatSender.USER))
                .willReturn(Optional.of(AnonSessionAiChat.create(ANON_SESSION_ID, AiChatSender.USER, "재밌었어", 7)));

        // when: 7번 턴에 같은 답변을 다시 전송
        OnboardingAiChatMessageResult result = aiChatService.aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("재밌었어", 7));

        // then: 종료 응답만 반환하고 모델 호출·저장은 일어나지 않음
        assertThat(result.isEnd()).isTrue();
        then(bedrockService).should(never()).converse(anyString());
        then(anonSessionAiChatRepository).should(never()).save(any());
        then(anonSessionPersonaElementRepository).should(never()).save(any());
    }
}
