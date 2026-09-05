package com.nidus.twinly.common.fcm;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.nidus.twinly.common.logging.Actor;
import com.nidus.twinly.common.logging.ErrorLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
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

    private static final String MIXED = "MIXED";

    private static final String PUSH_TYPE = "pushType";
    private static final String MESSAGING_ERROR_CODE = "messagingErrorCode";
    private static final String REQUESTED = "requested";
    private static final String SUCCEEDED = "succeeded";
    private static final String FAILED = "failed";
    private static final String REVOKED = "revoked";
    private static final String AUTH_FAILED = "authFailed";

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
            ErrorLog.error(log, e.getErrorCode().name(), null, e)
                    .addKeyValue(REQUESTED, pushMessages.size())
                    .log("푸시 발송이 중단되었습니다");
            return PushSendResult.cancelled();
        }

        List<String> revokableTokens = collectRevokableTokens(pushMessages, response);
        deviceTokenRevoker.revoke(revokableTokens);

        boolean authFailed = hasAuthFailure(response);

        log.atLevel(authFailed ? Level.ERROR : Level.INFO)
                .addKeyValue(PUSH_TYPE, pushType(pushMessages))
                .addKeyValue(REQUESTED, pushMessages.size())
                .addKeyValue(SUCCEEDED, response.getSuccessCount())
                .addKeyValue(FAILED, response.getFailureCount())
                .addKeyValue(REVOKED, revokableTokens.size())
                .addKeyValue(AUTH_FAILED, authFailed)
                .log("푸시 발송 결과");

        return new PushSendResult(response.getSuccessCount(), response.getFailureCount(), authFailed);
    }

    private String pushType(List<PushMessage> pushMessages) {
        return pushMessages.stream().map(PushMessage::type).distinct().count() == 1
                ? pushMessages.getFirst().type().name()
                : MIXED;
    }

    private boolean hasAuthFailure(BatchResponse response) {
        return response.getResponses().stream()
                .filter(each -> !each.isSuccessful())
                .map(SendResponse::getException)
                .anyMatch(exception -> exception.getMessagingErrorCode() == null
                        && AUTH_FAILURES.contains(exception.getErrorCode()));
    }

    private List<String> collectRevokableTokens(List<PushMessage> pushMessages, BatchResponse response) {
        List<String> revokable = new ArrayList<>();

        for (int i = 0; i < response.getResponses().size(); i++) {
            SendResponse each = response.getResponses().get(i);
            if (each.isSuccessful()) {
                continue;
            }

            PushMessage pushMessage = pushMessages.get(i);
            FirebaseMessagingException exception = each.getException();
            MessagingErrorCode messagingErrorCode = exception.getMessagingErrorCode();

            if (REVOKABLE.contains(messagingErrorCode)) {
                revokable.add(pushMessage.token());
            } else {
                ErrorLog.warn(log, exception.getErrorCode().name(), Actor.user(pushMessage.userId()), exception)
                        .addKeyValue(PUSH_TYPE, pushMessage.type())
                        .addKeyValue(MESSAGING_ERROR_CODE, messagingErrorCode)
                        .log("푸시 발송에 실패했습니다");
            }
        }

        return revokable;
    }
}
