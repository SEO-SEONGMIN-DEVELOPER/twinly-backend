package com.nidus.twinly.season.domain;

import java.util.Arrays;
import java.util.Collection;

public final class SeasonRecapRanking {

    private final int[] ascendingValues;

    private SeasonRecapRanking(int[] ascendingValues) {
        this.ascendingValues = ascendingValues;
    }

    public static SeasonRecapRanking of(Collection<Integer> values) {
        int[] ascendingValues = values.stream().mapToInt(Integer::intValue).sorted().toArray();

        return new SeasonRecapRanking(ascendingValues);
    }

    public Integer topPercentOf(int value) {
        if (value <= 0 || ascendingValues.length == 0) {
            return null;
        }

        int population = ascendingValues.length;
        int greaterCount = population - upperBound(value);

        return Math.min(100, ((greaterCount + 1) * 100 + population - 1) / population);
    }

    private int upperBound(int value) {
        int index = Arrays.binarySearch(ascendingValues, value);
        if (index < 0) {
            return -index - 1;
        }

        while (index < ascendingValues.length && ascendingValues[index] == value) {
            index++;
        }
        return index;
    }
}
