package com.nidus.twinly.common.parallel;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ParallelSimilarityScoreConverter {

    private static final double SQRT_2 = Math.sqrt(2);
    private static final int BAND_WIDTH = 5;

    private final ParallelRelationProperties parallelRelationProperties;
    private MonotoneCubicCurve curve;
    private List<ParallelScoreBand> distribution;

    @PostConstruct
    public void validate() {
        ParallelRelationProperties.SimilarityScore score = parallelRelationProperties.similarityScore();

        if (score.min() >= score.max()) {
            throw new IllegalStateException(
                    "표시 점수 하한이 상한보다 작아야 합니다: min=%d, max=%d".formatted(score.min(), score.max()));
        }

        List<double[]> knots = new ArrayList<>();
        knots.add(new double[]{0.0, score.min()});
        for (ParallelRelationProperties.SimilarityScore.Quantile quantile : score.quantiles()) {
            double[] previous = knots.getLast();
            if (quantile.percentile() <= previous[0] || quantile.percentile() >= 1.0) {
                throw new IllegalStateException(
                        "분위수는 0과 1 사이에서 오름차순이어야 합니다: percentile=%s".formatted(quantile.percentile()));
            }
            if (quantile.score() <= previous[1] || quantile.score() >= score.max()) {
                throw new IllegalStateException(
                        "분위수 점수는 하한과 상한 사이에서 오름차순이어야 합니다: score=%d".formatted(quantile.score()));
            }
            knots.add(new double[]{quantile.percentile(), quantile.score()});
        }
        knots.add(new double[]{1.0, score.max()});

        curve = MonotoneCubicCurve.through(knots);

        List<ParallelScoreBand> bands = new ArrayList<>();
        for (int from = score.min(); from <= score.max(); from += BAND_WIDTH) {
            int to = Math.min(from + BAND_WIDTH - 1, score.max());
            bands.add(new ParallelScoreBand(from, to,
                    toPercent(curve.inverseAt(to + 0.5) - curve.inverseAt(from - 0.5))));
        }
        distribution = List.copyOf(bands);
    }

    public int convert(double rawScore) {
        ParallelRelationProperties.SimilarityScore score = parallelRelationProperties.similarityScore();
        double percentile = standardNormalCdf((rawScore - score.rawMean()) / score.rawStdDev());

        return (int) Math.clamp(Math.round(curve.valueAt(percentile)), score.min(), score.max());
    }

    public double topPercent(int displayScore) {
        return toPercent(1 - curve.inverseAt(displayScore - 0.5));
    }

    public List<ParallelScoreBand> distribution() {
        return distribution;
    }

    private static double toPercent(double ratio) {
        return Math.round(ratio * 1000) / 10.0;
    }

    private static double standardNormalCdf(double z) {
        return 0.5 * (1 + erf(z / SQRT_2));
    }

    private static double erf(double x) {
        double t = 1 / (1 + 0.3275911 * Math.abs(x));
        double polynomial = ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t;
        double y = 1 - polynomial * Math.exp(-x * x);

        return Math.copySign(y, x);
    }
}
