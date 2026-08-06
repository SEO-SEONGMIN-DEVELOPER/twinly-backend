package com.nidus.twinly.common.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class KstTimes {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstTimes() {
    }

    public static OffsetDateTime toKstOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZONE).toOffsetDateTime();
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
