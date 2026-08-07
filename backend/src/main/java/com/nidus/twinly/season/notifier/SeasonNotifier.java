package com.nidus.twinly.season.notifier;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.common.websocket.relay.WebSocketRelayPublisher;
import com.nidus.twinly.season.dto.websocket.SeasonChangedPayload;
import com.nidus.twinly.season.event.SeasonChangedEvent;
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SeasonNotifier {

    private static final String SEASON_DESTINATION = "/queue/season";

    private static final String SEASON_OUTBOUND_CHANNEL = "/user/queue/season";

    private final WebSocketRelayPublisher relayPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeasonChanged(SeasonChangedEvent event) {
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(event.seasonId()));

        publishSeasonChanged(body);
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = SEASON_OUTBOUND_CHANNEL,
            description = "시즌이 전환됨 (클라이언트는 시즌에 의존하는 화면을 재조회해야 한다)"
    ))
    @StompAsyncOperationBinding
    public void publishSeasonChanged(@Payload WebSocketEventBody<SeasonChangedPayload> body) {
        relayPublisher.publishToAll(SEASON_DESTINATION, body);
    }
}
