package com.nidus.twinly.world.dto.request;

public final class WorldTrackFormat {

    public static final String TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?";
    public static final String ACTOR_REF = "user:[1-9]\\d{0,17}|npc:\\S+|extra:\\S+";
    public static final String CHANGED_BY = ACTOR_REF + "|card:\\S+";
    public static final String VIEWER_USER_ID = "[1-9]\\d{0,17}";
    public static final String LAYOUT_VERSION = "sha256:\\S+";
    public static final String SECTOR_CODE = "S\\d{3}";

    private WorldTrackFormat() {
    }
}
