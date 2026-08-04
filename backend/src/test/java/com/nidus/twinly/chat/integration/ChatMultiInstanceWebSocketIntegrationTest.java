package com.nidus.twinly.chat.integration;

import com.nidus.twinly.TwinlyApplication;
import com.nidus.twinly.chat.dto.websocket.ChatMessageSendPayload;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.common.websocket.dto.WebSocketRequestBody;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.support.AbstractWebSocketIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멀티 인스턴스 검증.
 *
 * <p>같은 MySQL·Redis를 보는 두 번째 애플리케이션 컨텍스트를 추가로 띄운다. 컨텍스트가 분리되면
 * SimpleBroker·SimpUserRegistry가 각각 따로 생기므로, "상대가 다른 서버에 붙어 있는" 상황이 재현된다.
 * (JVM은 하나라 static 상태까지 분리되지는 않는다. 그 부분은 실제로 앱을 두 번 띄워 확인해야 한다.)
 */
class ChatMultiInstanceWebSocketIntegrationTest extends AbstractWebSocketIntegrationTest {

    private static final long SEASON_ID = 1L;

    private static ConfigurableApplicationContext secondInstance;
    private static int secondPort;

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

    @BeforeAll
    static void startSecondInstance() {
        // properties(...)는 default properties라 application.yaml에 밀린다. 커맨드라인 인자로 넘겨야 우선순위가 높다.
        secondInstance = new SpringApplicationBuilder(TwinlyApplication.class)
                .registerShutdownHook(false)
                .run(
                "--server.port=0",
                "--management.server.port=0",
                "--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                "--spring.datasource.username=" + MYSQL.getUsername(),
                "--spring.datasource.password=" + MYSQL.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379));

        secondPort = secondInstance.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
    }

    @AfterAll
    static void stopSecondInstance() {
        secondInstance.close();
    }

    @AfterEach
    void cleanUp() {
        chatRoomParticipationRepository.deleteAll();
        chatRepository.deleteAll();
        chatRoomRepository.deleteAll();
        matchRepository.deleteAll();
        connectionTicketRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM seasons");
    }

    @Test
    @DisplayName("크로스 서버 e2e: 1번 인스턴스로 보낸 메시지가 2번 인스턴스에 붙은 상대에게 전달된다")
    void message_crossesInstances() throws Exception {
        // given: 보내는 사람은 1번 인스턴스에, 받는 사람은 2번 인스턴스에 접속한다
        Fixture fixture = saveChatFixture();

        StompSession senderSession = connect(port, issueWsTicket(fixture.me.getId()));
        StompSession receiverSession = connect(secondPort, issueWsTicket(fixture.partner.getId()));

        BlockingQueue<WebSocketEventBody> receiverQueue =
                subscribe(receiverSession, "/user/queue/chat/rooms/" + fixture.roomId, WebSocketEventBody.class);

        // when: 1번 인스턴스로 전송
        senderSession.send("/app/chat/messages", sendCommand(fixture.roomId, "cmid-1", "안녕"));

        // then: Redis를 건너 2번 인스턴스의 세션까지 도달한다
        WebSocketEventBody created = receiverQueue.poll(5, TimeUnit.SECONDS);
        assertThat(created).isNotNull();
        assertThat(created.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_CREATED);

        senderSession.disconnect();
        receiverSession.disconnect();
    }

    @Test
    @DisplayName("중복 방지: 발행한 인스턴스에 붙은 유저에게도 이벤트가 한 번만 간다")
    void message_notDuplicatedOnPublishingInstance() throws Exception {
        // given: 보낸 사람도 참여자라 자신에게도 created가 온다. 직접 전송이 남아 있으면 두 번 온다.
        Fixture fixture = saveChatFixture();

        StompSession senderSession = connect(port, issueWsTicket(fixture.me.getId()));
        BlockingQueue<WebSocketEventBody> senderQueue =
                subscribe(senderSession, "/user/queue/chat/rooms/" + fixture.roomId, WebSocketEventBody.class);

        // when
        senderSession.send("/app/chat/messages", sendCommand(fixture.roomId, "cmid-1", "안녕"));

        // then: 한 번만 도착하고, 뒤이어 오는 프레임은 없다
        assertThat(senderQueue.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(senderQueue.poll(2, TimeUnit.SECONDS)).isNull();

        senderSession.disconnect();
    }

    @Test
    @DisplayName("순서 보장: 연달아 보낸 메시지가 다른 인스턴스에서도 보낸 순서대로 도착한다")
    void messages_preserveOrderAcrossInstances() throws Exception {
        // given
        Fixture fixture = saveChatFixture();

        StompSession senderSession = connect(port, issueWsTicket(fixture.me.getId()));
        StompSession receiverSession = connect(secondPort, issueWsTicket(fixture.partner.getId()));

        BlockingQueue<WebSocketEventBody> receiverQueue =
                subscribe(receiverSession, "/user/queue/chat/rooms/" + fixture.roomId, WebSocketEventBody.class);

        // when: 1 → 2 → 3 순서로 전송
        for (int i = 1; i <= 3; i++) {
            senderSession.send("/app/chat/messages", sendCommand(fixture.roomId, "cmid-" + i, String.valueOf(i)));
        }

        // then: 받는 쪽에서도 1 → 2 → 3
        for (int i = 1; i <= 3; i++) {
            WebSocketEventBody created = receiverQueue.poll(5, TimeUnit.SECONDS);
            assertThat(created).isNotNull();
            assertThat(jsonMapper.writeValueAsString(created.payload())).contains("\"text\":\"" + i + "\"");
        }

        senderSession.disconnect();
        receiverSession.disconnect();
    }

    private WebSocketRequestBody<ChatMessageSendPayload> sendCommand(long roomId, String clientMsgId, String text) {
        return new WebSocketRequestBody<>(
                1, WebSocketBodyKind.COMMAND, WebSocketBodyType.CHAT_MESSAGE_SEND, "command-" + clientMsgId,
                new ChatMessageSendPayload(roomId, clientMsgId, text));
    }

    private Fixture saveChatFixture() {
        jdbcTemplate.update("""
                INSERT INTO seasons (id, started_at, ended_at, is_active, created_at)
                VALUES (?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6))
                """, SEASON_ID);

        User me = saveUser();
        User partner = saveUser();

        long userAId = Math.min(me.getId(), partner.getId());
        long userBId = Math.max(me.getId(), partner.getId());
        jdbcTemplate.update("""
                INSERT INTO matches (user_a_id, user_b_id, season_id, created_at)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6))
                """, userAId, userBId, SEASON_ID);
        Long matchId = jdbcTemplate.queryForObject("""
                SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?
                """, Long.class, userAId, userBId);

        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        chatRoomParticipationRepository.save(ChatRoomParticipation.create(room.getId(), me.getId()));
        chatRoomParticipationRepository.save(ChatRoomParticipation.create(room.getId(), partner.getId()));

        return new Fixture(me, partner, room.getId());
    }

    private record Fixture(User me, User partner, long roomId) {
    }
}
