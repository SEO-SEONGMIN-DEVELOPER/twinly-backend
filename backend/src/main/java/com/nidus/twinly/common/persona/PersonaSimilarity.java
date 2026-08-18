package com.nidus.twinly.common.persona;

import java.util.Map;

public record PersonaSimilarity(
        double score,
        Map<PersonaDimension, Double> dimensionScores
) {
}
