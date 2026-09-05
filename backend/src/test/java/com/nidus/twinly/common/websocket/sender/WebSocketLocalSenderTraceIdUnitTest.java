package com.nidus.twinly.common.websocket.sender;

import com.nidus.twinly.common.logging.TraceContext;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.season.dto.websocket.SeasonChangedPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 아웃바운드 스레드로 traceId 를 넘기는 유일한 수단이 메시지 헤더다.
 * Spring 이 헤더를 실제로 실어 보내는지는 목이 아닌 진짜 템플릿으로만 확인할 수 있다.
 */
class WebSocketLocalSenderTraceIdUnitTest {

    private final List<Message<?>> sent = new ArrayList<>();

    private final SimpUserRegistry simpUserRegistry = mock(SimpUserRegistry.class);

    private final WebSocketLocalSender sender = new WebSocketLocalSender(
            new SimpMessagingTemplate(channel()), simpUserRegistry);

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("전송 시점의 traceId 를 메시지 헤더에 싣는다")
    void carries_trace_id_in_header() {
        // given: 릴레이 수신 스레드에 traceId 가 있는 상태
        MDC.put(TraceContext.TRACE_ID, "a3f9c210");

        // when: 접속자에게 전달한다
        sender.sendToUser("42", "/queue/season", body());

        // then: 브로커가 copyHeadersIfAbsent 로 옮겨 주므로 아웃바운드까지 따라간다
        assertThat(sent).hasSize(1);
        System.out.println("HEADERS = " + sent.get(0).getHeaders());
        assertThat(sent.get(0).getHeaders().get(TraceContext.TRACE_ID)).isEqualTo("a3f9c210");
    }

    @Test
    @DisplayName("traceId 가 없으면 헤더를 붙이지 않는다")
    void omits_header_without_trace_id() {
        // when: traceId 없는 스레드에서 전달한다
        sender.sendToUser("42", "/queue/season", body());

        // then: null 을 헤더에 넣으면 MessageHeaders 가 거부한다
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getHeaders()).doesNotContainKey(TraceContext.TRACE_ID);
    }

    @Test
    @DisplayName("전원 전송에서도 유저마다 traceId 와 목적지가 온전하다")
    void carries_trace_id_for_every_user() {
        // given: 이 인스턴스에 두 명이 붙어 있다
        Set<SimpUser> users = Set.of(simpUser("1"), simpUser("2"));
        given(simpUserRegistry.getUsers()).willReturn(users);
        MDC.put(TraceContext.TRACE_ID, "a3f9c210");

        // when: 전원에게 전달한다
        sender.sendToAll("/queue/season", body());

        // then: 헤더 객체를 재사용하면 첫 전송이 immutable 로 바꾸고 목적지까지 써넣는다
        assertThat(sent).hasSize(2);
        assertThat(sent).allSatisfy(message ->
                assertThat(message.getHeaders().get(TraceContext.TRACE_ID)).isEqualTo("a3f9c210"));
        assertThat(sent).extracting(message -> message.getHeaders().get("simpDestination"))
                .containsExactlyInAnyOrder("/user/1/queue/season", "/user/2/queue/season");
    }

    private SimpUser simpUser(String name) {
        SimpUser user = mock(SimpUser.class);
        given(user.getName()).willReturn(name);

        return user;
    }

    private ExecutorSubscribableChannel channel() {
        ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
        channel.subscribe(sent::add);

        return channel;
    }

    private WebSocketEventBody<SeasonChangedPayload> body() {
        return WebSocketEventBody.of(WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(7L));
    }
}
