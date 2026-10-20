package com.nidus.twinly.admin.domain;

import com.nidus.twinly.user.entity.User;

public enum MemberStatus {
    ACTIVE,
    WITHDRAWING,
    WITHDRAWN;

    public static MemberStatus of(User user) {
        if (user.isDeleted()) {
            return WITHDRAWN;
        }

        return user.isWithdrawn() ? WITHDRAWING : ACTIVE;
    }
}
