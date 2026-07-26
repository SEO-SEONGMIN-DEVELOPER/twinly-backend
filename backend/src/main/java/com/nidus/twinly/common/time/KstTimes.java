package com.nidus.twinly.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class KstTimes {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstTimes() {
    }

    public static OffsetDateTime toKstOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime.atZone(KST).toOffsetDateTime();
    }
}
