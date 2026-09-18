package com.nidus.twinly.aichat.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.event.AiChatCompletedEvent;
import com.nidus.twinly.aichat.prompt.AiChatPromptBuilder;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.me.dto.command.MeAiChatMessageCommand;
import com.nidus.twinly.me.dto.result.MeAiChatMessageResult;
import com.nidus.twinly.me.dto.result.MeAiChatStartResult;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserAiChatServiceUnitTest {

    private static final Long ME = 1L;
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Mock
    BedrockService bedrockService;

    @Spy
    AiChatPromptBuilder aiChatPromptBuilder = new AiChatPromptBuilder();

    @Mock
    UserRepository userRepository;

    @Mock
    AiChatRepository aiChatRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    UserAiChatService userAiChatService;

    @Test
    @DisplayName("AI 채팅 시작은 유저 소속·페르소나가 담긴 프롬프트로 첫 질문을 받아 0번 턴 AI 메시지로 저장한다")
    void aiChatStart_saves_first_ai_question() {
        // given: 시작한 적 없음 + 유저 소속 "니두스" + 관심사 1건, 모델이 첫 질문을 반환
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 0, AiChatSender.AI)).willReturn(Optional.empty());
        given(userRepository.findById(ME)).willReturn(Optional.of(user()));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME))
                .willReturn(List.of(PersonaElement.create(ME, PersonaDimension.INTEREST, "등산", NOW)));
        given(bedrockService.converse(anyString())).willReturn("등산은 어디로 자주 가?");

        // when: AI 채팅 시작
        MeAiChatStartResult result = userAiChatService.aiChatStart(ME);

        // then: 프롬프트에 소속·관심사와 관심사 질문 지시가 담기고, 첫 질문이 0번 턴 AI 메시지로 저장됨
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("- 소속: 니두스", "- 등산", "위 관심사 중 하나를 골라");

        ArgumentCaptor<AiChat> chatCaptor = ArgumentCaptor.forClass(AiChat.class);
        then(aiChatRepository).should().save(chatCaptor.capture());
        assertThat(chatCaptor.getValue().getUserId()).isEqualTo(ME);
        assertThat(chatCaptor.getValue().getSender()).isEqualTo(AiChatSender.AI);
        assertThat(chatCaptor.getValue().getMessage()).isEqualTo("등산은 어디로 자주 가?");
        assertThat(chatCaptor.getValue().getTurnIndex()).isZero();

        assertThat(result).isEqualTo(new MeAiChatStartResult("등산은 어디로 자주 가?", 0, false));
    }

    @Test
    @DisplayName("이미 시작한 AI 채팅이면 저장된 첫 질문을 그대로 돌려주고 모델을 다시 부르지 않는다 (멱등)")
    void aiChatStart_already_started_returns_saved_question() {
        // given: 0번 턴 AI 질문이 이미 저장됨
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 0, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.AI, "등산은 어디로 자주 가?", 0, NOW)));

        // when: AI 채팅 다시 시작
        MeAiChatStartResult result = userAiChatService.aiChatStart(ME);

        // then: 저장된 질문을 반환하고 모델 호출·저장 없음
        assertThat(result).isEqualTo(new MeAiChatStartResult("등산은 어디로 자주 가?", 0, false));
        then(bedrockService).should(never()).converse(anyString());
        then(aiChatRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("AI 채팅 시작 시 유저가 없으면 USER_NOT_FOUND 예외가 발생하고 모델을 부르지 않는다")
    void aiChatStart_user_not_found_throws() {
        // given: 시작한 적 없고 유저도 없음
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 0, AiChatSender.AI)).willReturn(Optional.empty());
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생 + 모델 호출 없음
        assertThatThrownBy(() -> userAiChatService.aiChatStart(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(bedrockService).should(never()).converse(anyString());
    }

    @Test
    @DisplayName("해당 턴의 AI 질문이 없으면 AI_QUESTION_NOT_FOUND 예외가 발생하고 아무것도 저장하지 않는다")
    void aiChatMessage_when_ai_question_missing_throws() {
        // given: 0번 턴의 AI 질문이 없음
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 0, AiChatSender.AI)).willReturn(Optional.empty());

        // when & then: AI_QUESTION_NOT_FOUND 예외 발생 + 저장 없음
        assertThatThrownBy(() -> userAiChatService.aiChatMessage(ME, new MeAiChatMessageCommand("응 좋아", 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_QUESTION_NOT_FOUND);

        then(aiChatRepository).should(never()).save(any());
        then(personaElementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("마지막 턴 이전이면 사용자 답변을 저장하고 DETAIL 페르소나를 남긴 뒤 후속 질문 프롬프트로 다음 턴 질문을 반환한다")
    void aiChatMessage_middle_turn_returns_next_question() {
        // given: 0번 턴 AI 질문이 있고 아직 답하지 않음, 모델이 다음 질문을 반환
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 0, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.AI, "요즘 뭐 하고 지내?", 0, NOW)));
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 0, AiChatSender.USER)).willReturn(Optional.empty());
        given(userRepository.findById(ME)).willReturn(Optional.of(user()));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME)).willReturn(List.of());
        given(bedrockService.converse(anyString())).willReturn("그럼 거기서 뭘 주로 해?");

        // when: 0번 턴에 답변 전송
        MeAiChatMessageResult result = userAiChatService.aiChatMessage(ME, new MeAiChatMessageCommand("한강에 자주 가", 0));

        // then: 후속 질문 프롬프트에 방금 질문·답변이 담김
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("나의 질문: 요즘 뭐 하고 지내?", "사용자의 답변: 한강에 자주 가");

        // then: 사용자 답변(0턴)과 다음 AI 질문(1턴)이 저장됨
        ArgumentCaptor<AiChat> chatCaptor = ArgumentCaptor.forClass(AiChat.class);
        then(aiChatRepository).should(times(2)).save(chatCaptor.capture());
        assertThat(chatCaptor.getAllValues())
                .extracting(AiChat::getSender, AiChat::getMessage, AiChat::getTurnIndex)
                .containsExactly(
                        tuple(AiChatSender.USER, "한강에 자주 가", 0),
                        tuple(AiChatSender.AI, "그럼 거기서 뭘 주로 해?", 1));

        // then: 질문+답변이 DETAIL 페르소나로 남고 다음 턴 질문을 반환
        ArgumentCaptor<PersonaElement> personaCaptor = ArgumentCaptor.forClass(PersonaElement.class);
        then(personaElementRepository).should().save(personaCaptor.capture());
        assertThat(personaCaptor.getValue().getUserId()).isEqualTo(ME);
        assertThat(personaCaptor.getValue().getDimension()).isEqualTo(PersonaDimension.DETAIL);
        assertThat(personaCaptor.getValue().getExplanation()).isEqualTo("요즘 뭐 하고 지내?: 한강에 자주 가");

        assertThat(result).isEqualTo(new MeAiChatMessageResult("그럼 거기서 뭘 주로 해?", 1, false));
    }

    @Test
    @DisplayName("5번째 턴(인덱스 4) 질문은 직전 답변을 잇지 않고 이미 물어본 질문을 피해 관심사로 새 대화를 시작한다")
    void aiChatMessage_restart_turn_asks_new_interest_question() {
        // given: 3번 턴 AI 질문이 있고 아직 답하지 않음, 이미 물어본 AI 질문 2건
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 3, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.AI, "그 산 정상에서 뭐 했어?", 3, NOW)));
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 3, AiChatSender.USER)).willReturn(Optional.empty());
        given(userRepository.findById(ME)).willReturn(Optional.of(user()));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME))
                .willReturn(List.of(PersonaElement.create(ME, PersonaDimension.INTEREST, "재즈", NOW)));
        given(aiChatRepository.findByUserIdOrderByTurnIndexAscSenderDesc(ME))
                .willReturn(List.of(
                        AiChat.create(ME, AiChatSender.AI, "등산은 어디로 자주 가?", 0, NOW),
                        AiChat.create(ME, AiChatSender.USER, "북한산", 0, NOW),
                        AiChat.create(ME, AiChatSender.AI, "그 산 정상에서 뭐 했어?", 3, NOW)));
        given(bedrockService.converse(anyString())).willReturn("재즈는 어떤 아티스트 좋아해?");

        // when: 3번 턴에 답변 전송
        MeAiChatMessageResult result = userAiChatService.aiChatMessage(ME, new MeAiChatMessageCommand("사진 찍었어", 3));

        // then: 후속 질문이 아니라 이미 물어본 질문 목록이 담긴 관심사 프롬프트로 4번 턴 질문을 생성
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("[이미 물어본 질문]", "- 등산은 어디로 자주 가?", "- 그 산 정상에서 뭐 했어?", "완전히 새로운 주제")
                .doesNotContain("[방금 나눈 대화]", "- 북한산");

        assertThat(result).isEqualTo(new MeAiChatMessageResult("재즈는 어떤 아티스트 좋아해?", 4, false));
    }

    @Test
    @DisplayName("마지막 턴(7)에 답하면 DETAIL만 저장하고 모델을 부르지 않은 채 isEnd=true를 반환한다")
    void aiChatMessage_last_turn_ends_conversation() {
        // given: 7번 턴 AI 질문이 있고 아직 답하지 않음
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 7, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.AI, "마지막 질문", 7, NOW)));
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 7, AiChatSender.USER)).willReturn(Optional.empty());

        // when: 7번 턴에 답변 전송
        MeAiChatMessageResult result = userAiChatService.aiChatMessage(ME, new MeAiChatMessageCommand("재밌었어", 7));

        // then: 종료 응답 + 사용자 답변 1건과 DETAIL 1건만 저장, 모델 호출 없음
        assertThat(result).isEqualTo(new MeAiChatMessageResult("지금까지 이야기 들려줘서 고마워!", 7, true));
        then(aiChatRepository).should(times(1)).save(any());
        then(personaElementRepository).should(times(1)).save(any());
        then(bedrockService).should(never()).converse(anyString());
    }

    @Test
    @DisplayName("이미 답한 중간 턴에 같은 요청이 다시 오면 저장된 다음 질문을 그대로 돌려주고 아무것도 저장하지 않는다 (멱등)")
    void aiChatMessage_answered_middle_turn_is_idempotent() {
        // given: 0번 턴 질문·답변과 1번 턴 AI 질문이 이미 저장됨
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 0, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.AI, "요즘 뭐 하고 지내?", 0, NOW)));
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 0, AiChatSender.USER))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.USER, "한강에 자주 가", 0, NOW)));
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 1, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.AI, "그럼 거기서 뭘 주로 해?", 1, NOW)));

        // when: 0번 턴에 같은 답변을 다시 전송
        MeAiChatMessageResult result = userAiChatService.aiChatMessage(ME, new MeAiChatMessageCommand("한강에 자주 가", 0));

        // then: 저장된 1번 턴 질문을 반환하고 모델 호출·저장 없음
        assertThat(result).isEqualTo(new MeAiChatMessageResult("그럼 거기서 뭘 주로 해?", 1, false));
        then(bedrockService).should(never()).converse(anyString());
        then(aiChatRepository).should(never()).save(any());
        then(personaElementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 답한 마지막 턴(7)에 같은 요청이 다시 오면 종료 응답만 반환하고 아무것도 저장하지 않는다 (멱등)")
    void aiChatMessage_answered_last_turn_is_idempotent() {
        // given: 7번 턴 질문·답변이 모두 저장됨
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 7, AiChatSender.AI))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.AI, "마지막 질문", 7, NOW)));
        given(aiChatRepository.findByUserIdAndTurnIndexAndSender(ME, 7, AiChatSender.USER))
                .willReturn(Optional.of(AiChat.create(ME, AiChatSender.USER, "재밌었어", 7, NOW)));

        // when: 7번 턴에 같은 답변을 다시 전송
        MeAiChatMessageResult result = userAiChatService.aiChatMessage(ME, new MeAiChatMessageCommand("재밌었어", 7));

        // then: 종료 응답만 반환하고 모델 호출·저장 없음
        assertThat(result).isEqualTo(new MeAiChatMessageResult("지금까지 이야기 들려줘서 고마워!", 7, true));
        then(bedrockService).should(never()).converse(anyString());
        then(aiChatRepository).should(never()).save(any());
        then(personaElementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("처음 AI 대화를 종료하면 현재 시각으로 종료 시각을 기록하고 AI 대화 종료 이벤트를 발행한다")
    void aiChatComplete_first_time_marks_and_publishes_event() {
        // given: 종료 시각이 아직 없어 1행이 갱신됨
        Instant before = Instant.now();
        given(userRepository.markAiChatCompleted(eq(ME), any())).willReturn(1);

        // when: AI 대화 종료
        userAiChatService.aiChatComplete(ME);

        // then: 호출 시점의 시각으로 기록을 위임하고, 유저 id로 종료 이벤트를 발행
        ArgumentCaptor<Instant> completedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        then(userRepository).should().markAiChatCompleted(eq(ME), completedAtCaptor.capture());
        assertThat(completedAtCaptor.getValue()).isBetween(before, Instant.now());
        then(eventPublisher).should().publishEvent(new AiChatCompletedEvent(ME));
    }

    @Test
    @DisplayName("이미 종료한 AI 대화를 다시 종료하면 예외 없이 끝나고 종료 이벤트를 다시 발행하지 않는다 (멱등)")
    void aiChatComplete_already_completed_does_not_publish() {
        // given: 이미 종료 시각이 기록돼 있어 갱신된 행이 없음
        given(userRepository.markAiChatCompleted(eq(ME), any())).willReturn(0);

        // when: AI 대화 다시 종료
        userAiChatService.aiChatComplete(ME);

        // then: 이벤트를 발행하지 않음 (요약이 두 번 만들어지지 않도록)
        then(eventPublisher).should(never()).publishEvent(any());
    }

    private User user() {
        return User.create(
                "nick", "홍", "familyHash", "길동", "givenHash",
                Gender.MALE, "organization", "organizationHash", "니두스", "affHash", "2020123", "affNoHash",
                "2000-01-01", "birthHash", "01000000000", "phoneHash", "me@test.com", "emailHash", null, null, null, null);
    }
}
