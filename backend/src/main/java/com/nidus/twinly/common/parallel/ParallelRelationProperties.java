package com.nidus.twinly.common.parallel;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "parallel")
public record ParallelRelationProperties(
        @NotEmpty Map<ParallelRelation, Double> relationThresholds
) {
}
