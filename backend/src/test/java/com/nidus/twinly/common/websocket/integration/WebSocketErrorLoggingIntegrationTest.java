package com.nidus.twinly.common.websocket.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nidus.twinly.common.websocket.domain.WebSocketErrorCode;
import com.nidus.twinly.common.websocket.interceptor.WebSocketErrorInterceptor;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import com.nidus.twinly.support.AbstractWebSocketIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompSession;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WebSocketErrorLoggingIntegrationTest extends AbstractWebSocketIntegrationTest {

    private static final String DISALLOWED_DESTINATION = "/app/not-allowed";
    private static final String ERROR_CODE = "errorCode";

    @Autowired
    ConnectionTicketRepository connectionTicketRepository;

    private Logger interceptorLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        interceptorLogger = (Logger) LoggerFactory.getLogger(WebSocketErrorInterceptor.class);
        appender = new ListAppender<>();
        appender.start();
        interceptorLogger.addAppender(appender);
    }

    @AfterEach
    void cleanUp() {
        interceptorLogger.detachAppender(appender);
        connectionTicketRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("허용되지 않은 destination으로 SEND하면 FRAME_REJECTED가 로그에 남는다")
    void frameRejected_isLogged() throws Exception {
        // given: 실제 STOMP 연결
        User me = saveUser();
        StompSession session = connect(issueWsTicket(me.getId()));

        // when: 프레임 검증 인터셉터가 막는 destination으로 전송
        session.send(DISALLOWED_DESTINATION, "{}");

        // then: 인터셉터가 errorCode=FRAME_REJECTED 로 남긴다
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(errorCodesOf(appender.list))
                        .contains(WebSocketErrorCode.FRAME_REJECTED.name()));
    }

    private List<String> errorCodesOf(List<ILoggingEvent> events) {
        return events.stream()
                .filter(event -> event.getKeyValuePairs() != null)
                .flatMap(event -> event.getKeyValuePairs().stream())
                .filter(pair -> ERROR_CODE.equals(pair.key))
                .map(pair -> String.valueOf(pair.value))
                .toList();
    }
}
