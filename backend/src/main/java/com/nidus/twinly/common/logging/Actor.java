package com.nidus.twinly.common.logging;

public final class Actor {

    private static final String USER_PREFIX = "user:";
    private static final String ANON_PREFIX = "anon:";

    private Actor() {
    }

    public static String user(Long userId) {
        return USER_PREFIX + userId;
    }

    public static String anonSession(Long anonSessionId) {
        return ANON_PREFIX + anonSessionId;
    }
}
