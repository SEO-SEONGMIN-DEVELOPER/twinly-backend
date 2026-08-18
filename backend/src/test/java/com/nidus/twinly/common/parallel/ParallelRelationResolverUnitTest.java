package com.nidus.twinly.common.parallel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelRelationResolverUnitTest {

    ParallelRelationLoader loader;
    ParallelRelationResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        // given: 실제 문구 파일과 운영 기본값과 같은 경계값으로 조립한다
        loader = new ParallelRelationLoader(new ObjectMapper(), new ParallelStoryRenderer());
        loader.load();

        resolver = new ParallelRelationResolver(new ParallelRelationProperties(thresholds()), loader, new ParallelStoryRenderer());
        resolver.validateThresholds();
    }

    @Test
    @DisplayName("점수 구간에 맞는 등급으로 매핑된다")
    void resolve_maps_score_to_relation() {
        // when & then
        assertThat(resolver.resolve(0.00, "지훈", "서연").relation()).isEqualTo(ParallelRelation.ENEMY);
        assertThat(resolver.resolve(0.40, "지훈", "서연").relation()).isEqualTo(ParallelRelation.STRANGER);
        assertThat(resolver.resolve(0.55, "지훈", "서연").relation()).isEqualTo(ParallelRelation.AWKWARD);
        assertThat(resolver.resolve(0.65, "지훈", "서연").relation()).isEqualTo(ParallelRelation.CLOSE);
        assertThat(resolver.resolve(1.00, "지훈", "서연").relation()).isEqualTo(ParallelRelation.BEST_FRIEND);
    }

    @Test
    @DisplayName("경계값과 정확히 같은 점수는 위쪽 등급을 받는다")
    void score_on_the_boundary_belongs_to_the_upper_relation() {
        // when & then: 0.72는 CLOSE가 아니라 BEST_FRIEND다
        assertThat(resolver.resolve(0.72, "지훈", "서연").relation()).isEqualTo(ParallelRelation.BEST_FRIEND);
        assertThat(resolver.resolve(0.7199, "지훈", "서연").relation()).isEqualTo(ParallelRelation.CLOSE);
    }

    @Test
    @DisplayName("이야기의 이름 자리에 두 사람의 이름이 채워진다")
    void resolve_fills_both_names() {
        // when
        ParallelRelationResult result = resolver.resolve(1.0, "지훈", "서연");

        // then: 공유 화면에 그대로 나갈 문장이므로 자리표시자가 남거나 조사가 틀리면 안 된다
        assertThat(result.title()).isEqualTo("아무때나 전화해도 좋아하는 사이");
        assertThat(result.story()).startsWith("지훈과 서연은 다른 평행우주에서는");
        assertThat(result.story()).doesNotContain("{A", "{B");
    }

    @Test
    @DisplayName("이름을 바꿔 넣으면 이야기 속 두 사람의 역할도 바뀐다")
    void names_are_not_swapped() {
        // when
        ParallelRelationResult result = resolver.resolve(0.0, "지훈", "서연");
        ParallelRelationResult swapped = resolver.resolve(0.0, "서연", "지훈");

        // then: 동아리를 나간 쪽이 서로 달라진다
        assertThat(result.story()).contains("결국 서연이 동아리를 나가면서");
        assertThat(swapped.story()).contains("결국 지훈이 동아리를 나가면서");
    }

    @Test
    @DisplayName("경계값이 빠진 등급이 있으면 기동 시점에 실패한다")
    void missing_threshold_fails_fast() {
        // given: BEST_FRIEND 경계값을 빠뜨린 설정
        Map<ParallelRelation, Double> incomplete = thresholds();
        incomplete.remove(ParallelRelation.BEST_FRIEND);

        ParallelRelationResolver brokenResolver =
                new ParallelRelationResolver(new ParallelRelationProperties(incomplete), loader, new ParallelStoryRenderer());

        // when & then: 첫 요청이 아니라 기동 때 터진다
        assertThatThrownBy(brokenResolver::validateThresholds)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEST_FRIEND");
    }

    private Map<ParallelRelation, Double> thresholds() {
        Map<ParallelRelation, Double> thresholds = new EnumMap<>(ParallelRelation.class);

        thresholds.put(ParallelRelation.ENEMY, 0.0);
        thresholds.put(ParallelRelation.STRANGER, 0.36);
        thresholds.put(ParallelRelation.AWKWARD, 0.48);
        thresholds.put(ParallelRelation.CLOSE, 0.60);
        thresholds.put(ParallelRelation.BEST_FRIEND, 0.72);

        return thresholds;
    }
}
