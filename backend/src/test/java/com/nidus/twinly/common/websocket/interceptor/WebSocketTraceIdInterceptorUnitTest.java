package com.nidus.twinly.common.websocket.interceptor;

import com.nidus.twinly.common.logging.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketTraceIdInterceptorUnitTest {

    private final WebSocketTraceIdInterceptor interceptor = new WebSocketTraceIdInterceptor();

    private final Message<String> message = MessageBuilder.withPayload("frame").build();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("메시지 처리 직전에 traceId 를 발급한다")
    void issues_trace_id_before_handling() {
        // when: 프레임 처리가 시작된다
        interceptor.beforeHandle(message, null, null);

        // then: STOMP 는 서블릿 필터를 타지 않으므로 여기서 만들어 줘야 한다
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNotNull();
    }

    @Test
    @DisplayName("메시지마다 다른 traceId 를 발급한다")
    void issues_different_trace_id_per_message() {
        // given: 첫 프레임 처리
        interceptor.beforeHandle(message, null, null);
        String first = MDC.get(TraceContext.TRACE_ID);
        interceptor.afterMessageHandled(message, null, null, null);

        // when: 다음 프레임 처리
        interceptor.beforeHandle(message, null, null);

        // then: 채팅 두 건이 한 덩어리로 보이면 안 된다
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNotEqualTo(first);
    }

    @Test
    @DisplayName("헤더에 traceId 가 실려 오면 이어받는다")
    void inherits_trace_id_from_header() {
        // given: 아웃바운드 메시지는 보낸 스레드의 traceId 를 헤더에 달고 온다
        Message<String> carried = MessageBuilder.withPayload("frame")
                .setHeader(TraceContext.TRACE_ID, "a3f9c210")
                .build();

        // when: 처리가 시작된다
        interceptor.beforeHandle(carried, null, null);

        // then: 새로 발급하면 소켓 전송 실패 로그가 원인과 끊긴다
        assertThat(MDC.get(TraceContext.TRACE_ID)).isEqualTo("a3f9c210");
    }

    @Test
    @DisplayName("처리가 끝나면 traceId 를 지운다")
    void clears_trace_id_after_handling() {
        // given: 처리 중인 프레임
        interceptor.beforeHandle(message, null, null);

        // when: 처리가 끝난다
        interceptor.afterMessageHandled(message, null, null, null);

        // then: 인바운드 채널도 스레드를 재사용한다
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
    }
}
