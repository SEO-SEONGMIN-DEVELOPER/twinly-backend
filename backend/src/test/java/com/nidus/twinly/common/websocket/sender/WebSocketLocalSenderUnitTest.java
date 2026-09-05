package com.nidus.twinly.common.websocket.sender;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.season.dto.websocket.SeasonChangedPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 릴레이를 거치지 않고 <b>이 인스턴스에 붙어 있는 접속자</b>에게만 전달하는 경로.
 * 릴레이 수신(Dispatcher)과 draining 로컬 예고가 이 구현을 공유한다.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketLocalSenderUnitTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    SimpUserRegistry simpUserRegistry;

    @InjectMocks
    WebSocketLocalSender sender;

    @Test
    @DisplayName("특정 유저 전송은 접속자 목록을 조회하지 않는다")
    void sendToUser() {
        // given
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(7L));

        // when
        sender.sendToUser("42", "/queue/season", body);

        // then
        then(messagingTemplate).should().convertAndSendToUser(eq("42"), eq("/queue/season"), eq(body), anyMap());
        then(simpUserRegistry).should(never()).getUsers();
    }

    @Test
    @DisplayName("전원 전송은 이 인스턴스에 접속한 유저마다 개인 큐로 한 번씩 보낸다")
    void sendToAll() {
        // given: 이 인스턴스에는 1번과 2번이 붙어 있다
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(7L));
        Set<SimpUser> connected = Set.of(simpUser("1"), simpUser("2"));
        given(simpUserRegistry.getUsers()).willReturn(connected);

        // when
        sender.sendToAll("/queue/season", body);

        // then
        then(messagingTemplate).should().convertAndSendToUser(eq("1"), eq("/queue/season"), eq(body), anyMap());
        then(messagingTemplate).should().convertAndSendToUser(eq("2"), eq("/queue/season"), eq(body), anyMap());
    }

    @Test
    @DisplayName("접속자가 없으면 아무에게도 보내지 않는다")
    void sendToAll_withNoConnectedUsers() {
        // given: 재기동 직후처럼 접속자가 한 명도 없는 상태
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(7L));
        given(simpUserRegistry.getUsers()).willReturn(Set.of());

        // when
        sender.sendToAll("/queue/season", body);

        // then
        then(messagingTemplate).shouldHaveNoInteractions();
    }

    private SimpUser simpUser(String name) {
        SimpUser user = mock(SimpUser.class);
        given(user.getName()).willReturn(name);
        return user;
    }
}
