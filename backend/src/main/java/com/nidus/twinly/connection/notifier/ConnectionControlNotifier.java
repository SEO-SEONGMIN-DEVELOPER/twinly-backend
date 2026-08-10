package com.nidus.twinly.connection.notifier;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketControlBody;
import com.nidus.twinly.common.websocket.relay.WebSocketRelayPublisher;
import com.nidus.twinly.common.websocket.sender.WebSocketLocalSender;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.domain.ConnectionDrainingScope;
import com.nidus.twinly.connection.dto.websocket.ConnectionDrainingPayload;
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConnectionControlNotifier {

    private static final String CONTROL_DESTINATION = "/queue/connection/control";

    private static final String CONTROL_OUTBOUND_CHANNEL = "/user/queue/connection/control";

    private final WebSocketRelayPublisher relayPublisher;
    private final WebSocketLocalSender localSender;

    public void notifyDraining(ConnectionDrainingReason reason, Long retryAfterMs, ConnectionDrainingScope scope) {
        WebSocketControlBody<ConnectionDrainingPayload> body = WebSocketControlBody.of(
                WebSocketBodyType.CONNECTION_DRAINING,
                new ConnectionDrainingPayload(reason, retryAfterMs));

        if (scope == ConnectionDrainingScope.LOCAL) {
            localSender.sendToAll(CONTROL_DESTINATION, body);
            return;
        }

        publishDraining(body);
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = CONTROL_OUTBOUND_CHANNEL,
            description = "서버가 연결 종료를 예고함 (클라이언트는 retryAfterMs 후 재연결해야 한다)"
    ))
    @StompAsyncOperationBinding
    public void publishDraining(@Payload WebSocketControlBody<ConnectionDrainingPayload> body) {
        relayPublisher.publishToAll(CONTROL_DESTINATION, body);
    }
}
