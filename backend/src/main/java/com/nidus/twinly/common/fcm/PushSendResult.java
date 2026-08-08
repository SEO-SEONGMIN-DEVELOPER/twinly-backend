package com.nidus.twinly.common.fcm;

public record PushSendResult(int succeeded, int failed, boolean authFailed) {

    public static final int UNKNOWN = -1;

    public static PushSendResult empty() {
        return new PushSendResult(0, 0, false);
    }

    public static PushSendResult cancelled() {
        return new PushSendResult(UNKNOWN, UNKNOWN, false);
    }
}
