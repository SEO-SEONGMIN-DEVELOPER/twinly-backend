package com.nidus.twinly.common.websocket.interceptor;

import com.nidus.twinly.common.logging.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.UserDestinationMessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WebSocketSubscribeReceiptInterceptorUnitTest {

    private static final String SESSION_ID = "session-1";
    private static final String RECEIPT = "rcpt-41d0a9c2";
    private static final String DESTINATION = "/user/queue/chat/index";

    // RECEIPT 는 앱 메시지가 아니라 STOMP 프로토콜 프레임이라 convertAndSendToUser 가 아니라 아웃바운드 채널로 직접 보낸다.
    @Mock
    MessageChannel clientOutboundChannel;

    @Mock
    UserDestinationMessageHandler userDestinationHandler;

    @Mock
    SimpleBrokerMessageHandler brokerHandler;

    @InjectMocks
    WebSocketSubscribeReceiptInterceptor interceptor;

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("구독 등록이 끝나면 요청한 세션으로 같은 receipt 값의 RECEIPT 를 보낸다")
    void sends_receipt_to_requesting_session() {
        // given: receipt 를 단 SUBSCRIBE
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, RECEIPT);

        // when: /user 구독을 등록하는 핸들러가 처리를 마친다
        interceptor.afterMessageHandled(subscribe, null, userDestinationHandler, null);

        // then: 앱은 receipt-id 로 구독과 짝을 맞추므로 값이 바뀌면 안 된다
        StompHeaderAccessor receipt = sentReceipt();
        assertThat(receipt.getCommand()).isEqualTo(StompCommand.RECEIPT);
        assertThat(receipt.getReceiptId()).isEqualTo(RECEIPT);
        assertThat(receipt.getSessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    @DisplayName("다른 핸들러가 처리를 마친 시점에는 보내지 않는다")
    void ignores_other_handlers() {
        // given: 인바운드 채널은 핸들러마다 afterMessageHandled 를 부른다
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, RECEIPT);

        // when: /user 구독을 등록하지 않는 핸들러가 끝난다
        interceptor.afterMessageHandled(subscribe, null, brokerHandler, null);

        // then: 여기서 보내면 RECEIPT 가 중복되고, 등록 전에 나갈 수도 있다
        then(clientOutboundChannel).should(never()).send(any());
    }

    @Test
    @DisplayName("구독 처리 중 예외가 나면 보내지 않는다")
    void skips_failed_subscription() {
        // given: receipt 를 단 SUBSCRIBE
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, RECEIPT);

        // when: 등록 중 예외가 난다
        interceptor.afterMessageHandled(subscribe, null, userDestinationHandler,
                new MessagingException("등록 실패"));

        // then: 등록되지 않은 구독을 확정해 주면 앱이 메시지를 기다리다 놓친다
        then(clientOutboundChannel).should(never()).send(any());
    }

    @Test
    @DisplayName("receipt 헤더가 없으면 보내지 않는다")
    void skips_subscription_without_receipt() {
        // given: receipt 없는 SUBSCRIBE (지금 배포된 앱)
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, null);

        // when: 등록이 끝난다
        interceptor.afterMessageHandled(subscribe, null, userDestinationHandler, null);

        // then: 요청하지 않은 RECEIPT 는 보내지 않는다
        then(clientOutboundChannel).should(never()).send(any());
    }

    @Test
    @DisplayName("SEND 의 receipt 에는 응답하지 않는다")
    void ignores_send_receipt() {
        // given: receipt 를 단 SEND
        Message<byte[]> send = frame(StompCommand.SEND, RECEIPT);

        // when: 처리가 끝난다
        interceptor.afterMessageHandled(send, null, userDestinationHandler, null);

        // then: SEND 의 RECEIPT 는 이번 범위가 아니다
        then(clientOutboundChannel).should(never()).send(any());
    }

    @Test
    @DisplayName("RECEIPT 에 현재 traceId 를 실어 보낸다")
    void carries_trace_id() {
        // given: SUBSCRIBE 를 처리하는 스레드의 traceId
        MDC.put(TraceContext.TRACE_ID, "a3f9c210");
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, RECEIPT);

        // when: 등록이 끝난다
        interceptor.afterMessageHandled(subscribe, null, userDestinationHandler, null);

        // then: 아웃바운드 전송 실패 로그가 원래 SUBSCRIBE 와 이어진다
        assertThat(sentReceipt().getHeader(TraceContext.TRACE_ID)).isEqualTo("a3f9c210");
    }

    private Message<byte[]> frame(StompCommand command, String receipt) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination(DESTINATION);
        if (receipt != null) {
            accessor.setReceipt(receipt);
        }

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @SuppressWarnings("unchecked")
    private StompHeaderAccessor sentReceipt() {
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        then(clientOutboundChannel).should().send(captor.capture());

        return StompHeaderAccessor.wrap(captor.getValue());
    }
}
