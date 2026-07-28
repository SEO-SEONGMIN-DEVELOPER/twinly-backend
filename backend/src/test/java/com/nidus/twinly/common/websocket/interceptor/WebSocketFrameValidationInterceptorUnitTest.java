package com.nidus.twinly.common.websocket.interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketFrameValidationInterceptorUnitTest {

    private final WebSocketFrameValidationInterceptor interceptor = new WebSocketFrameValidationInterceptor();

    @Test
    @DisplayName("SEND 정상: 화이트리스트에 있는 커맨드 목적지는 그대로 통과한다")
    void send_allowed_destination_passes() {
        // given: 컨트롤러가 @MessageMapping 으로 열어둔 두 목적지
        Message<byte[]> sendMessage = frame(StompCommand.SEND, "/app/chat/messages");
        Message<byte[]> readMessage = frame(StompCommand.SEND, "/app/chat/read");

        // when & then: 두 목적지 모두 예외 없이 통과
        assertThatCode(() -> interceptor.preSend(sendMessage, null)).doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(readMessage, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SEND 차단: 브로커 목적지로 직접 보내면 거부한다 (구독자에게 위조 이벤트가 브로드캐스트되는 것을 막는다)")
    void send_to_broker_destination_is_rejected() {
        // given: 서버만 발행해야 하는 브로커 목적지
        Message<byte[]> message = frame(StompCommand.SEND, "/queue/chat/rooms/1");

        // when & then: 목적지 위반 예외 발생
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("허용되지 않은 전송 목적지");
    }

    @Test
    @DisplayName("SEND 차단: 남의 개인 큐로 보내면 거부한다 (임의 유저에게 위조 이벤트를 밀어넣는 것을 막는다)")
    void send_to_other_users_queue_is_rejected() {
        // given: 999번 유저의 개인 큐를 노린 목적지
        Message<byte[]> message = frame(StompCommand.SEND, "/user/999/queue/chat/index");

        // when & then: 목적지 위반 예외 발생
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("허용되지 않은 전송 목적지");
    }

    @Test
    @DisplayName("SEND 차단: 목적지가 없으면 거부한다")
    void send_without_destination_is_rejected() {
        // given: destination 헤더가 없는 SEND 프레임
        Message<byte[]> message = frame(StompCommand.SEND, null);

        // when & then: 목적지 위반 예외 발생
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("허용되지 않은 전송 목적지");
    }

    @Test
    @DisplayName("SUBSCRIBE 정상: 개인 큐(/user/) 구독은 통과한다")
    void subscribe_user_destination_passes() {
        // given: 앱이 실제로 구독하는 개인 큐
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/user/queue/chat/index");

        // when & then: 예외 없이 통과
        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SUBSCRIBE 차단: /user 가 아닌 브로커 목적지 직접 구독은 거부한다")
    void subscribe_broker_destination_is_rejected() {
        // given: 개인 큐 변환을 우회한 브로커 목적지
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/queue/chat/index");

        // when & then: 목적지 위반 예외 발생
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("허용되지 않은 구독 목적지");
    }

    @Test
    @DisplayName("SEND·SUBSCRIBE 외의 프레임은 목적지 검사 없이 통과한다")
    void other_commands_pass_without_destination_check() {
        // given: 목적지가 없는 연결 관리 프레임들
        Message<byte[]> connect = frame(StompCommand.CONNECT, null);
        Message<byte[]> disconnect = frame(StompCommand.DISCONNECT, null);

        // when & then: 검사 대상이 아니므로 그대로 통과
        assertThatCode(() -> interceptor.preSend(connect, null)).doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(disconnect, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SEND 차단: 허용 목적지라도 본문이 32KB를 초과하면 거부한다")
    void send_over_body_limit_is_rejected() {
        // given: 상한을 1바이트 초과하는 본문
        byte[] tooLarge = "a".repeat(32 * 1024 + 1).getBytes(StandardCharsets.UTF_8);
        Message<byte[]> message = frame(StompCommand.SEND, "/app/chat/messages", tooLarge);

        // when & then: 본문 상한 예외 발생
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("본문 크기 상한");
    }

    @Test
    @DisplayName("통과한 프레임은 변형 없이 그대로 반환된다")
    void passed_message_is_returned_as_is() {
        // given: 허용된 목적지의 SEND 프레임
        Message<byte[]> message = frame(StompCommand.SEND, "/app/chat/messages");

        // when: 인터셉터를 통과
        Message<?> result = interceptor.preSend(message, null);

        // then: 같은 인스턴스가 그대로 나옴
        assertThat(result).isSameAs(message);
    }

    private Message<byte[]> frame(StompCommand command, String destination) {
        return frame(command, destination, new byte[0]);
    }

    private Message<byte[]> frame(StompCommand command, String destination, byte[] body) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(body, accessor.getMessageHeaders());
    }
}
