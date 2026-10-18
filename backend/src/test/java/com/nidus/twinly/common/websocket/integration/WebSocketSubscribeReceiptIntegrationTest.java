package com.nidus.twinly.common.websocket.integration;

import com.nidus.twinly.chat.dto.websocket.ChatChangedPayload;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.common.websocket.sender.WebSocketLocalSender;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import com.nidus.twinly.support.AbstractWebSocketIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * SUBSCRIBE 의 receipt 헤더에 RECEIPT 로 답하는지 실제 소켓으로 검증한다.
 *
 * <p>Spring STOMP 클라이언트는 RECEIPT 를 receipt-id 로 구독과 짝지어 receipt task 를 실행한다.
 * 그래서 receipt task 가 돌았다는 것은 서버가 같은 값을 그대로 돌려줬다는 뜻이다.
 */
class WebSocketSubscribeReceiptIntegrationTest extends AbstractWebSocketIntegrationTest {

    private static final List<String> CORE_DESTINATIONS = List.of(
            "/user/queue/season",
            "/user/queue/connection/control",
            "/user/queue/chat/index",
            "/user/queue/chat/commands"
    );
    private static final String ROOM_DESTINATION = "/user/queue/chat/rooms/42";
    private static final String ROOM_BROKER_DESTINATION = "/queue/chat/rooms/42";
    private static final String DISALLOWED_DESTINATION = "/queue/chat/index";

    private static final Duration CORE_RECEIPT_WAIT = Duration.ofSeconds(6);
    private static final Duration ROOM_RECEIPT_WAIT = Duration.ofSeconds(8);

    @Autowired
    ConnectionTicketRepository connectionTicketRepository;

    @Autowired
    WebSocketLocalSender webSocketLocalSender;

    @AfterEach
    void cleanUp() {
        connectionTicketRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("기본 구독 4개에 각자의 receipt 값을 담은 RECEIPT 4개가 온다")
    void core_subscriptions_each_get_receipt() throws Exception {
        // given: 실제 STOMP 연결
        User me = saveUser();
        StompSession session = connect(issueWsTicket(me.getId()));

        // when: 앱처럼 기본 구독 4개를 구독마다 다른 receipt 로 연달아 보낸다
        List<CompletableFuture<Void>> receipts = CORE_DESTINATIONS.stream()
                .map(destination -> subscribeWithReceipt(session, destination, newReceipt(), new LinkedBlockingQueue<>()))
                .toList();

        // then: 앱 대기시간(6초) 안에 4개 모두 도착한다
        CompletableFuture.allOf(receipts.toArray(CompletableFuture[]::new))
                .get(CORE_RECEIPT_WAIT.toSeconds(), TimeUnit.SECONDS);

        session.disconnect();
    }

    @Test
    @DisplayName("방 구독의 RECEIPT 를 받은 직후 발행한 메시지가 따로 기다리지 않아도 도착한다")
    void message_published_right_after_receipt_arrives() throws Exception {
        // given: 방 구독을 보내고 RECEIPT 를 받는다
        User me = saveUser();
        StompSession session = connect(issueWsTicket(me.getId()));
        BlockingQueue<WebSocketEventBody<?>> received = new LinkedBlockingQueue<>();

        subscribeWithReceipt(session, ROOM_DESTINATION, newReceipt(), received)
                .get(ROOM_RECEIPT_WAIT.toSeconds(), TimeUnit.SECONDS);

        // when: 지연 없이 바로 그 방으로 발행한다
        webSocketLocalSender.sendToUser(String.valueOf(me.getId()), ROOM_BROKER_DESTINATION,
                WebSocketEventBody.of(WebSocketBodyType.CHAT_CHANGED, new ChatChangedPayload(42L)));

        // then: RECEIPT 는 구독 등록이 끝난 뒤에 나가므로 메시지를 놓치지 않는다
        WebSocketEventBody<?> event = received.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo(WebSocketBodyType.CHAT_CHANGED);

        session.disconnect();
    }

    @Test
    @DisplayName("거절된 구독에는 RECEIPT 가 오지 않고 연결이 닫힌다")
    void rejected_subscription_gets_no_receipt() throws Exception {
        // given: 실제 STOMP 연결
        User me = saveUser();
        StompSession session = connect(issueWsTicket(me.getId()));

        // when: /user 가 아닌 목적지를 receipt 와 함께 구독한다
        CompletableFuture<Void> receipt =
                subscribeWithReceipt(session, DISALLOWED_DESTINATION, newReceipt(), new LinkedBlockingQueue<>());

        // then: 지금처럼 ERROR 뒤 연결이 닫히고, RECEIPT 는 오지 않는다
        await().atMost(Duration.ofSeconds(5)).until(() -> !session.isConnected());
        assertThat(receipt).failsWithin(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("다른 세션이 같은 receipt 값을 써도 RECEIPT 는 각자 자기 세션으로 간다")
    void same_receipt_value_is_isolated_per_session() throws Exception {
        // given: 두 유저가 각자 연결한다
        User first = saveUser();
        User second = saveUser();
        StompSession firstSession = connect(issueWsTicket(first.getId()));
        StompSession secondSession = connect(issueWsTicket(second.getId()));
        String sharedReceipt = newReceipt();

        // when: 첫 세션이 RECEIPT 를 받은 뒤, 두 번째 세션이 같은 receipt 값으로 구독한다
        subscribeWithReceipt(firstSession, CORE_DESTINATIONS.get(2), sharedReceipt, new LinkedBlockingQueue<>())
                .get(CORE_RECEIPT_WAIT.toSeconds(), TimeUnit.SECONDS);
        CompletableFuture<Void> secondReceipt =
                subscribeWithReceipt(secondSession, CORE_DESTINATIONS.get(2), sharedReceipt, new LinkedBlockingQueue<>());

        // then: 두 번째 세션도 자기 RECEIPT 를 받는다. 첫 세션으로 새면 이쪽은 받지 못한다
        secondReceipt.get(CORE_RECEIPT_WAIT.toSeconds(), TimeUnit.SECONDS);

        firstSession.disconnect();
        secondSession.disconnect();
    }

    private CompletableFuture<Void> subscribeWithReceipt(
            StompSession session, String destination, String receipt, BlockingQueue<WebSocketEventBody<?>> received) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setReceipt(receipt);

        CompletableFuture<Void> confirmed = new CompletableFuture<>();
        session.subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders frameHeaders) {
                return WebSocketEventBody.class;
            }

            @Override
            public void handleFrame(StompHeaders frameHeaders, Object payload) {
                received.add((WebSocketEventBody<?>) payload);
            }
        }).addReceiptTask(() -> confirmed.complete(null));

        return confirmed;
    }

    private String newReceipt() {
        return "rcpt-" + UUID.randomUUID();
    }
}
