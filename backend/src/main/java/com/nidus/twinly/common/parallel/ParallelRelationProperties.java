package com.nidus.twinly.common.parallel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "parallel")
public record ParallelRelationProperties(
        @NotEmpty Map<ParallelRelationType, Integer> relationThresholds,
        @NotNull @Valid SimilarityScore similarityScore
) {
    public record SimilarityScore(
            @NotNull Double rawMean,
            @NotNull @Positive Double rawStdDev,
            @NotNull Integer min,
            @NotNull Integer max,
            @NotEmpty List<@Valid Quantile> quantiles
    ) {
        public record Quantile(
                @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double percentile,
                @NotNull Integer score
        ) {
        }
    }
}
