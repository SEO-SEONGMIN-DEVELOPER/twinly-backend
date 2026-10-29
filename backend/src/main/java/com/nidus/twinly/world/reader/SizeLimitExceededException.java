package com.nidus.twinly.world.reader;

import java.io.IOException;

final class SizeLimitExceededException extends IOException {

    SizeLimitExceededException(long maxBytes) {
        super("본문이 " + maxBytes + " 바이트를 넘었습니다.");
    }
}
