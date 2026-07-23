package com.nidus.twinly.common.websocket.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Slf4j
@ControllerAdvice
public class GlobalWebSocketExceptionHandler {

    @MessageExceptionHandler
    public void handleUnexpected(Throwable e) {
        log.warn("처리하지 못한 WebSocket 메시지", e);
    }
}
