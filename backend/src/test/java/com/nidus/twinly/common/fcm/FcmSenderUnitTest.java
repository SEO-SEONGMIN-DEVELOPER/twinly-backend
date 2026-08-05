package com.nidus.twinly.common.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FcmSenderUnitTest {

    @Mock
    FirebaseMessaging firebaseMessaging;

    @Mock
    DeviceTokenRevoker deviceTokenRevoker;

    @InjectMocks
    FcmSender fcmSender;

    @Test
    @DisplayName("보낼 메시지가 없으면 FCM을 호출하지 않는다")
    void send_skips_when_empty() throws Exception {
        // when: 빈 목록으로 발송
        fcmSender.send(List.of());

        // then: 빈 목록으로 sendEach를 부르면 예외가 나므로 아예 호출하지 않는다
        then(firebaseMessaging).should(never()).sendEach(anyList());
        then(deviceTokenRevoker).should(never()).revoke(anyList());
    }

    @Test
    @DisplayName("전부 성공하면 폐기할 토큰이 없다")
    void send_all_success() throws Exception {
        // given: 두 건 모두 성공
        BatchResponse response = batchResponse(success(), success());
        given(firebaseMessaging.sendEach(anyList())).willReturn(response);

        // when: 발송
        fcmSender.send(List.of(pushMessage("token-A"), pushMessage("token-B")));

        // then: 빈 목록으로 위임된다
        then(deviceTokenRevoker).should().revoke(List.of());
    }

    @Test
    @DisplayName("UNREGISTERED·SENDER_ID_MISMATCH 는 해당 토큰만 폐기한다")
    void send_revokes_dead_tokens() throws Exception {
        // given: 1번은 성공, 2번은 앱 삭제, 3번은 다른 프로젝트 토큰
        BatchResponse response = batchResponse(
                success(),
                failure(MessagingErrorCode.UNREGISTERED),
                failure(MessagingErrorCode.SENDER_ID_MISMATCH));
        given(firebaseMessaging.sendEach(anyList())).willReturn(response);

        // when: 발송
        fcmSender.send(List.of(pushMessage("token-A"), pushMessage("token-B"), pushMessage("token-C")));

        // then: 실패한 두 토큰만 폐기된다 (응답 순서로 토큰을 짝지어야 맞는다)
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.captor();
        then(deviceTokenRevoker).should().revoke(captor.capture());
        assertThat(captor.getValue()).containsExactly("token-B", "token-C");
    }

    @Test
    @DisplayName("INVALID_ARGUMENT 는 토큰을 폐기하지 않는다")
    void send_does_not_revoke_on_invalid_argument() throws Exception {
        // given: 페이로드 버그로도 발생하는 코드. 폐기했다간 빌더 버그 한 번에 전 사용자 토큰이 날아간다
        BatchResponse response = batchResponse(
                failure(MessagingErrorCode.INVALID_ARGUMENT),
                failure(MessagingErrorCode.INVALID_ARGUMENT));
        given(firebaseMessaging.sendEach(anyList())).willReturn(response);

        // when: 발송
        fcmSender.send(List.of(pushMessage("token-A"), pushMessage("token-B")));

        // then: 하나도 폐기하지 않는다
        then(deviceTokenRevoker).should().revoke(List.of());
    }

    @Test
    @DisplayName("THIRD_PARTY_AUTH_ERROR 는 토큰을 폐기하지 않는다")
    void send_does_not_revoke_on_apns_config_error() throws Exception {
        // given: APNs 인증 키 미설정. 설정 문제지 토큰 문제가 아니다
        BatchResponse response = batchResponse(
                failure(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR));
        given(firebaseMessaging.sendEach(anyList())).willReturn(response);

        // when: 발송
        fcmSender.send(List.of(pushMessage("token-A")));

        // then: 폐기하지 않는다 (키를 등록하면 그대로 살아날 토큰이다)
        then(deviceTokenRevoker).should().revoke(List.of());
    }

    @Test
    @DisplayName("일시 장애(UNAVAILABLE)는 토큰을 폐기하지 않는다")
    void send_does_not_revoke_on_transient_error() throws Exception {
        // given: FCM 일시 장애
        BatchResponse response = batchResponse(
                failure(MessagingErrorCode.UNAVAILABLE));
        given(firebaseMessaging.sendEach(anyList())).willReturn(response);

        // when: 발송
        fcmSender.send(List.of(pushMessage("token-A")));

        // then: 폐기하지 않는다
        then(deviceTokenRevoker).should().revoke(List.of());
    }

    @Test
    @DisplayName("요청 자체가 실패하면 토큰을 건드리지 않고 조용히 끝낸다")
    void send_swallows_request_failure() throws Exception {
        // given: 네트워크 단절 등으로 요청 전체가 실패
        willThrow(mock(FirebaseMessagingException.class)).given(firebaseMessaging).sendEach(anyList());

        // when & then: 예외가 밖으로 나가지 않는다 (푸시 실패가 본 기능을 실패시키면 안 된다)
        fcmSender.send(List.of(pushMessage("token-A")));

        then(deviceTokenRevoker).should(never()).revoke(anyList());
    }

    private PushMessage pushMessage(String token) {
        return new PushMessage(token, Message.builder().setToken(token).build());
    }

    private BatchResponse batchResponse(SendResponse... responses) {
        BatchResponse batchResponse = mock(BatchResponse.class);
        given(batchResponse.getResponses()).willReturn(List.of(responses));
        return batchResponse;
    }

    private SendResponse success() {
        SendResponse sendResponse = mock(SendResponse.class);
        given(sendResponse.isSuccessful()).willReturn(true);
        return sendResponse;
    }

    private SendResponse failure(MessagingErrorCode errorCode) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(errorCode);

        SendResponse sendResponse = mock(SendResponse.class);
        given(sendResponse.isSuccessful()).willReturn(false);
        given(sendResponse.getException()).willReturn(exception);
        return sendResponse;
    }
}
