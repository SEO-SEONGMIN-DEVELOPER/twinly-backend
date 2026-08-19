package com.nidus.twinly.common.parallel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void relation_of_maps_score_to_relation() {
        // when & then
        assertThat(resolver.relationOf(0.00)).isEqualTo(ParallelRelationType.ENEMY);
        assertThat(resolver.relationOf(0.40)).isEqualTo(ParallelRelationType.STRANGER);
        assertThat(resolver.relationOf(0.55)).isEqualTo(ParallelRelationType.AWKWARD);
        assertThat(resolver.relationOf(0.65)).isEqualTo(ParallelRelationType.CLOSE);
        assertThat(resolver.relationOf(1.00)).isEqualTo(ParallelRelationType.BEST_FRIEND);
    }

    @Test
    @DisplayName("경계값과 정확히 같은 점수는 위쪽 등급을 받는다")
    void score_on_the_boundary_belongs_to_the_upper_relation() {
        // when & then: 0.72는 CLOSE가 아니라 BEST_FRIEND다
        assertThat(resolver.relationOf(0.72)).isEqualTo(ParallelRelationType.BEST_FRIEND);
        assertThat(resolver.relationOf(0.7199)).isEqualTo(ParallelRelationType.CLOSE);
    }

    @Test
    @DisplayName("이야기의 이름 자리에 두 사람의 이름이 채워진다")
    void render_fills_both_names() {
        // when
        ParallelRelationResult result = resolver.render(ParallelRelationType.BEST_FRIEND, 0, "지훈", "서연");

        // then: 공유 화면에 그대로 나갈 문장이므로 자리표시자가 남거나 조사가 틀리면 안 된다
        assertThat(result.title()).isIn(titlesOf(ParallelRelationType.BEST_FRIEND));
        assertThat(result.story()).startsWith("지훈과 서연은 다른 평행우주에서는");
        assertThat(result.story()).doesNotContain("{A", "{B");
    }

    @Test
    @DisplayName("이름을 바꿔 넣으면 이야기 속 두 사람의 역할도 바뀐다")
    void names_are_not_swapped() {
        // when
        ParallelRelationResult result = resolver.render(ParallelRelationType.ENEMY, 0, "지훈", "서연");
        ParallelRelationResult swapped = resolver.render(ParallelRelationType.ENEMY, 0, "서연", "지훈");

        // then: 먼저 불리는 사람이 서로 달라진다
        assertThat(result.story()).startsWith("지훈과 서연은");
        assertThat(swapped.story()).startsWith("서연과 지훈은");
    }

    @Test
    @DisplayName("같은 등급, 같은 이야기 번호는 언제나 같은 이야기를 준다")
    void same_story_index_renders_the_same_story() {
        // when & then: 결과를 다시 열어도 문장이 흔들리면 안 된다
        assertThat(resolver.render(ParallelRelationType.CLOSE, 7, "지훈", "서연").story())
                .isEqualTo(resolver.render(ParallelRelationType.CLOSE, 7, "지훈", "서연").story());
    }

    @Test
    @DisplayName("이야기 번호가 문구 수를 넘어도 렌더링된다")
    void story_index_out_of_range_still_renders() {
        // given: 문구를 줄이면 저장해둔 번호가 범위를 벗어날 수 있다
        int outOfRange = titlesOf(ParallelRelationType.CLOSE).size() + 3;

        // when & then: 번호가 깨졌다고 과거 결과가 조회 불가가 되면 안 된다
        assertThat(resolver.render(ParallelRelationType.CLOSE, outOfRange, "지훈", "서연").title())
                .isIn(titlesOf(ParallelRelationType.CLOSE));
    }

    @Test
    @DisplayName("이야기 번호는 매번 무작위로 뽑힌다")
    void pick_story_index_is_random() {
        // when: 같은 등급으로 여러 번 뽑는다
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            indexes.add(resolver.pickStoryIndex(ParallelRelationType.BEST_FRIEND));
        }

        // then: 항상 같은 번호만 나오면 무작위가 아니다
        assertThat(indexes).hasSizeGreaterThan(1);
        assertThat(indexes).allMatch(index -> index >= 0 && index < titlesOf(ParallelRelationType.BEST_FRIEND).size());
    }

    @Test
    @DisplayName("경계값이 빠진 등급이 있으면 기동 시점에 실패한다")
    void missing_threshold_fails_fast() {
        // given: BEST_FRIEND 경계값을 빠뜨린 설정
        Map<ParallelRelationType, Double> incomplete = thresholds();
        incomplete.remove(ParallelRelationType.BEST_FRIEND);

        ParallelRelationResolver brokenResolver =
                new ParallelRelationResolver(new ParallelRelationProperties(incomplete), loader, new ParallelStoryRenderer());

        // when & then: 첫 요청이 아니라 기동 때 터진다
        assertThatThrownBy(brokenResolver::validateThresholds)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEST_FRIEND");
    }

    private List<String> titlesOf(ParallelRelationType relation) {
        return loader.getContents(relation).stream()
                .map(ParallelRelationContent::title)
                .toList();
    }

    private Map<ParallelRelationType, Double> thresholds() {
        Map<ParallelRelationType, Double> thresholds = new EnumMap<>(ParallelRelationType.class);

        thresholds.put(ParallelRelationType.ENEMY, 0.0);
        thresholds.put(ParallelRelationType.STRANGER, 0.36);
        thresholds.put(ParallelRelationType.AWKWARD, 0.48);
        thresholds.put(ParallelRelationType.CLOSE, 0.60);
        thresholds.put(ParallelRelationType.BEST_FRIEND, 0.72);

        return thresholds;
    }
}
