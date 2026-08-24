package com.nidus.twinly.chat.integration;

import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketRequestBody;
import com.nidus.twinly.common.websocket.dto.WebSocketCommandResultBody;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.chat.dto.websocket.ChatMessageSendPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadAdvancePayload;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.support.AbstractWebSocketIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ChatWebSocketIntegrationTest extends AbstractWebSocketIntegrationTest {

    /** matches.season_id FK를 만족시키기 위한 시즌 id (sendMessage 로직 자체는 시즌을 안 씀). */
    private static final long SEASON_ID = 1L;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatRoomRepository chatRoomRepository;

    @Autowired
    ChatRoomParticipationRepository chatRoomParticipationRepository;

    @Autowired
    MatchRepository matchRepository;

    @Autowired
    ConnectionTicketRepository connectionTicketRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // @Transactional 롤백을 못 쓰므로 커밋된 픽스처를 FK 안전 순서로 수동 정리한다.
        // participations.last_read_message_id 가 chats(id)를 참조하므로 참여 정보를 먼저 지운다.
        chatRoomParticipationRepository.deleteAll();
        chatRepository.deleteAll();
        chatRoomRepository.deleteAll();
        matchRepository.deleteAll();
        connectionTicketRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM seasons");
    }

    @Test
    @DisplayName("메시지 전송 e2e: 실제 STOMP 연결→/app 전송→committed·created 프레임 수신·DB 저장까지 관통한다")
    void sendMessage_end_to_end() throws Exception {
        // given: 시즌·유저 2명·매치·채팅방을 실제 DB에 커밋 (핸들러 스레드가 봐야 하므로 커밋 필수)
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();
        long matchId = saveMatch(me.getId(), partner.getId());
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), me.getId()));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), partner.getId()));
        UUID ticket = issueWsTicket(me.getId());

        // when: 티켓으로 STOMP 연결 → 두 개인 큐 구독 → /app/chat/messages 로 명령 전송
        StompSession session = connect(ticket);
        BlockingQueue<WebSocketCommandResultBody> commandQueue =
                subscribe(session, "/user/queue/chat/commands", WebSocketCommandResultBody.class);
        BlockingQueue<WebSocketEventBody> roomQueue =
                subscribe(session, "/user/queue/chat/rooms/" + room.getId(), WebSocketEventBody.class);

        session.send("/app/chat/messages", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_MESSAGE_SEND, "command-1",
                new ChatMessageSendPayload(room.getId(), "cmid-1", "안녕")));

        // then: 개인 큐로 committed 결과 프레임 수신 (같은 commandId)
        WebSocketCommandResultBody committed = commandQueue.poll(5, TimeUnit.SECONDS);
        assertThat(committed).isNotNull();
        assertThat(committed.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_COMMITTED);
        assertThat(committed.commandId()).isEqualTo("command-1");

        // then: 방 브로드캐스트(created) 프레임 수신 (발신자도 참여자라 자신에게도 전달됨)
        WebSocketEventBody created = roomQueue.poll(5, TimeUnit.SECONDS);
        assertThat(created).isNotNull();
        assertThat(created.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_CREATED);

        // then: DB에 실제로 채팅이 저장되고 수신자가 매칭 상대로 채워짐
        Chat saved = chatRepository.findBySenderUserIdAndClientMsgId(me.getId(), "cmid-1").orElseThrow();
        assertThat(saved.getRoomId()).isEqualTo(room.getId());
        assertThat(saved.getReceiverUserId()).isEqualTo(partner.getId());
        assertThat(saved.getMessage()).isEqualTo("안녕");

        session.disconnect();
    }

    @Test
    @DisplayName("메시지 전송 e2e: created 페이로드의 clientMsgId 는 발신자에게만 있고 상대에게는 키 자체가 내려가지 않는다")
    void createdEvent_exposes_clientMsgId_only_to_sender() throws Exception {
        // given: 두 유저가 참여 중인 방, 각자 STOMP 로 접속해 같은 방 큐를 구독
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();
        long matchId = saveMatch(me.getId(), partner.getId());
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), me.getId()));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), partner.getId()));

        StompSession mySession = connect(issueWsTicket(me.getId()));
        StompSession partnerSession = connect(issueWsTicket(partner.getId()));
        BlockingQueue<WebSocketEventBody> myRoomQueue =
                subscribe(mySession, "/user/queue/chat/rooms/" + room.getId(), WebSocketEventBody.class);
        BlockingQueue<WebSocketEventBody> partnerRoomQueue =
                subscribe(partnerSession, "/user/queue/chat/rooms/" + room.getId(), WebSocketEventBody.class);

        // when: 내가 메시지를 전송
        mySession.send("/app/chat/messages", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_MESSAGE_SEND, "command-1",
                new ChatMessageSendPayload(room.getId(), "cmid-1", "안녕")));

        // then: 양쪽 모두 created 이벤트를 받는다
        WebSocketEventBody mine = myRoomQueue.poll(5, TimeUnit.SECONDS);
        WebSocketEventBody theirs = partnerRoomQueue.poll(5, TimeUnit.SECONDS);
        assertThat(mine).isNotNull();
        assertThat(theirs).isNotNull();

        // then: 발신자 프레임은 me + clientMsgId, 상대 프레임은 them + clientMsgId 키 없음 (null 이 아니라 부재)
        @SuppressWarnings("unchecked")
        Map<String, Object> myMessage = (Map<String, Object>) ((Map<String, Object>) mine.payload()).get("message");
        @SuppressWarnings("unchecked")
        Map<String, Object> theirMessage = (Map<String, Object>) ((Map<String, Object>) theirs.payload()).get("message");
        assertThat(myMessage.get("senderType")).isEqualTo("me");
        assertThat(myMessage.get("clientMsgId")).isEqualTo("cmid-1");
        assertThat(theirMessage.get("senderType")).isEqualTo("them");
        assertThat(theirMessage).doesNotContainKey("clientMsgId");

        mySession.disconnect();
        partnerSession.disconnect();
    }

    @Test
    @DisplayName("읽음 처리 e2e: /app/chat/read 전송→read committed 프레임 수신·읽음 커서 DB 반영까지 관통한다")
    void readMessages_end_to_end() throws Exception {
        // given: 시즌·유저 2명·매치·방 + 내 참여 정보 + 상대가 보낸 메시지 1건을 실제 DB에 커밋
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();
        long matchId = saveMatch(me.getId(), partner.getId());
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), me.getId()));
        Chat received = chatRepository.save(
                Chat.create("cmid-partner", room.getId(), partner.getId(), me.getId(), ChatMessageType.TEXT, "먼저 보냄"));
        UUID ticket = issueWsTicket(me.getId());

        // when: STOMP 연결 → 명령 결과 큐 구독 → /app/chat/read 로 읽음 커서 전진 명령 전송
        StompSession session = connect(ticket);
        BlockingQueue<WebSocketCommandResultBody> commandQueue =
                subscribe(session, "/user/queue/chat/commands", WebSocketCommandResultBody.class);

        session.send("/app/chat/read", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_READ_ADVANCE, "command-read-1",
                new ChatReadAdvancePayload(room.getId(), received.getId())));

        // then: 개인 큐로 read committed 결과 프레임 수신 (같은 commandId)
        WebSocketCommandResultBody committed = commandQueue.poll(5, TimeUnit.SECONDS);
        assertThat(committed).isNotNull();
        assertThat(committed.type()).isEqualTo(WebSocketBodyType.CHAT_READ_COMMITTED);
        assertThat(committed.commandId()).isEqualTo("command-read-1");
        assertThat(committed.kind()).isEqualTo(WebSocketBodyKind.COMMAND_RESULT);

        // then: DB의 읽음 커서가 실제로 해당 메시지까지 전진함
        ChatRoomParticipation participation =
                chatRoomParticipationRepository.findByRoomIdAndUserId(room.getId(), me.getId()).orElseThrow();
        assertThat(participation.getLastReadMessageId()).isEqualTo(received.getId());

        session.disconnect();
    }

    @Test
    @DisplayName("검증 실패 e2e: 봉투 type 불일치·@Valid 실패도 무응답이 아니라 목적지에 맞는 rejected 프레임으로 돌아온다")
    void invalidCommand_returnsRejectedFrame() throws Exception {
        // given: 정상 접속한 유저
        User me = saveUser();
        StompSession session = connect(issueWsTicket(me.getId()));
        BlockingQueue<WebSocketCommandResultBody> commandQueue =
                subscribe(session, "/user/queue/chat/commands", WebSocketCommandResultBody.class);

        // when: 봉투 type이 목적지와 어긋난 명령 (컨트롤러 진입 전 IllegalArgumentException)
        session.send("/app/chat/messages", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_READ_ADVANCE, "command-envelope-1",
                new ChatMessageSendPayload(1L, "cmid-e1", "안녕")));

        // then: 로그만 남기고 끝나지 않고, 목적지에 맞는 거절 프레임이 요청자에게 돌아온다
        WebSocketCommandResultBody rejected = commandQueue.poll(5, TimeUnit.SECONDS);
        assertThat(rejected).isNotNull();
        assertThat(rejected.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_REJECTED);
        assertThat(rejected.commandId()).isEqualTo("command-envelope-1");

        // when: 필수 필드가 빠진 명령 (@Valid 실패)
        session.send("/app/chat/messages", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_MESSAGE_SEND, "command-valid-1",
                new ChatMessageSendPayload(1L, "cmid-v1", null)));

        // then: 같은 경로로 거절 프레임이 돌아오고 commandId가 보존된다
        WebSocketCommandResultBody invalid = commandQueue.poll(5, TimeUnit.SECONDS);
        assertThat(invalid).isNotNull();
        assertThat(invalid.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_REJECTED);
        assertThat(invalid.commandId()).isEqualTo("command-valid-1");

        // when: 읽음 목적지로 잘못된 명령
        session.send("/app/chat/read", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_MESSAGE_SEND, "command-read-1",
                new ChatReadAdvancePayload(1L, 5L)));

        // then: 메시지 전송이 아니라 읽음 거절 타입으로 돌아온다
        WebSocketCommandResultBody readRejected = commandQueue.poll(5, TimeUnit.SECONDS);
        assertThat(readRejected).isNotNull();
        assertThat(readRejected.type()).isEqualTo(WebSocketBodyType.CHAT_READ_REJECTED);
        assertThat(readRejected.commandId()).isEqualTo("command-read-1");

        session.disconnect();
    }

    @Test
    @DisplayName("거절 e2e: 존재하지 않는 방으로 전송하면 rejected 프레임이 요청자에게 돌아오고 DB에는 저장되지 않는다")
    void sendMessage_roomNotFound_returnsRejectedFrame() throws Exception {
        // given: 유저만 있고 채팅방은 만들지 않는다 (roomId 999999 는 존재하지 않음)
        User me = saveUser();
        UUID ticket = issueWsTicket(me.getId());

        // when: 없는 방으로 메시지 전송 명령
        StompSession session = connect(ticket);
        BlockingQueue<WebSocketCommandResultBody> commandQueue =
                subscribe(session, "/user/queue/chat/commands", WebSocketCommandResultBody.class);

        session.send("/app/chat/messages", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_MESSAGE_SEND, "command-reject-1",
                new ChatMessageSendPayload(999_999L, "cmid-reject", "안녕")));

        // then: 예외가 STOMP ERROR로 나가지 않고 rejected 결과 프레임으로 돌아온다
        WebSocketCommandResultBody rejected = commandQueue.poll(5, TimeUnit.SECONDS);
        assertThat(rejected).isNotNull();
        assertThat(rejected.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_REJECTED);
        assertThat(rejected.commandId()).isEqualTo("command-reject-1");

        // then: 저장은 일어나지 않는다
        assertThat(chatRepository.findBySenderUserIdAndClientMsgId(me.getId(), "cmid-reject")).isEmpty();

        session.disconnect();
    }

    @Test
    @DisplayName("다중 기기 e2e: command-result는 명령을 보낸 기기에만 가고, event는 두 기기 모두에 간다")
    void commandResult_goesOnlyToRequestingSession() throws Exception {
        // given: 같은 유저가 두 기기(=두 STOMP 세션)로 접속한 상태
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();
        long matchId = saveMatch(me.getId(), partner.getId());
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), me.getId()));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), partner.getId()));

        StompSession deviceA = connect(issueWsTicket(me.getId()));
        StompSession deviceB = connect(issueWsTicket(me.getId()));

        BlockingQueue<WebSocketCommandResultBody> commandQueueA =
                subscribe(deviceA, "/user/queue/chat/commands", WebSocketCommandResultBody.class);
        BlockingQueue<WebSocketCommandResultBody> commandQueueB =
                subscribe(deviceB, "/user/queue/chat/commands", WebSocketCommandResultBody.class);
        BlockingQueue<WebSocketEventBody> roomQueueB =
                subscribe(deviceB, "/user/queue/chat/rooms/" + room.getId(), WebSocketEventBody.class);

        // when: 기기 A 에서만 메시지 전송 명령
        deviceA.send("/app/chat/messages", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_MESSAGE_SEND, "command-multi-1",
                new ChatMessageSendPayload(room.getId(), "cmid-multi", "안녕")));

        // then: 명령을 보낸 A 는 committed 응답을 받는다
        WebSocketCommandResultBody committedOnA = commandQueueA.poll(5, TimeUnit.SECONDS);
        assertThat(committedOnA).isNotNull();
        assertThat(committedOnA.commandId()).isEqualTo("command-multi-1");

        // then: B 는 event 는 받되(같은 계정이므로 화면 갱신 필요), command-result 는 받지 않는다.
        //       event 가 먼저 도착했음을 확인한 뒤 검사하므로 "아직 안 온 것"과 구분된다.
        WebSocketEventBody createdOnB = roomQueueB.poll(5, TimeUnit.SECONDS);
        assertThat(createdOnB).isNotNull();
        assertThat(createdOnB.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_CREATED);
        assertThat(commandQueueB).isEmpty();

        deviceA.disconnect();
        deviceB.disconnect();
    }

    @Test
    @DisplayName("읽음 전파 e2e: 내가 읽으면 상대의 방 큐로 chat.read.advanced 이벤트가 가고, 읽은 본인에게는 가지 않는다")
    void readAdvanced_event_goes_to_partner_only() throws Exception {
        // given: 두 유저가 모두 참여 중인 방 + 상대가 보낸 메시지 1건
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();
        long matchId = saveMatch(me.getId(), partner.getId());
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), me.getId()));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), partner.getId()));
        Chat received = chatRepository.save(
                Chat.create("cmid-partner", room.getId(), partner.getId(), me.getId(), ChatMessageType.TEXT, "먼저 보냄"));

        // given: 두 유저가 각자 STOMP로 접속해 같은 방 큐를 구독
        StompSession mySession = connect(issueWsTicket(me.getId()));
        StompSession partnerSession = connect(issueWsTicket(partner.getId()));

        BlockingQueue<WebSocketEventBody> myRoomQueue =
                subscribe(mySession, "/user/queue/chat/rooms/" + room.getId(), WebSocketEventBody.class);
        BlockingQueue<WebSocketEventBody> partnerRoomQueue =
                subscribe(partnerSession, "/user/queue/chat/rooms/" + room.getId(), WebSocketEventBody.class);

        // when: 내가 상대 메시지를 읽음 처리
        mySession.send("/app/chat/read", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_READ_ADVANCE, "command-read-adv-1",
                new ChatReadAdvancePayload(room.getId(), received.getId())));

        // then: 상대의 방 큐로 chat.read.advanced 이벤트가 도착하고 봉투는 event 규약을 따른다
        WebSocketEventBody advanced = partnerRoomQueue.poll(5, TimeUnit.SECONDS);
        assertThat(advanced).isNotNull();
        assertThat(advanced.type()).isEqualTo(WebSocketBodyType.CHAT_READ_ADVANCED);
        assertThat(advanced.v()).isEqualTo(1);
        assertThat(advanced.kind()).isEqualTo(WebSocketBodyKind.EVENT);
        assertThat(advanced.eventId()).isNotBlank();
        assertThat(advanced.occurredAt()).isNotNull();

        // then: 페이로드에 방 id와 내가 읽은 위치가 문자열 id로 담긴다
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) advanced.payload();
        assertThat(payload.get("roomId")).isEqualTo(String.valueOf(room.getId()));
        assertThat(payload.get("lastReadMessageId")).isEqualTo(String.valueOf(received.getId()));

        // then: 읽은 본인에게는 아무 이벤트도 가지 않는다 (상대 프레임 도착을 확인한 뒤 검사)
        assertThat(myRoomQueue).isEmpty();

        mySession.disconnect();
        partnerSession.disconnect();
    }

    @Test
    @DisplayName("읽음 전파 e2e: 같은 위치로 다시 읽음 처리하면 상대에게 이벤트가 다시 가지 않는다")
    void readAdvanced_event_is_not_republished_when_pointer_does_not_move() throws Exception {
        // given: 두 유저가 참여 중인 방 + 상대가 보낸 메시지 2건
        saveCurrentSeason();
        User me = saveUser();
        User partner = saveUser();
        long matchId = saveMatch(me.getId(), partner.getId());
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), me.getId()));
        chatRoomParticipationRepository.save(agreedParticipation(room.getId(), partner.getId()));
        Chat first = chatRepository.save(
                Chat.create("cmid-p1", room.getId(), partner.getId(), me.getId(), ChatMessageType.TEXT, "1"));
        Chat second = chatRepository.save(
                Chat.create("cmid-p2", room.getId(), partner.getId(), me.getId(), ChatMessageType.TEXT, "2"));

        StompSession mySession = connect(issueWsTicket(me.getId()));
        StompSession partnerSession = connect(issueWsTicket(partner.getId()));

        BlockingQueue<WebSocketEventBody> partnerRoomQueue =
                subscribe(partnerSession, "/user/queue/chat/rooms/" + room.getId(), WebSocketEventBody.class);

        // when: 2번까지 읽은 뒤, 뒤늦게 도착한 중복 명령으로 1번까지 다시 읽음 처리
        mySession.send("/app/chat/read", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_READ_ADVANCE, "command-read-adv-2",
                new ChatReadAdvancePayload(room.getId(), second.getId())));

        WebSocketEventBody advanced = partnerRoomQueue.poll(5, TimeUnit.SECONDS);
        assertThat(advanced).isNotNull();

        mySession.send("/app/chat/read", new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_READ_ADVANCE, "command-read-adv-3",
                new ChatReadAdvancePayload(room.getId(), first.getId())));

        // then: 위치가 뒤로 가지 않으므로 상대 화면을 흔들 두 번째 프레임은 오지 않는다
        assertThat(partnerRoomQueue.poll(1, TimeUnit.SECONDS)).isNull();

        // then: DB 커서도 앞선 값을 유지한다
        ChatRoomParticipation participation =
                chatRoomParticipationRepository.findByRoomIdAndUserId(room.getId(), me.getId()).orElseThrow();
        assertThat(participation.getLastReadMessageId()).isEqualTo(second.getId());

        mySession.disconnect();
        partnerSession.disconnect();
    }

    /** seasons/matches는 팩토리·세터가 없어 네이티브로 insert 한다 (JdbcTemplate은 autocommit이라 커밋됨). */
    private void saveCurrentSeason() {
        jdbcTemplate.update("""
                INSERT INTO seasons (id, started_at, ended_at, is_active, created_at)
                VALUES (?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6))
                """, SEASON_ID);
    }

    private long saveMatch(long userId, long partnerUserId) {
        // matches 에 user_a_id < user_b_id 체크 제약이 있어 정렬해서 넣는다.
        long userAId = Math.min(userId, partnerUserId);
        long userBId = Math.max(userId, partnerUserId);

        jdbcTemplate.update("""
                INSERT INTO matches (user_a_id, user_b_id, season_id, created_at)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6))
                """, userAId, userBId, SEASON_ID);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?
                """, Long.class, userAId, userBId);
    }

    /** 입장 동의까지 마친 참여 정보를 만든다. 메시지 전송은 둘 다 동의한 방에서만 가능하다. */
    private ChatRoomParticipation agreedParticipation(Long roomId, Long userId) {
        ChatRoomParticipation participation = ChatRoomParticipation.create(roomId, userId);
        participation.agree();
        return participation;
    }
}
