package com.nidus.twinly.common.websocket.interceptor;

import com.nidus.twinly.common.logging.TraceContext;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class WebSocketTraceIdInterceptor implements ExecutorChannelInterceptor {

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        MDC.put(TraceContext.TRACE_ID, inheritedTraceId(message));

        return message;
    }

    private String inheritedTraceId(Message<?> message) {
        return message.getHeaders().get(TraceContext.TRACE_ID) instanceof String traceId
                ? traceId
                : TraceContext.newTraceId();
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        MDC.clear();
    }
}
