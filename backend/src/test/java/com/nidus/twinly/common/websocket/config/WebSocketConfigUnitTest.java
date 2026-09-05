package com.nidus.twinly.common.websocket.config;

import com.nidus.twinly.common.websocket.interceptor.WebSocketErrorInterceptor;
import com.nidus.twinly.common.websocket.interceptor.WebSocketFrameValidationInterceptor;
import com.nidus.twinly.common.websocket.interceptor.WebSocketTraceIdInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.TaskExecutorRegistration;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class WebSocketConfigUnitTest {

    private final WebSocketTraceIdInterceptor traceIdInterceptor = new WebSocketTraceIdInterceptor();
    private final WebSocketErrorInterceptor errorInterceptor = new WebSocketErrorInterceptor();
    private final WebSocketFrameValidationInterceptor frameValidationInterceptor = new WebSocketFrameValidationInterceptor();

    @Test
    @DisplayName("아웃바운드 채널도 traceId 인터셉터를 가장 먼저 태운다")
    void outbound_channel_carries_trace_id() {
        // given: 실제 설정
        WebSocketConfig config = new WebSocketConfig(
                null, frameValidationInterceptor, errorInterceptor, traceIdInterceptor, null);

        ChannelRegistration registration = mock(ChannelRegistration.class);

        // when: 아웃바운드 채널을 구성한다
        config.configureClientOutboundChannel(registration);

        // then: 소켓 전송 실패 로그도 traceId 를 달아야 한다
        ArgumentCaptor<ChannelInterceptor[]> captor = ArgumentCaptor.forClass(ChannelInterceptor[].class);
        then(registration).should().interceptors(captor.capture());

        List<ChannelInterceptor> interceptors = List.of(captor.getValue());
        assertThat(interceptors.indexOf(traceIdInterceptor))
                .isLessThan(interceptors.indexOf(errorInterceptor));
    }

    @Test
    @DisplayName("인바운드 채널은 traceId 인터셉터를 가장 먼저 태운다")
    void trace_id_interceptor_comes_first() {
        // given: 실제 설정
        WebSocketConfig config = new WebSocketConfig(
                null, frameValidationInterceptor, errorInterceptor, traceIdInterceptor, null);

        ChannelRegistration registration = mock(ChannelRegistration.class);
        TaskExecutorRegistration taskExecutorRegistration = mock(TaskExecutorRegistration.class);
        given(registration.taskExecutor()).willReturn(taskExecutorRegistration);
        given(taskExecutorRegistration.corePoolSize(1)).willReturn(taskExecutorRegistration);

        // when: 인바운드 채널을 구성한다
        config.configureClientInboundChannel(registration);

        // then: afterMessageHandled 는 역순으로 돌기 때문에, 가장 먼저 등록해야
        //       에러 인터셉터의 로그가 MDC 가 지워지기 전에 남는다
        ArgumentCaptor<ChannelInterceptor[]> captor = ArgumentCaptor.forClass(ChannelInterceptor[].class);
        then(registration).should().interceptors(captor.capture());

        List<ChannelInterceptor> interceptors = List.of(captor.getValue());
        assertThat(interceptors.indexOf(traceIdInterceptor))
                .isLessThan(interceptors.indexOf(errorInterceptor));
    }
}
