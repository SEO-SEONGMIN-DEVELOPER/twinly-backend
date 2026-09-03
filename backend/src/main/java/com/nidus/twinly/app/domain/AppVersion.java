package com.nidus.twinly.app.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AppVersion(
        int major,
        int minor,
        int patch
) implements Comparable<AppVersion> {

    private static final Pattern FORMAT = Pattern.compile("^(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})$");

    private static final Comparator<AppVersion> ORDER = Comparator
            .comparingInt(AppVersion::major)
            .thenComparingInt(AppVersion::minor)
            .thenComparingInt(AppVersion::patch);

    public static Optional<AppVersion> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }

        Matcher matcher = FORMAT.matcher(value.trim());

        if (!matcher.matches()) {
            return Optional.empty();
        }

        return Optional.of(new AppVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))));
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AppVersion from(String value) {
        return parse(value)
                .orElseThrow(() -> new IllegalArgumentException("버전 형식이 올바르지 않습니다: " + value));
    }

    public boolean isLowerThan(AppVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(AppVersion other) {
        return ORDER.compare(this, other);
    }

    @JsonValue
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
