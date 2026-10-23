package com.nidus.twinly.schoolverification.storage;

public record StudentIdPhotoOwner(
        String pathSegment
) {

    public static StudentIdPhotoOwner anonSession(Long anonSessionId) {
        return new StudentIdPhotoOwner("anon-" + anonSessionId);
    }

    public static StudentIdPhotoOwner user(Long userId) {
        return new StudentIdPhotoOwner("user-" + userId);
    }
}
