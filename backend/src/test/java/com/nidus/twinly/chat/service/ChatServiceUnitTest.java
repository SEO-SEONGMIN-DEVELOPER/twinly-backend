package com.nidus.twinly.chat.service;

import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.domain.ChatSenderType;
import com.nidus.twinly.chat.dto.command.ChatReadMessagesCommand;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.result.ChatMessageItemResult;
import com.nidus.twinly.chat.dto.result.ChatMessagesResult;
import com.nidus.twinly.chat.dto.result.ChatReadMessagesResult;
import com.nidus.twinly.chat.dto.result.ChatRoomDetailResult;
import com.nidus.twinly.chat.dto.result.ChatRoomResult;
import com.nidus.twinly.chat.dto.result.ChatRoomsResult;
import com.nidus.twinly.chat.dto.result.ChatSendMessageResult;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.event.ChatMessageCreatedEvent;
import com.nidus.twinly.chat.event.ChatReadAdvancedEvent;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatServiceUnitTest {

    private static final Long CURRENT_SEASON_ID = 1L;
    private static final Long ME = 1L;
    private static final Long PARTNER = 2L;
    private static final Long ROOM_ID = 10L;
    private static final Long MATCH_ID = 100L;

    @Mock
    CloudFrontService cloudFrontService;

    @Mock
    UserRepository userRepository;

    @Mock
    ChatRoomRepository chatRoomRepository;

    @Mock
    MatchRepository matchRepository;

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatRoomParticipationRepository chatRoomParticipationRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    DisclosureAgreementRepository disclosureAgreementRepository;

    @Mock
    BlockRepository blockRepository;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    AppNotificationFeedWriter appNotificationFeedWriter;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ChatService chatService;

    @Test
    @DisplayName("메시지 전송 정상: 수신자를 매칭 상대로 채워 Chat을 저장하고 생성 이벤트를 발행한다")
    void sendMessage_saves_chat_and_publishes_event() {
        // given: 내가 참여한 매칭의 채팅방이 존재하고, 같은 clientMsgId로 보낸 이력이 없음
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(participation(ROOM_ID, ME), participation(ROOM_ID, PARTNER)));
        given(chatRepository.findBySenderUserIdAndClientMsgId(ME, "client-1")).willReturn(Optional.empty());

        // when: 메시지를 전송
        ChatSendMessageResult result = chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1"));

        // then: roomId/발신자/수신자/타입/본문이 채워진 Chat이 저장되고 생성 이벤트가 발행됨
        ArgumentCaptor<Chat> captor = ArgumentCaptor.forClass(Chat.class);
        then(chatRepository).should().save(captor.capture());
        Chat saved = captor.getValue();
        assertThat(saved.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(saved.getSenderUserId()).isEqualTo(ME);
        assertThat(saved.getReceiverUserId()).isEqualTo(PARTNER);
        assertThat(saved.getType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(saved.getMessage()).isEqualTo("hello");
        assertThat(saved.getSentAt()).isNotNull();

        then(eventPublisher).should().publishEvent(any(ChatMessageCreatedEvent.class));
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.clientMsgId()).isEqualTo("client-1");
    }

    @Test
    @DisplayName("메시지 본문이 4KB를 초과하면 MESSAGE_LENGTH_EXCEEDED 예외가 발생하고 저장하지 않는다")
    void sendMessage_text_over_limit_throws() {
        // given: 내가 참여 중인 방에 4KB를 1바이트 초과하는 본문
        String tooLong = "a".repeat(4 * 1024 + 1);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(participation(ROOM_ID, ME), participation(ROOM_ID, PARTNER)));

        // when & then: 상한 초과 예외 발생 + 저장/이벤트 발행 없음
        assertThatThrownBy(() -> chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand(tooLong, "client-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_LENGTH_EXCEEDED);

        then(chatRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(ChatMessageCreatedEvent.class));
    }

    @Test
    @DisplayName("없는 방에 상한 초과 본문을 보내면 길이가 아니라 ROOM_NOT_FOUND로 응답한다")
    void sendMessage_over_limit_to_unknown_room_throws_room_not_found() {
        // given: 존재하지 않는 방 + 4KB 초과 본문
        String tooLong = "a".repeat(4 * 1024 + 1);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        // when & then: 길이 검사가 인가보다 앞서면 422가 나가 방 존재 여부가 유추된다
        assertThatThrownBy(() -> chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand(tooLong, "client-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 clientMsgId로 같은 방·같은 본문을 재전송하면 저장하지 않고 기존 메시지를 그대로 반환한다 (멱등)")
    void sendMessage_same_clientMsgId_is_idempotent() {
        // given: 같은 clientMsgId로 같은 방에 같은 본문을 보낸 이력이 있음
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(participation(ROOM_ID, ME), participation(ROOM_ID, PARTNER)));
        Chat sent = chat(55L, ROOM_ID, ME, PARTNER, "hello", "client-1");
        given(chatRepository.findBySenderUserIdAndClientMsgId(ME, "client-1")).willReturn(Optional.of(sent));

        // when: 동일한 커맨드로 재전송
        ChatSendMessageResult result = chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1"));

        // then: 기존 메시지가 반환되고 저장/이벤트 발행은 일어나지 않음
        assertThat(result.messageId()).isEqualTo(55L);
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.clientMsgId()).isEqualTo("client-1");
        then(chatRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(ChatMessageCreatedEvent.class));
    }

    @Test
    @DisplayName("같은 clientMsgId를 다른 본문으로 재사용하면 CLIENT_MSG_ID_CONFLICT 예외가 발생한다")
    void sendMessage_same_clientMsgId_different_text_throws_conflict() {
        // given: 같은 clientMsgId가 다른 본문으로 이미 사용됨
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(participation(ROOM_ID, ME), participation(ROOM_ID, PARTNER)));
        given(chatRepository.findBySenderUserIdAndClientMsgId(ME, "client-1"))
                .willReturn(Optional.of(chat(55L, ROOM_ID, ME, PARTNER, "bye", "client-1")));

        // when & then: 충돌 예외 발생 + 저장 없음
        assertThatThrownBy(() -> chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_MSG_ID_CONFLICT);

        then(chatRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("매칭 참여자가 아닌 유저가 메시지를 보내면 NOT_MATCH_PARTICIPANT 예외가 발생한다")
    void sendMessage_not_participant_throws() {
        // given: 매칭 당사자가 5번·6번 유저인 방
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, 5L, 6L, CURRENT_SEASON_ID)));

        // when & then: 제3자가 전송하면 참여자 아님 예외 발생 + 저장 없음
        assertThatThrownBy(() -> chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_MATCH_PARTICIPANT);

        then(chatRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("방을 나간 유저가 메시지를 보내면 NOT_ACTIVE_ROOM_PARTICIPANT 예외가 발생하고 저장하지 않는다")
    void sendMessage_left_participant_throws() {
        // given: 내가 이미 나간 방
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.leave();
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(mine, participation(ROOM_ID, PARTNER)));

        // when & then: 활성 참여자가 아님 예외 발생 + 저장/이벤트 발행 없음
        assertThatThrownBy(() -> chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ACTIVE_ROOM_PARTICIPANT);

        then(chatRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(ChatMessageCreatedEvent.class));
    }

    @Test
    @DisplayName("종료된 방에 메시지를 보내면 ROOM_CLOSED 예외가 발생하고 저장하지 않는다")
    void sendMessage_closed_room_throws() {
        // given: 종료된 방이지만 나는 여전히 활성 참여자다
        ChatRoom closed = room(ROOM_ID, MATCH_ID);
        closed.close("SEASON_ENDED");
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(closed));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(participation(ROOM_ID, ME), participation(ROOM_ID, PARTNER)));

        // when & then: 종료 상태로 거절되고 저장·이벤트 발행이 없다
        assertThatThrownBy(() -> chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_CLOSED);

        then(chatRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(ChatMessageCreatedEvent.class));
    }

    @Test
    @DisplayName("방을 숨긴 유저가 메시지를 보내면 NOT_ACTIVE_ROOM_PARTICIPANT 예외가 발생한다")
    void sendMessage_hidden_participant_throws() {
        // given: 내가 숨긴 방
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.hide();
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(mine, participation(ROOM_ID, PARTNER)));

        // when & then: 활성 참여자가 아님 예외 발생 + 저장 없음
        assertThatThrownBy(() -> chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ACTIVE_ROOM_PARTICIPANT);

        then(chatRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("상대가 방을 나갔으면 메시지는 저장하되 발행 대상에서 상대를 제외한다")
    void sendMessage_excludes_inactive_partner_from_publish_targets() {
        // given: 상대는 방을 나갔고 나는 활성 상태
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        ChatRoomParticipation partner = participation(ROOM_ID, PARTNER);
        partner.leave();
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID))
                .willReturn(List.of(participation(ROOM_ID, ME), partner));
        given(chatRepository.findBySenderUserIdAndClientMsgId(ME, "client-1")).willReturn(Optional.empty());

        // when: 메시지를 전송
        chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1"));

        // then: 메시지는 저장되지만 발행 대상은 나 하나뿐
        then(chatRepository).should().save(any(Chat.class));

        ArgumentCaptor<ChatMessageCreatedEvent> captor = ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().participantUserIds()).containsExactly(ME);
    }

    @Test
    @DisplayName("방을 나간 유저가 읽음 처리하면 NOT_ACTIVE_ROOM_PARTICIPANT 예외가 발생하고 포인터를 전진시키지 않는다")
    void readMessages_left_participant_throws() {
        // given: 내가 이미 나간 방
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.leave();
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        // when & then: 활성 참여자가 아님 예외 발생 + 포인터 전진 없음
        assertThatThrownBy(() -> chatService.readMessages(ME, ROOM_ID, new ChatReadMessagesCommand(77L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ACTIVE_ROOM_PARTICIPANT);

        then(chatRoomParticipationRepository).should(never()).advanceReadPointer(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 채팅방에 메시지를 보내면 ROOM_NOT_FOUND 예외가 발생한다")
    void sendMessage_room_not_found_throws() {
        // given: 채팅방이 존재하지 않음
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        // when & then: 방 없음 예외 발생
        assertThatThrownBy(() -> chatService.sendMessage(ME, ROOM_ID, new ChatSendMessageCommand("hello", "client-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("메시지 목록은 limit 미지정 시 기본 20건을 반환하고, 한 건 더 조회되면 hasMore와 다음 커서를 채운다")
    void messages_default_limit_and_paging_boundary() {
        // given: 기본 limit(20)보다 한 건 많은 21건이 조회됨 (id 21 → 1 내림차순)
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        List<Chat> chats = new ArrayList<>();
        for (long id = 21; id >= 1; id--) {
            chats.add(chat(id, ROOM_ID, ME, PARTNER, "m" + id, "client-" + id));
        }
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME))
                .willReturn(Optional.of(participation(ROOM_ID, ME)));
        given(chatRepository.findBeforeCursorByRoomId(ROOM_ID, null, 21)).willReturn(chats);

        // when: limit 없이 메시지 목록 조회
        ChatMessagesResult result = chatService.messages(ME, ROOM_ID, null, null);

        // then: 20건만 반환하고 hasMore=true, 다음 커서는 마지막 항목의 id
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.messages()).hasSize(20);
        assertThat(result.page().hasMore()).isTrue();
        assertThat(result.page().nextCursor()).isEqualTo(2L);
    }

    @Test
    @DisplayName("메시지 목록은 내가 보낸 메시지를 ME, 상대가 보낸 메시지를 THEM으로 매핑하고 더 없으면 커서를 비운다")
    void messages_maps_sender_type_and_last_page() {
        // given: 내가 보낸 1건 + 상대가 보낸 1건만 존재
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME))
                .willReturn(Optional.of(participation(ROOM_ID, ME)));
        given(chatRepository.findBeforeCursorByRoomId(ROOM_ID, null, 11)).willReturn(List.of(
                chat(2L, ROOM_ID, ME, PARTNER, "mine", "client-2"),
                chat(1L, ROOM_ID, PARTNER, ME, "theirs", "client-1")
        ));

        // when: limit 10으로 메시지 목록 조회
        ChatMessagesResult result = chatService.messages(ME, ROOM_ID, null, 10);

        // then: 발신자 타입이 ME/THEM으로 매핑되고 다음 페이지가 없음
        assertThat(result.messages()).extracting(ChatMessageItemResult::senderType)
                .containsExactly(ChatSenderType.ME, ChatSenderType.THEM);
        assertThat(result.page().hasMore()).isFalse();
        assertThat(result.page().nextCursor()).isNull();
    }

    @Test
    @DisplayName("메시지 목록의 lastReadMessageId는 내 읽음 위치가 아니라 상대의 읽음 위치를 내려준다")
    void messages_returns_partner_last_read_message_id() {
        // given: 나는 100까지 읽었고 상대는 55까지 읽은 상태
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));

        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        ReflectionTestUtils.setField(mine, "lastReadMessageId", 100L);
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        ChatRoomParticipation theirs = participation(ROOM_ID, PARTNER);
        ReflectionTestUtils.setField(theirs, "lastReadMessageId", 55L);
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, PARTNER)).willReturn(Optional.of(theirs));

        given(chatRepository.findBeforeCursorByRoomId(ROOM_ID, null, 11)).willReturn(List.of());

        // when: 메시지 목록 조회
        ChatMessagesResult result = chatService.messages(ME, ROOM_ID, null, 10);

        // then: 상대가 읽은 위치인 55가 나감 (내 위치 100이 아님)
        assertThat(result.lastReadMessageId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("상대가 아직 아무것도 읽지 않았으면 메시지 목록의 lastReadMessageId는 null이다")
    void messages_returns_null_when_partner_never_read() {
        // given: 상대 참여 정보에 읽음 이력이 없음
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME))
                .willReturn(Optional.of(participation(ROOM_ID, ME)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, PARTNER))
                .willReturn(Optional.of(participation(ROOM_ID, PARTNER)));
        given(chatRepository.findBeforeCursorByRoomId(ROOM_ID, null, 11)).willReturn(List.of());

        // when: 메시지 목록 조회
        ChatMessagesResult result = chatService.messages(ME, ROOM_ID, null, 10);

        // then: 읽음 표시를 그릴 수 없는 상태를 null로 구분해 내려줌
        assertThat(result.lastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("읽음 처리 정상: 읽음 포인터 전진을 위임하고 확정된 마지막 메시지 id를 반환한다")
    void readMessages_advances_pointer() {
        // given: 읽음 이력이 없는 참여 정보와 해당 방에 존재하는 메시지
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRepository.existsByIdAndRoomId(77L, ROOM_ID)).willReturn(true);
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME))
                .willReturn(Optional.of(participation(ROOM_ID, ME)));
        given(chatRoomParticipationRepository.advanceReadPointer(ROOM_ID, ME, 77L)).willReturn(1);

        // when: 77번 메시지까지 읽음 처리
        ChatReadMessagesResult result = chatService.readMessages(ME, ROOM_ID, new ChatReadMessagesCommand(77L));

        // then: 조건부 UPDATE가 실제로 갱신했으므로 요청값이 그대로 확정값이 되고, 포인터를 되읽지 않는다
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.lastMessageId()).isEqualTo(77L);
        then(chatRoomParticipationRepository).should().findByRoomIdAndUserId(ROOM_ID, ME);
    }

    @Test
    @DisplayName("이미 더 뒤까지 읽었으면 갱신 건수 0을 보고 DB의 현재 포인터를 다시 읽어 확정값으로 반환한다")
    void readMessages_does_not_regress_pointer() {
        // given: 조건부 UPDATE가 아무 행도 바꾸지 못하고, DB의 현재 포인터는 100
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRepository.existsByIdAndRoomId(77L, ROOM_ID)).willReturn(true);
        ChatRoomParticipation participation = participation(ROOM_ID, ME);
        ReflectionTestUtils.setField(participation, "lastReadMessageId", 100L);
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(participation));
        given(chatRoomParticipationRepository.advanceReadPointer(ROOM_ID, ME, 77L)).willReturn(0);

        // when: 더 앞선 77번으로 읽음 처리 요청
        ChatReadMessagesResult result = chatService.readMessages(ME, ROOM_ID, new ChatReadMessagesCommand(77L));

        // then: UPDATE 이전에 읽어둔 값이 아니라 DB를 다시 읽은 값으로 확정한다 (동시 요청 시 역행 방지)
        assertThat(result.lastMessageId()).isEqualTo(100L);

        // then: 위치가 실제로 이동하지 않았으므로 상대에게 알리지 않는다 (재전송 시 상대 화면이 흔들리지 않도록)
        then(eventPublisher).should(never()).publishEvent(any(ChatReadAdvancedEvent.class));
    }

    @Test
    @DisplayName("갱신 건수가 0인데 읽음 이력도 없으면 참여 정보 없음으로 오판하지 않고 null을 확정값으로 반환한다")
    void readMessages_when_not_advanced_and_never_read_returns_null() {
        // given: 갱신은 0건인데 포인터가 아직 비어 있는 상태 (동시 요청이 겹친 순간)
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRepository.existsByIdAndRoomId(77L, ROOM_ID)).willReturn(true);
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME))
                .willReturn(Optional.of(participation(ROOM_ID, ME)));
        given(chatRoomParticipationRepository.advanceReadPointer(ROOM_ID, ME, 77L)).willReturn(0);

        // when: 읽음 처리
        ChatReadMessagesResult result = chatService.readMessages(ME, ROOM_ID, new ChatReadMessagesCommand(77L));

        // then: 포인터가 비어 있는 것과 참여 정보가 없는 것을 구분해, 예외 대신 null을 돌려준다
        assertThat(result.lastMessageId()).isNull();
    }

    @Test
    @DisplayName("읽음 위치가 전진하면 읽은 본인을 제외한 활성 참여자에게 알릴 이벤트를 발행한다")
    void readMessages_publishes_read_advanced_event_to_partner_only() {
        // given: 읽음 이력이 없는 내 참여 정보와 활성 상태인 두 참여자
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRepository.existsByIdAndRoomId(77L, ROOM_ID)).willReturn(true);
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME))
                .willReturn(Optional.of(participation(ROOM_ID, ME)));
        given(chatRoomParticipationRepository.advanceReadPointer(ROOM_ID, ME, 77L)).willReturn(1);
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID)).willReturn(List.of(
                participation(ROOM_ID, ME),
                participation(ROOM_ID, PARTNER)
        ));

        // when: 77번 메시지까지 읽음 처리
        chatService.readMessages(ME, ROOM_ID, new ChatReadMessagesCommand(77L));

        // then: 상대만 대상으로 확정된 읽음 위치가 담긴 이벤트가 발행됨
        ArgumentCaptor<ChatReadAdvancedEvent> captor = ArgumentCaptor.forClass(ChatReadAdvancedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());

        ChatReadAdvancedEvent event = captor.getValue();
        assertThat(event.roomId()).isEqualTo(ROOM_ID);
        assertThat(event.lastReadMessageId()).isEqualTo(77L);
        assertThat(event.targetUserIds()).containsExactly(PARTNER);
    }

    @Test
    @DisplayName("상대가 방을 나갔으면 읽음 위치가 전진해도 알릴 대상이 없어 이벤트를 발행하지 않는다")
    void readMessages_without_active_partner_does_not_publish() {
        // given: 상대가 이미 방을 나가 활성 참여자가 나뿐인 상태
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRepository.existsByIdAndRoomId(77L, ROOM_ID)).willReturn(true);
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME))
                .willReturn(Optional.of(participation(ROOM_ID, ME)));

        given(chatRoomParticipationRepository.advanceReadPointer(ROOM_ID, ME, 77L)).willReturn(1);

        ChatRoomParticipation left = participation(ROOM_ID, PARTNER);
        left.leave();
        given(chatRoomParticipationRepository.findAllByRoomId(ROOM_ID)).willReturn(List.of(
                participation(ROOM_ID, ME),
                left
        ));

        // when: 77번 메시지까지 읽음 처리
        chatService.readMessages(ME, ROOM_ID, new ChatReadMessagesCommand(77L));

        // then: 읽음 처리 자체는 성공하되 이벤트는 발행되지 않음
        then(chatRoomParticipationRepository).should().advanceReadPointer(ROOM_ID, ME, 77L);
        then(eventPublisher).should(never()).publishEvent(any(ChatReadAdvancedEvent.class));
    }

    @Test
    @DisplayName("해당 방에 없는 메시지 id로 읽음 처리하면 MESSAGE_NOT_IN_ROOM 예외가 발생하고 포인터를 전진시키지 않는다")
    void readMessages_message_not_in_room_throws() {
        // given: 요청한 메시지가 해당 방에 존재하지 않음
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME))
                .willReturn(Optional.of(participation(ROOM_ID, ME)));
        given(chatRepository.existsByIdAndRoomId(77L, ROOM_ID)).willReturn(false);

        // when & then: 방에 없는 메시지 예외 발생 + 포인터 전진 없음
        assertThatThrownBy(() -> chatService.readMessages(ME, ROOM_ID, new ChatReadMessagesCommand(77L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_IN_ROOM);

        then(chatRoomParticipationRepository).should(never()).advanceReadPointer(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("참여 정보가 없는 방에 읽음 처리하면 CHAT_PARTICIPATION_NOT_FOUND 예외가 발생한다")
    void readMessages_participation_not_found_throws() {
        // given: 방은 있으나 참여 정보가 없음
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.empty());

        // when & then: 참여 정보 없음 예외 발생
        assertThatThrownBy(() -> chatService.readMessages(ME, ROOM_ID, new ChatReadMessagesCommand(77L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND);
    }

    @Test
    @DisplayName("채팅방 입장은 참여 정보에 동의 시각을 남기고 입장 상태가 반영된 상세를 반환한다")
    void enterRoom_agrees_and_returns_detail() {
        // given: 아직 입장 동의하지 않은 내 참여 정보와 상대 정보
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, PARTNER)).willReturn(Optional.empty());
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "partnerNick")));
        given(photoRepository.findByUserIdAndType(PARTNER, PhotoType.PROFILE)).willReturn(Optional.empty());
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, PARTNER)).willReturn(Optional.empty());
        given(disclosureAgreementRepository.findAllByUserId(PARTNER)).willReturn(List.of());
        given(currentSeasonReader.read()).willReturn(currentSeason());

        // when: 채팅방에 입장
        ChatRoomDetailResult result = chatService.enterRoom(ME, ROOM_ID);

        // then: 동의 시각이 기록되고 응답의 입장 상태에 반영됨
        assertThat(mine.getEntryAgreedAt()).isNotNull();
        assertThat(result.entryStatus().myEntryAgreed()).isTrue();
        assertThat(result.entryStatus().partnerEntryAgreed()).isFalse();
        assertThat(result.partner().userId()).isEqualTo(PARTNER);
        assertThat(result.isCurrentSeason()).isTrue();

    }

    @Test
    @DisplayName("내 입장 동의로 양쪽 동의가 채워지면 입장 상태가 모두 동의로 반영된다")
    void enterRoom_both_agreed() {
        // given: 상대는 이미 동의했고 나는 아직 동의하지 않음
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        ChatRoomParticipation partner = participation(ROOM_ID, PARTNER);
        partner.agree();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, PARTNER)).willReturn(Optional.of(partner));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "partnerNick")));
        given(photoRepository.findByUserIdAndType(PARTNER, PhotoType.PROFILE)).willReturn(Optional.empty());
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, PARTNER)).willReturn(Optional.empty());
        given(disclosureAgreementRepository.findAllByUserId(PARTNER)).willReturn(List.of());
        given(currentSeasonReader.read()).willReturn(currentSeason());

        // when: 채팅방에 입장
        ChatRoomDetailResult result = chatService.enterRoom(ME, ROOM_ID);

        // then: 양쪽 동의가 상세 응답에 반영됨
        assertThat(result.entryStatus().myEntryAgreed()).isTrue();
        assertThat(result.entryStatus().partnerEntryAgreed()).isTrue();
    }

    @Test
    @DisplayName("방을 나간 유저가 채팅방 상세를 조회하면 NOT_ACTIVE_ROOM_PARTICIPANT 예외가 발생한다")
    void roomDetail_left_participant_throws() {
        // given: 내가 이미 나간 방
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "partnerNick")));
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.leave();
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        // when & then: 활성 참여자가 아님 예외 발생
        assertThatThrownBy(() -> chatService.roomDetail(ME, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ACTIVE_ROOM_PARTICIPANT);
    }

    @Test
    @DisplayName("방을 숨긴 유저가 메시지 목록을 조회하면 NOT_ACTIVE_ROOM_PARTICIPANT 예외가 발생하고 조회 쿼리를 타지 않는다")
    void messages_hidden_participant_throws() {
        // given: 내가 숨긴 방
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.hide();
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        // when & then: 활성 참여자가 아님 예외 발생 + 메시지 조회 없음
        assertThatThrownBy(() -> chatService.messages(ME, ROOM_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ACTIVE_ROOM_PARTICIPANT);

        then(chatRepository).should(never()).findBeforeCursorByRoomId(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("방을 나간 유저가 채팅방에 입장하면 NOT_ACTIVE_ROOM_PARTICIPANT 예외가 발생하고 동의 시각을 남기지 않는다")
    void enterRoom_left_participant_throws() {
        // given: 내가 이미 나간 방
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.leave();
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        // when & then: 활성 참여자가 아님 예외 발생 + 동의 시각 미기록
        assertThatThrownBy(() -> chatService.enterRoom(ME, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ACTIVE_ROOM_PARTICIPANT);

        assertThat(mine.getEntryAgreedAt()).isNull();
    }

    @Test
    @DisplayName("채팅방 숨김은 내 참여 정보의 숨김 플래그를 켠다")
    void hideRoom_sets_hidden_flag() {
        // given: 숨기지 않은 내 참여 정보
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        // when: 채팅방 숨김
        chatService.hideRoom(ME, ROOM_ID);

        // then: 숨김 플래그가 켜짐
        assertThat(mine.getIsHidden()).isTrue();
    }

    @Test
    @DisplayName("채팅방 나가기는 내 참여 정보에 나간 시각을 남긴다")
    void leaveRoom_sets_left_at() {
        // given: 아직 나가지 않은 내 참여 정보
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        // when: 채팅방 나가기
        chatService.leaveRoom(ME, ROOM_ID);

        // then: 나간 시각이 기록됨
        assertThat(mine.getLeftAt()).isNotNull();
    }

    @Test
    @DisplayName("채팅방 숨김은 이미 숨긴 방에 다시 호출해도 성공한다 (멱등)")
    void hideRoom_already_hidden_is_idempotent() {
        // given: 이미 숨긴 내 참여 정보
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.hide();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        // when & then: 활성 참여 검사를 끼우면 403이 되는 경로다. 재요청은 흔하므로 성공해야 한다
        chatService.hideRoom(ME, ROOM_ID);

        assertThat(mine.getIsHidden()).isTrue();
    }

    @Test
    @DisplayName("채팅방 나가기는 이미 나간 방에 다시 호출해도 성공한다 (멱등)")
    void leaveRoom_already_left_is_idempotent() {
        // given: 이미 나간 내 참여 정보
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.leave();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));

        // when & then: 활성 참여 검사를 끼우면 403이 되는 경로다. 재요청은 흔하므로 성공해야 한다
        chatService.leaveRoom(ME, ROOM_ID);

        assertThat(mine.getLeftAt()).isNotNull();
    }

    @Test
    @DisplayName("채팅방 숨김·나가기는 방은 있는데 참여 정보가 없으면 CHAT_PARTICIPATION_NOT_FOUND 예외가 발생한다")
    void hideRoom_and_leaveRoom_without_participation_throw() {
        // given: 방과 매칭은 있지만 내 참여 행이 없는 상태 (읽음 처리·입장과 같은 상황)
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.empty());

        // when & then: 500이 아니라 404 도메인 에러로 나가고, 다른 엔드포인트와 코드가 같다
        assertThatThrownBy(() -> chatService.hideRoom(ME, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND);

        assertThatThrownBy(() -> chatService.leaveRoom(ME, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND);
    }

    @Test
    @DisplayName("채팅방 목록은 내가 숨겼거나 나간 방을 제외하고 마지막 메시지·안읽음 수를 채워 반환한다")
    void rooms_excludes_hidden_and_left_rooms() {
        // given: 방 3개 중 하나는 숨김, 하나는 나간 상태
        given(currentSeasonReader.read()).willReturn(currentSeason());
        given(matchRepository.findAllByUserAIdOrUserBId(ME, ME)).willReturn(List.of(
                match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID),
                match(200L, ME, 3L, CURRENT_SEASON_ID),
                match(300L, ME, 4L, CURRENT_SEASON_ID)
        ));
        given(chatRoomRepository.findAllByMatchIdIn(List.of(MATCH_ID, 200L, 300L))).willReturn(List.of(
                room(ROOM_ID, MATCH_ID), room(20L, 200L), room(30L, 300L)
        ));

        ChatRoomParticipation visible = participation(ROOM_ID, ME);
        ChatRoomParticipation hidden = participation(20L, ME);
        hidden.hide();
        ChatRoomParticipation left = participation(30L, ME);
        left.leave();
        given(chatRoomParticipationRepository.findAllByRoomIdIn(List.of(ROOM_ID, 20L, 30L)))
                .willReturn(List.of(visible, hidden, left, participation(ROOM_ID, PARTNER)));

        given(userRepository.findAllById(List.of(PARTNER))).willReturn(List.of(user(PARTNER, "partnerNick")));
        given(photoRepository.findAllByUserIdInAndType(List.of(PARTNER), PhotoType.PROFILE)).willReturn(List.of());
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdIn(ME, List.of(PARTNER))).willReturn(List.of());
        given(chatRepository.findLatestByRoomIdIn(List.of(ROOM_ID)))
                .willReturn(List.of(chat(55L, ROOM_ID, PARTNER, ME, "hello", "client-55")));
        given(chatRepository.countUnreadByRoomIdIn(ME, List.of(ROOM_ID))).willReturn(List.of());

        // when: 채팅방 목록 조회
        ChatRoomsResult result = chatService.rooms(ME);

        // then: 숨김·나간 방은 빠지고 남은 방에 마지막 메시지와 안읽음 0이 채워짐
        assertThat(result.rooms()).hasSize(1);
        ChatRoomResult only = result.rooms().getFirst();
        assertThat(only.roomId()).isEqualTo(ROOM_ID);
        assertThat(only.partner().userId()).isEqualTo(PARTNER);
        assertThat(only.partner().intimacy()).isZero();
        assertThat(only.preview()).isEqualTo("hello");
        assertThat(only.messages().unreadCount()).isZero();
        assertThat(only.messages().lastMessage().text()).isEqualTo("hello");
        assertThat(only.isCurrentSeason()).isTrue();
    }

    @Test
    @DisplayName("채팅방 목록에서 내가 차단한 상대의 방은 제외된다")
    void rooms_excludes_blocked_partner_room() {
        // given: 활성 방 2개 중 상대 3L을 차단한 상태
        given(currentSeasonReader.read()).willReturn(currentSeason());
        given(matchRepository.findAllByUserAIdOrUserBId(ME, ME)).willReturn(List.of(
                match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID),
                match(200L, ME, 3L, CURRENT_SEASON_ID)
        ));
        given(chatRoomRepository.findAllByMatchIdIn(List.of(MATCH_ID, 200L)))
                .willReturn(List.of(room(ROOM_ID, MATCH_ID), room(20L, 200L)));
        given(chatRoomParticipationRepository.findAllByRoomIdIn(List.of(ROOM_ID, 20L)))
                .willReturn(List.of(participation(ROOM_ID, ME), participation(20L, ME)));
        given(blockRepository.findAllByUserId(ME)).willReturn(List.of(Block.create(ME, 3L)));

        given(userRepository.findAllById(List.of(PARTNER))).willReturn(List.of(user(PARTNER, "partnerNick")));
        given(photoRepository.findAllByUserIdInAndType(List.of(PARTNER), PhotoType.PROFILE)).willReturn(List.of());
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdIn(ME, List.of(PARTNER))).willReturn(List.of());
        given(chatRepository.findLatestByRoomIdIn(List.of(ROOM_ID))).willReturn(List.of());
        given(chatRepository.countUnreadByRoomIdIn(ME, List.of(ROOM_ID))).willReturn(List.of());

        // when: 채팅방 목록 조회
        ChatRoomsResult result = chatService.rooms(ME);

        // then: 차단한 상대의 방만 빠지고, 부가 정보도 남은 방 기준으로만 조회된다
        assertThat(result.rooms()).hasSize(1);
        assertThat(result.rooms().getFirst().partner().userId()).isEqualTo(PARTNER);
    }

    @Test
    @DisplayName("채팅방 상세는 상대가 공개 동의한 필드만 노출하고 친밀도로 관계 타입을 계산한다")
    void roomDetail_discloses_only_agreed_fields() {
        // given: 상대가 소속만 공개 동의했고 친밀도가 45인 관계
        given(currentSeasonReader.read()).willReturn(currentSeason());
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(user(PARTNER, "partnerNick")));
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.agree();
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, PARTNER)).willReturn(Optional.empty());
        given(photoRepository.findByUserIdAndType(PARTNER, PhotoType.PROFILE)).willReturn(Optional.empty());
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, PARTNER))
                .willReturn(Optional.of(relationship(ME, PARTNER, 45)));
        given(disclosureAgreementRepository.findAllByUserId(PARTNER))
                .willReturn(List.of(DisclosureAgreement.create(PARTNER, DisclosureField.AFFILIATION)));

        // when: 채팅방 상세 조회
        ChatRoomDetailResult result = chatService.roomDetail(ME, ROOM_ID);

        // then: 동의한 소속만 노출되고 소속번호는 가려지며 관계 타입이 친밀도로 계산됨
        assertThat(result.partner().disclosedFields().affiliation()).isEqualTo("aff");
        assertThat(result.partner().disclosedFields().affiliationNumber()).isNull();
        assertThat(result.partner().intimacy()).isEqualTo(45);
        assertThat(result.partner().relationshipSpecificType())
                .isEqualTo(RelationshipSpecificType.CLOSE);
        assertThat(result.entryStatus().myEntryAgreed()).isTrue();
        assertThat(result.entryStatus().partnerEntryAgreed()).isFalse();
    }

    @Test
    @DisplayName("채팅방 상세에서 탈퇴한 상대는 닉네임·사진·공개 필드를 모두 가리고 조회하지 않는다")
    void roomDetail_of_withdrawn_partner_is_masked() {
        // given: 탈퇴한 상대와의 채팅방
        given(currentSeasonReader.read()).willReturn(currentSeason());
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ROOM_ID, MATCH_ID)));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match(MATCH_ID, ME, PARTNER, CURRENT_SEASON_ID)));
        User withdrawn = user(PARTNER, "partnerNick");
        ReflectionTestUtils.setField(withdrawn, "deletedAt", Instant.now());
        given(userRepository.findById(PARTNER)).willReturn(Optional.of(withdrawn));
        ChatRoomParticipation mine = participation(ROOM_ID, ME);
        mine.agree();
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, ME)).willReturn(Optional.of(mine));
        given(chatRoomParticipationRepository.findByRoomIdAndUserId(ROOM_ID, PARTNER)).willReturn(Optional.empty());
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, PARTNER)).willReturn(Optional.empty());

        // when: 채팅방 상세 조회
        ChatRoomDetailResult result = chatService.roomDetail(ME, ROOM_ID);

        // then: 실명 도메인과 같은 문구로 닉네임까지 가려진다
        assertThat(result.partner().userName()).isEqualTo(User.WITHDRAWN_NAME);
        assertThat(result.partner().profilePhoto()).isNull();
        assertThat(result.partner().disclosedFields().affiliation()).isNull();

        // then: 가려질 값이므로 사진·공개 동의는 조회하지 않는다
        then(photoRepository).should(never()).findByUserIdAndType(eq(PARTNER), any());
        then(disclosureAgreementRepository).should(never()).findAllByUserId(PARTNER);
    }

    private ChatRoom room(Long id, Long matchId) {
        ChatRoom room = ChatRoom.create(matchId);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private Season currentSeason() {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", CURRENT_SEASON_ID);
        return season;
    }

    private Match match(Long id, Long userAId, Long userBId, Long seasonId) {
        Match match = BeanUtils.instantiateClass(Match.class);
        ReflectionTestUtils.setField(match, "id", id);
        ReflectionTestUtils.setField(match, "userAId", userAId);
        ReflectionTestUtils.setField(match, "userBId", userBId);
        ReflectionTestUtils.setField(match, "seasonId", seasonId);
        return match;
    }

    private Chat chat(Long id, Long roomId, Long senderUserId, Long receiverUserId, String message, String clientMsgId) {
        Chat chat = Chat.create(clientMsgId, roomId, senderUserId, receiverUserId, ChatMessageType.TEXT, message);
        ReflectionTestUtils.setField(chat, "id", id);
        return chat;
    }

    private ChatRoomParticipation participation(Long roomId, Long userId) {
        return ChatRoomParticipation.create(roomId, userId);
    }

    private Relationship relationship(Long userId, Long partnerUserId, Integer intimacy) {
        Relationship relationship = BeanUtils.instantiateClass(Relationship.class);
        ReflectionTestUtils.setField(relationship, "userId", userId);
        ReflectionTestUtils.setField(relationship, "partnerUserId", partnerUserId);
        ReflectionTestUtils.setField(relationship, "intimacy", intimacy);
        return relationship;
    }

    private User user(Long id, String nickname) {
        User user = User.create(
                nickname,
                "family", "familyHash",
                "given", "givenHash",
                Gender.MALE,
                "organization", "organizationHash",
                "aff", "affHash",
                "affNo", "affNoHash",
                "2000-01-01", "birthHash",
                "phone", "phoneHash",
                "email", "emailHash"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
