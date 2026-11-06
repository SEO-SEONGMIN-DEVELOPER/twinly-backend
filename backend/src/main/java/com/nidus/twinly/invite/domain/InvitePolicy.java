package com.nidus.twinly.invite.domain;

import java.time.Duration;

public final class InvitePolicy {

    public static final int GOAL = 5;
    public static final Duration REWARD_DURATION = Duration.ofDays(30);

    private static final String SHARE_MESSAGE_FORMAT = "트윈리에서 내 트윈을 만들어 봐! 가입할 때 초대 코드 %1$s를 넣어 줘.\nhttps://trytwinly.com/i/%1$s";

    private InvitePolicy() {
    }

    public static String shareMessage(String code) {
        return SHARE_MESSAGE_FORMAT.formatted(code);
    }
}
