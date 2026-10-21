package com.nidus.twinly.admin.domain;

public enum ChatRoomSort {
    LATEST("r.id DESC"),
    MESSAGES("(SELECT COUNT(*) FROM chats c WHERE c.room_id = r.id) DESC, r.id DESC");

    private final String orderBy;

    ChatRoomSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public String orderBy() {
        return orderBy;
    }
}
