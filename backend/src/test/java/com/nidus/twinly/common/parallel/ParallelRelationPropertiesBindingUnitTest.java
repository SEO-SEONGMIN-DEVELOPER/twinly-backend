package com.nidus.twinly.common.parallel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelRelationPropertiesBindingUnitTest {

    @Test
    @DisplayName("application.yaml의 경계값이 모든 등급으로 바인딩된다")
    void thresholds_bind_to_every_relation() throws IOException {
        // given: 실제 운영 설정 파일
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));

        // when: parallel 설정만 바인딩한다
        ParallelRelationProperties properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("parallel", ParallelRelationProperties.class)
                .orElseThrow(() -> new IllegalStateException("parallel 설정을 바인딩하지 못했습니다"));

        // then: yaml의 케밥 표기가 enum·필드명과 하나도 어긋나지 않는다
        assertThat(properties.relationThresholds()).containsOnlyKeys(ParallelRelationType.values());
        assertThat(properties.relationThresholds().get(ParallelRelationType.ENEMY)).isEqualTo(0);
        assertThat(properties.relationThresholds().get(ParallelRelationType.BEST_FRIEND)).isEqualTo(85);
        assertThat(properties.similarityScore().rawMean()).isEqualTo(0.457);
        assertThat(properties.similarityScore().rawStdDev()).isEqualTo(0.084);
        assertThat(properties.similarityScore().min()).isEqualTo(10);
        assertThat(properties.similarityScore().max()).isEqualTo(99);
        assertThat(properties.similarityScore().quantiles()).hasSize(4);
        assertThat(properties.similarityScore().quantiles().getFirst().percentile()).isEqualTo(0.10);
        assertThat(properties.similarityScore().quantiles().getFirst().score()).isEqualTo(30);
        assertThat(properties.similarityScore().quantiles().getLast().percentile()).isEqualTo(0.90);
        assertThat(properties.similarityScore().quantiles().getLast().score()).isEqualTo(85);
    }
}
