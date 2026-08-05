package com.nidus.twinly.common.fcm;

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

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRevoker deviceTokenRevoker;

    public void send(List<PushMessage> pushMessages) {
        if (pushMessages.isEmpty()) {
            return;
        }

        BatchResponse response;
        try {
            response = firebaseMessaging.sendEach(pushMessages.stream().map(PushMessage::message).toList());
        } catch (FirebaseMessagingException e) {
            log.warn("푸시 발송에 실패했습니다. count={}", pushMessages.size(), e);
            return;
        }

        deviceTokenRevoker.revoke(collectRevokableTokens(pushMessages, response));
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
