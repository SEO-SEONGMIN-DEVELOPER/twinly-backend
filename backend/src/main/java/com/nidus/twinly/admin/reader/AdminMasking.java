package com.nidus.twinly.admin.reader;

public final class AdminMasking {

    private static final int VISIBLE_LOCAL_LENGTH = 2;
    private static final String MASK = "****";

    private AdminMasking() {
    }

    public static String email(String email) {
        if (email == null) {
            return null;
        }

        int at = email.indexOf('@');

        if (at < 0) {
            return MASK;
        }

        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(VISIBLE_LOCAL_LENGTH, local.length()));

        return visible + MASK + email.substring(at);
    }
}
