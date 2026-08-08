package com.nidus.twinly.common.fcm;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSender {

    private static final Set<MessagingErrorCode> REVOKABLE = EnumSet.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.SENDER_ID_MISMATCH);

    private static final Set<ErrorCode> AUTH_FAILURES = EnumSet.of(
            ErrorCode.UNAUTHENTICATED,
            ErrorCode.PERMISSION_DENIED);

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRevoker deviceTokenRevoker;

    public PushSendResult send(List<PushMessage> pushMessages) {
        if (pushMessages.isEmpty()) {
            return PushSendResult.empty();
        }

        BatchResponse response;
        try {
            response = firebaseMessaging.sendEach(pushMessages.stream().map(PushMessage::message).toList());
        } catch (FirebaseMessagingException e) {
            log.warn("푸시 발송이 중단되었습니다. count={}", pushMessages.size(), e);
            return PushSendResult.cancelled();
        }

        deviceTokenRevoker.revoke(collectRevokableTokens(pushMessages, response));

        return new PushSendResult(response.getSuccessCount(), response.getFailureCount(), hasAuthFailure(response));
    }

    private boolean hasAuthFailure(BatchResponse response) {
        return response.getResponses().stream()
                .filter(each -> !each.isSuccessful())
                .anyMatch(each -> AUTH_FAILURES.contains(each.getException().getErrorCode()));
    }

    private List<String> collectRevokableTokens(List<PushMessage> pushMessages, BatchResponse response) {
        List<String> revokable = new ArrayList<>();

        for (int i = 0; i < response.getResponses().size(); i++) {
            SendResponse each = response.getResponses().get(i);
            if (each.isSuccessful()) {
                continue;
            }

            MessagingErrorCode errorCode = each.getException().getMessagingErrorCode();

            if (REVOKABLE.contains(errorCode)) {
                revokable.add(pushMessages.get(i).token());
            } else {
                log.warn("푸시 발송 실패. errorCode={}", errorCode, each.getException());
            }
        }

        return revokable;
    }
}
