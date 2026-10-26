package com.nidus.twinly.aichat.service;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.entity.TwinUpdateAiChat;
import com.nidus.twinly.aichat.prompt.AiChatPromptBuilder;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.aichat.repository.TwinUpdateAiChatRepository;
import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.me.dto.command.MeSendTwinUpdateAiChatMessageCommand;
import com.nidus.twinly.me.dto.command.MeStartTwinUpdateAiChatCommand;
import com.nidus.twinly.me.dto.result.MeAiChatMessageResult;
import com.nidus.twinly.me.dto.result.MeAiChatStartResult;
import com.nidus.twinly.twinupdate.entity.TwinUpdate;
import com.nidus.twinly.twinupdate.service.TwinUpdateReader;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TwinUpdateAiChatServiceUnitTest {

    private static final Long ME = 1L;
    private static final Long TWIN_UPDATE_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-10-24T01:00:00Z");

    @Mock
    BedrockService bedrockService;

    @Spy
    AiChatPromptBuilder aiChatPromptBuilder = new AiChatPromptBuilder();

    @Mock
    TwinUpdateReader twinUpdateReader;

    @Mock
    UserRepository userRepository;

    @Mock
    AiChatRepository aiChatRepository;

    @Mock
    TwinUpdateAiChatRepository twinUpdateAiChatRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @InjectMocks
    TwinUpdateAiChatService twinUpdateAiChatService;

    TwinUpdate twinUpdate;
    UUID updateId;

    @BeforeEach
    void setUp() {
        twinUpdate = TwinUpdate.create(ME);
        ReflectionTestUtils.setField(twinUpdate, "id", TWIN_UPDATE_ID);
        updateId = twinUpdate.getUpdateId();
        given(twinUpdateReader.lockInProgress(ME, updateId)).willReturn(twinUpdate);
    }

    @Test
    @DisplayName("대화 시작: 이 회차의 0턴 질문이 이미 있으면 모델을 다시 부르지 않고 그 질문을 돌려준다")
    void aiChatStart_returns_existing_question() {
        // given: 0턴 AI 질문이 저장돼 있다
        given(twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(TWIN_UPDATE_ID, 0, AiChatSender.AI))
                .willReturn(Optional.of(TwinUpdateAiChat.create(TWIN_UPDATE_ID, AiChatSender.AI, "요리는 주로 뭘 해?", 0)));

        // when: 대화 시작
        MeAiChatStartResult result = twinUpdateAiChatService.aiChatStart(ME, new MeStartTwinUpdateAiChatCommand(updateId));

        // then: 저장된 질문 그대로 + 모델 호출·저장 없음
        assertThat(result).isEqualTo(new MeAiChatStartResult("요리는 주로 뭘 해?", 0, false));
        then(bedrockService).shouldHaveNoInteractions();
        then(twinUpdateAiChatRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("대화 시작: 새 관심사를 첫 주제로 지정하고, 이번 회차 관심사와 이전 질문들을 프롬프트에 넣은 뒤 0턴 질문을 회차에 저장한다")
    void aiChatStart_focuses_new_interest() {
        // given: 기존 관심사 '등산', 이번 회차 관심사 '등산'·'요리', 처음 생성과 지난 업데이트의 질문이 있다
        givenUser();
        given(twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(TWIN_UPDATE_ID, 0, AiChatSender.AI)).willReturn(Optional.empty());
        given(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(ME, PersonaDimension.INTEREST))
                .willReturn(List.of(PersonaElement.create(ME, PersonaDimension.INTEREST, "등산", NOW)));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(ME))
                .willReturn(List.of(PersonaElement.create(ME, PersonaDimension.INTEREST, "등산", NOW)));
        given(twinUpdateReader.interests(TWIN_UPDATE_ID)).willReturn(List.of("등산", "요리"));
        given(aiChatRepository.findByUserIdOrderByTurnIndexAscSenderDesc(ME))
                .willReturn(List.of(AiChat.create(ME, AiChatSender.AI, "처음 질문", 0, NOW)));
        given(twinUpdateAiChatRepository.findCompletedQuestionsByUserId(ME)).willReturn(List.of("지난 업데이트 질문"));
        given(bedrockService.converse(anyString())).willReturn("요리는 주로 뭘 해?");

        // when: 대화 시작
        MeAiChatStartResult result = twinUpdateAiChatService.aiChatStart(ME, new MeStartTwinUpdateAiChatCommand(updateId));

        // then: 새 관심사 '요리' 지정 + 이전 질문 포함 + 기존 관심사는 한 번만(회차 목록으로 대체)
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("'요리' 관심사에 대한 질문")
                .contains("- 처음 질문")
                .contains("- 지난 업데이트 질문")
                .containsOnlyOnce("- 등산");

        // then: 0턴 AI 질문을 이 회차에 저장
        ArgumentCaptor<TwinUpdateAiChat> chatCaptor = ArgumentCaptor.forClass(TwinUpdateAiChat.class);
        then(twinUpdateAiChatRepository).should().save(chatCaptor.capture());
        assertThat(chatCaptor.getValue().getTwinUpdateId()).isEqualTo(TWIN_UPDATE_ID);
        assertThat(chatCaptor.getValue().getSender()).isEqualTo(AiChatSender.AI);
        assertThat(chatCaptor.getValue().getTurnIndex()).isZero();
        assertThat(result).isEqualTo(new MeAiChatStartResult("요리는 주로 뭘 해?", 0, false));
    }

    @Test
    @DisplayName("메시지: 해당 턴의 AI 질문이 없으면 AI_QUESTION_NOT_FOUND 예외가 발생한다")
    void aiChatMessage_without_question_throws() {
        // given: 3턴 질문 없음
        given(twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(TWIN_UPDATE_ID, 3, AiChatSender.AI)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> twinUpdateAiChatService.aiChatMessage(ME, new MeSendTwinUpdateAiChatMessageCommand(updateId, "답", 3)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("메시지: 이미 답한 턴을 다시 보내면 새 답을 버리고 저장된 다음 질문을 돌려준다 (멱등)")
    void aiChatMessage_already_answered_is_idempotent() {
        // given: 0턴 문답과 1턴 질문이 저장돼 있다
        givenChat(AiChatSender.AI, "요리는 주로 뭘 해?", 0);
        givenChat(AiChatSender.USER, "파스타", 0);
        givenChat(AiChatSender.AI, "제일 자신 있는 메뉴는?", 1);

        // when: 0턴 답을 다시 보냄
        MeAiChatMessageResult result = twinUpdateAiChatService.aiChatMessage(ME, new MeSendTwinUpdateAiChatMessageCommand(updateId, "라면", 0));

        // then: 저장된 1턴 질문 + 저장·모델 호출 없음
        assertThat(result).isEqualTo(new MeAiChatMessageResult("제일 자신 있는 메뉴는?", 1, false));
        then(twinUpdateAiChatRepository).should(never()).saveAndFlush(any());
        then(bedrockService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("메시지: 마지막 턴에 답하면 답만 저장하고 마무리 문구와 isEnd=true 를 돌려준다")
    void aiChatMessage_last_turn_ends() {
        // given: 7턴 질문만 있다
        givenChat(AiChatSender.AI, "마지막 질문", AiChatService.MAX_TURN_INDEX);
        given(twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(TWIN_UPDATE_ID, AiChatService.MAX_TURN_INDEX, AiChatSender.USER))
                .willReturn(Optional.empty());

        // when: 7턴 답
        MeAiChatMessageResult result = twinUpdateAiChatService.aiChatMessage(ME,
                new MeSendTwinUpdateAiChatMessageCommand(updateId, "마지막 답", AiChatService.MAX_TURN_INDEX));

        // then: 답 저장 + 마무리 + 모델 호출 없음
        then(twinUpdateAiChatRepository).should().saveAndFlush(any(TwinUpdateAiChat.class));
        assertThat(result).isEqualTo(new MeAiChatMessageResult(AiChatService.LAST_MESSAGE, AiChatService.MAX_TURN_INDEX, true));
        then(bedrockService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("메시지: 다음 턴이 새 주제(2턴)면 두 번째 새 관심사를 지정한다")
    void aiChatMessage_topic_turn_uses_next_new_interest() {
        // given: 기존 관심사 '등산', 이번 회차 '등산'·'요리'·'독서', 1턴 질문만 있다
        givenUser();
        givenChat(AiChatSender.AI, "제일 자신 있는 메뉴는?", 1);
        given(twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(TWIN_UPDATE_ID, 1, AiChatSender.USER)).willReturn(Optional.empty());
        given(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(ME, PersonaDimension.INTEREST))
                .willReturn(List.of(PersonaElement.create(ME, PersonaDimension.INTEREST, "등산", NOW)));
        given(twinUpdateReader.interests(TWIN_UPDATE_ID)).willReturn(List.of("등산", "요리", "독서"));
        given(bedrockService.converse(anyString())).willReturn("요즘 읽는 책은?");

        // when: 1턴 답
        MeAiChatMessageResult result = twinUpdateAiChatService.aiChatMessage(ME, new MeSendTwinUpdateAiChatMessageCommand(updateId, "까르보나라", 1));

        // then: 두 번째 새 관심사 '독서' 지정 + 2턴 질문 반환
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("'독서' 관심사에 대한 질문");
        assertThat(result).isEqualTo(new MeAiChatMessageResult("요즘 읽는 책은?", 2, false));
    }

    @Test
    @DisplayName("메시지: 다음 턴이 후속 질문(1턴)이면 방금 문답으로 후속 질문을 만들고, 실제 트윈에는 DETAIL 을 저장하지 않는다")
    void aiChatMessage_follow_up_does_not_touch_persona() {
        // given: 0턴 질문만 있다
        givenUser();
        givenChat(AiChatSender.AI, "요리는 주로 뭘 해?", 0);
        given(twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(TWIN_UPDATE_ID, 0, AiChatSender.USER)).willReturn(Optional.empty());
        given(bedrockService.converse(anyString())).willReturn("제일 자신 있는 메뉴는?");

        // when: 0턴 답
        MeAiChatMessageResult result = twinUpdateAiChatService.aiChatMessage(ME, new MeSendTwinUpdateAiChatMessageCommand(updateId, "파스타", 0));

        // then: 후속 프롬프트에 방금 문답 포함 + 1턴 질문 반환 + 페르소나 저장 없음
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        then(bedrockService).should().converse(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("나의 질문: 요리는 주로 뭘 해?")
                .contains("사용자의 답변: 파스타");
        assertThat(result).isEqualTo(new MeAiChatMessageResult("제일 자신 있는 메뉴는?", 1, false));
        then(personaElementRepository).should(never()).save(any());
    }

    private void givenUser() {
        User user = mock(User.class);
        given(user.getAffiliation()).willReturn("트윈리대학교");
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
    }

    private void givenChat(AiChatSender sender, String message, int turnIndex) {
        given(twinUpdateAiChatRepository.findByTwinUpdateIdAndTurnIndexAndSender(TWIN_UPDATE_ID, turnIndex, sender))
                .willReturn(Optional.of(TwinUpdateAiChat.create(TWIN_UPDATE_ID, sender, message, turnIndex)));
    }
}
