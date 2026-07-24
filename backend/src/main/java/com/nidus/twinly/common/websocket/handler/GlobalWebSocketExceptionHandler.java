package com.nidus.twinly.common.websocket.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Slf4j
@ControllerAdvice
public class GlobalWebSocketExceptionHandler {

    @MessageExceptionHandler
    public void handleUnexpected(Exception e) {
        log.warn("[Websocket Error]: ", e);
    }
}
