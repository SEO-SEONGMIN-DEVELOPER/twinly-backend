package com.nidus.twinly.me.dto.result;

import java.util.List;

public record MeConsentsResult(
        List<MeConsentsItemResult> consents
) {
}
