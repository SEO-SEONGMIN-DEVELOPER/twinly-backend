package com.nidus.twinly.admin.domain;

public enum ChatEntryState {
    BOTH,
    ONE,
    NONE;

    public static ChatEntryState of(long enteredCount) {
        if (enteredCount >= 2) {
            return BOTH;
        }

        return enteredCount == 1 ? ONE : NONE;
    }
}
