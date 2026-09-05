package com.nidus.twinly.common.parallel;

import java.util.List;

public final class MonotoneCubicCurve {

    private static final int BISECTION_STEPS = 60;

    private final double[] xs;
    private final double[] ys;
    private final double[] tangents;

    private MonotoneCubicCurve(double[] xs, double[] ys, double[] tangents) {
        this.xs = xs;
        this.ys = ys;
        this.tangents = tangents;
    }

    public static MonotoneCubicCurve through(List<double[]> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("보간 곡선에는 점이 최소 2개 필요합니다: " + points.size());
        }

        int n = points.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i)[0];
            ys[i] = points.get(i)[1];
            if (i > 0 && (xs[i] <= xs[i - 1] || ys[i] <= ys[i - 1])) {
                throw new IllegalArgumentException(
                        "보간 곡선의 점은 x, y 모두 오름차순이어야 합니다: (%s, %s)".formatted(xs[i], ys[i]));
            }
        }

        double[] widths = new double[n - 1];
        double[] slopes = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            widths[i] = xs[i + 1] - xs[i];
            slopes[i] = (ys[i + 1] - ys[i]) / widths[i];
        }

        double[] tangents = new double[n];
        tangents[0] = slopes[0];
        tangents[n - 1] = slopes[n - 2];
        for (int i = 1; i < n - 1; i++) {
            double weightLeft = 2 * widths[i] + widths[i - 1];
            double weightRight = widths[i] + 2 * widths[i - 1];
            tangents[i] = 3 * (widths[i - 1] + widths[i]) / (weightLeft / slopes[i - 1] + weightRight / slopes[i]);
        }

        return new MonotoneCubicCurve(xs, ys, tangents);
    }

    public double valueAt(double x) {
        if (x <= xs[0]) {
            return ys[0];
        }
        if (x >= xs[xs.length - 1]) {
            return ys[ys.length - 1];
        }

        int i = 0;
        while (x > xs[i + 1]) {
            i++;
        }

        double width = xs[i + 1] - xs[i];
        double t = (x - xs[i]) / width;
        double t2 = t * t;
        double t3 = t2 * t;

        return (2 * t3 - 3 * t2 + 1) * ys[i]
                + (t3 - 2 * t2 + t) * width * tangents[i]
                + (-2 * t3 + 3 * t2) * ys[i + 1]
                + (t3 - t2) * width * tangents[i + 1];
    }

    public double inverseAt(double y) {
        if (y <= ys[0]) {
            return xs[0];
        }
        if (y >= ys[ys.length - 1]) {
            return xs[xs.length - 1];
        }

        double low = xs[0];
        double high = xs[xs.length - 1];
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = (low + high) / 2;
            if (valueAt(mid) < y) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return high;
    }
}
