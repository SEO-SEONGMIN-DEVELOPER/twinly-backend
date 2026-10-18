package com.nidus.twinly.common.websocket.interceptor;

import com.nidus.twinly.common.logging.TraceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.UserDestinationMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class WebSocketSubscribeReceiptInterceptor implements ExecutorChannelInterceptor {

    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    private final MessageChannel clientOutboundChannel;

    public WebSocketSubscribeReceiptInterceptor(
            @Lazy @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.clientOutboundChannel = clientOutboundChannel;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        if (ex != null || !(handler instanceof UserDestinationMessageHandler)) {
            return;
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String receipt = accessor.getReceipt();

        if (accessor.getCommand() != StompCommand.SUBSCRIBE || receipt == null) {
            return;
        }

        clientOutboundChannel.send(receiptMessage(accessor.getSessionId(), receipt));
    }

    private Message<byte[]> receiptMessage(String sessionId, String receipt) {
        StompHeaderAccessor receiptAccessor = StompHeaderAccessor.create(StompCommand.RECEIPT);
        receiptAccessor.setReceiptId(receipt);
        receiptAccessor.setSessionId(sessionId);

        String traceId = TraceContext.currentTraceId();
        if (traceId != null) {
            receiptAccessor.setHeader(TraceContext.TRACE_ID, traceId);
        }

        return MessageBuilder.createMessage(EMPTY_PAYLOAD, receiptAccessor.getMessageHeaders());
    }
}
