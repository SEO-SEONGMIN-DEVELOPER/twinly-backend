package com.nidus.twinly.common.docs;

import java.util.List;

public record ErrorCodeDocsResponse(
        List<ErrorCodeDoc> errors
) {
}
