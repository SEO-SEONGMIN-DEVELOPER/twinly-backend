package com.nidus.twinly.admin.domain;

public enum MemberSort {
    SIGNUP("u.created_at DESC, u.id DESC"),
    ID("u.id DESC");

    private final String orderBy;

    MemberSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public String orderBy() {
        return orderBy;
    }
}
