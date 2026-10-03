package com.nidus.twinly.relationship.domain;

public record Intimacy(int value, int game) {

    public static final Intimacy ZERO = new Intimacy(0, 0);

    private static final int MAX = 100;

    public static Intimacy of(int simulated, long bonusAfterAsOf, long bonusTotal) {
        int value = (int) Math.min(MAX, simulated + bonusAfterAsOf);

        return new Intimacy(value, (int) Math.min(bonusTotal, value));
    }
}
