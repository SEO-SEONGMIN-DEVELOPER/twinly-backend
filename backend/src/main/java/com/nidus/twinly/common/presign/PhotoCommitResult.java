package com.nidus.twinly.common.presign;

public record PhotoCommitResult(
        String photoUrl,
        long sourceBytes
) {
}
