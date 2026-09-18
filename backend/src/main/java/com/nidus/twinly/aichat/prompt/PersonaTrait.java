package com.nidus.twinly.aichat.prompt;

import com.nidus.twinly.common.persona.PersonaDimension;

public record PersonaTrait(
        PersonaDimension dimension,
        String explanation
) {
}
