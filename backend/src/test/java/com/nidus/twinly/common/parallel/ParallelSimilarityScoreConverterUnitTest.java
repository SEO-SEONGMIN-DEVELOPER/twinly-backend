package com.nidus.twinly.common.parallel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ParallelSimilarityScoreConverterUnitTest {

    private static final double RAW_MEAN = 0.457;
    private static final double RAW_STD_DEV = 0.084;
    private static final double Z_90 = 1.281552;

    @Test
    @DisplayName("원점수가 평균이면 누적 50% 지점의 곡선 값으로 환산된다")
    void converts_mean_to_median_quantile() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when: 평균과 같은 원점수를 환산
        int score = converter.convert(RAW_MEAN);

        // then: 29%→50점, 51%→70점 사이 곡선 위의 69점이 된다
        assertThat(score).isEqualTo(69);
    }

    @Test
    @DisplayName("분위수 꺾은점에 해당하는 원점수는 설정한 점수 그대로 환산된다")
    void converts_quantile_knots_exactly() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when: 누적 90%와 10% 지점의 원점수를 환산
        int top = converter.convert(RAW_MEAN + Z_90 * RAW_STD_DEV);
        int bottom = converter.convert(RAW_MEAN - Z_90 * RAW_STD_DEV);

        // then: 각각 BEST_FRIEND 하한 85점, STRANGER 하한 30점이 된다
        assertThat(top).isEqualTo(85);
        assertThat(bottom).isEqualTo(30);
    }

    @Test
    @DisplayName("환산 결과가 범위를 벗어나면 상한과 하한으로 잘린다")
    void clamps_to_range() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when: 누적 확률이 사실상 0%와 100%인 원점수를 환산
        int max = converter.convert(1.0);
        int min = converter.convert(0.0);

        // then: 설정한 상한 99와 하한 10을 넘지 않는다
        assertThat(max).isEqualTo(99);
        assertThat(min).isEqualTo(10);
    }

    @Test
    @DisplayName("하한이 상한보다 크면 기동 시점에 실패한다")
    void invalid_range_fails_fast() {
        // given: 하한과 상한이 뒤집힌 설정
        ParallelSimilarityScoreConverter converter = new ParallelSimilarityScoreConverter(
                new ParallelRelationProperties(thresholds(),
                        new ParallelRelationProperties.SimilarityScore(RAW_MEAN, RAW_STD_DEV, 99, 10, quantiles())));

        // when & then: 첫 요청이 아니라 기동 때 터진다
        assertThatThrownBy(converter::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("min=99");
    }

    @Test
    @DisplayName("분위수가 오름차순이 아니면 기동 시점에 실패한다")
    void unordered_quantiles_fail_fast() {
        // given: 누적 비율이 거꾸로 적힌 설정
        List<ParallelRelationProperties.SimilarityScore.Quantile> unordered = List.of(
                new ParallelRelationProperties.SimilarityScore.Quantile(0.51, 70),
                new ParallelRelationProperties.SimilarityScore.Quantile(0.29, 50));
        ParallelSimilarityScoreConverter converter = new ParallelSimilarityScoreConverter(
                new ParallelRelationProperties(thresholds(),
                        new ParallelRelationProperties.SimilarityScore(RAW_MEAN, RAW_STD_DEV, 10, 99, unordered)));

        // when & then: 두 번째 꺾은점에서 터진다
        assertThatThrownBy(converter::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("percentile=0.29");
    }

    @Test
    @DisplayName("분위수 점수가 하한·상한 범위를 벗어나면 기동 시점에 실패한다")
    void out_of_range_quantile_score_fails_fast() {
        // given: 상한 99와 같은 점수를 꺾은점으로 적은 설정
        List<ParallelRelationProperties.SimilarityScore.Quantile> outOfRange = List.of(
                new ParallelRelationProperties.SimilarityScore.Quantile(0.90, 99));
        ParallelSimilarityScoreConverter converter = new ParallelSimilarityScoreConverter(
                new ParallelRelationProperties(thresholds(),
                        new ParallelRelationProperties.SimilarityScore(RAW_MEAN, RAW_STD_DEV, 10, 99, outOfRange)));

        // when & then: 기동 때 터진다
        assertThatThrownBy(converter::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score=99");
    }

    @Test
    @DisplayName("최저 점수의 상위 비율은 100%이고 최고 점수가 가장 희소하다")
    void top_percent_covers_both_ends() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when & then: 하한은 모두를 포함하고, 상한은 0.4%까지 좁혀진다
        assertThat(converter.topPercent(10)).isEqualTo(100.0);
        assertThat(converter.topPercent(99)).isEqualTo(0.4);
    }

    @Test
    @DisplayName("점수가 오르면 상위 비율은 반드시 내려간다")
    void top_percent_decreases_as_score_rises() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when & then: 표시 점수 전 구간에서 역전이 없다
        for (int score = 10; score < 99; score++) {
            assertThat(converter.topPercent(score)).isGreaterThan(converter.topPercent(score + 1));
        }
    }

    @Test
    @DisplayName("등급 경계 점수의 상위 비율이 설정한 분위수와 맞물린다")
    void top_percent_matches_configured_quantiles() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when & then: BEST_FRIEND 하한은 상위 10.8%, AWKWARD 하한은 상위 71.5%다
        assertThat(converter.topPercent(85)).isEqualTo(10.8);
        assertThat(converter.topPercent(50)).isEqualTo(71.5);
    }

    @Test
    @DisplayName("구간 분포는 하한부터 상한까지 5점 단위로 빈틈없이 이어진다")
    void distribution_covers_whole_range_in_five_point_bands() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when: 구간 분포를 가져온다
        List<ParallelScoreBand> bands = converter.distribution();

        // then: 10~14부터 95~99까지 18구간이 앞뒤로 맞물린다
        assertThat(bands).hasSize(18);
        assertThat(bands.getFirst().from()).isEqualTo(10);
        assertThat(bands.getLast().to()).isEqualTo(99);
        for (int i = 1; i < bands.size(); i++) {
            assertThat(bands.get(i).from()).isEqualTo(bands.get(i - 1).to() + 1);
        }
    }

    @Test
    @DisplayName("구간 확률을 모두 더하면 100%가 된다")
    void distribution_sums_to_one_hundred() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when: 모든 구간의 확률을 더한다
        double total = converter.distribution().stream().mapToDouble(ParallelScoreBand::percent).sum();

        // then: 반올림 오차를 감안해도 100%에 모인다
        assertThat(total).isCloseTo(100.0, within(0.5));
    }

    @Test
    @DisplayName("등급 경계가 모두 구간 시작점과 맞아떨어진다")
    void distribution_bands_align_with_relation_thresholds() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when: 구간 시작 점수만 모은다
        List<Integer> starts = converter.distribution().stream().map(ParallelScoreBand::from).toList();

        // then: 등급 경계 30, 50, 70, 85가 구간을 가르지 않는다
        assertThat(starts).contains(30, 50, 70, 85);
    }

    @Test
    @DisplayName("분포의 봉우리는 75~79 구간이다")
    void distribution_peaks_between_75_and_79() {
        // given: 운영 기본값과 같은 변환 설정
        ParallelSimilarityScoreConverter converter = converter(similarityScore());

        // when: 확률이 가장 높은 구간을 찾는다
        ParallelScoreBand peak = converter.distribution().stream()
                .max(Comparator.comparingDouble(ParallelScoreBand::percent))
                .orElseThrow();

        // then: 곡선의 정점이 들어 있는 구간이 17.3%로 가장 두껍다
        assertThat(peak.from()).isEqualTo(75);
        assertThat(peak.percent()).isEqualTo(17.3);
    }

    private ParallelSimilarityScoreConverter converter(ParallelRelationProperties.SimilarityScore similarityScore) {
        ParallelSimilarityScoreConverter converter = new ParallelSimilarityScoreConverter(
                new ParallelRelationProperties(thresholds(), similarityScore));
        converter.validate();

        return converter;
    }

    private ParallelRelationProperties.SimilarityScore similarityScore() {
        return new ParallelRelationProperties.SimilarityScore(RAW_MEAN, RAW_STD_DEV, 10, 99, quantiles());
    }

    private List<ParallelRelationProperties.SimilarityScore.Quantile> quantiles() {
        return List.of(
                new ParallelRelationProperties.SimilarityScore.Quantile(0.10, 30),
                new ParallelRelationProperties.SimilarityScore.Quantile(0.29, 50),
                new ParallelRelationProperties.SimilarityScore.Quantile(0.51, 70),
                new ParallelRelationProperties.SimilarityScore.Quantile(0.90, 85));
    }

    private Map<ParallelRelationType, Integer> thresholds() {
        Map<ParallelRelationType, Integer> thresholds = new EnumMap<>(ParallelRelationType.class);

        thresholds.put(ParallelRelationType.ENEMY, 0);

        return thresholds;
    }
}
