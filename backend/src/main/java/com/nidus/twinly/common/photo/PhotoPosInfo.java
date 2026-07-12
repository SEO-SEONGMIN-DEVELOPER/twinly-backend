package com.nidus.twinly.common.photo;

public record PhotoPosInfo(
        StartPos startPos,
        Integer width,
        Integer height
) {
    public record StartPos(Integer x, Integer y) {}
}