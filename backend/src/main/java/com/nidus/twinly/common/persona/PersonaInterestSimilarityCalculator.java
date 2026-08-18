package com.nidus.twinly.common.persona;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PersonaInterestSimilarityCalculator {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public OptionalDouble similarity(List<String> interests, List<String> otherInterests) {
        Set<String> normalized = normalize(interests);
        Set<String> otherNormalized = normalize(otherInterests);

        if (normalized.isEmpty() || otherNormalized.isEmpty()) {
            return OptionalDouble.empty();
        }

        long shared = normalized.stream()
                .filter(otherNormalized::contains)
                .count();

        return OptionalDouble.of(shared / Math.sqrt((double) normalized.size() * otherNormalized.size()));
    }

    private Set<String> normalize(List<String> interests) {
        return interests.stream()
                .map(interest -> WHITESPACE.matcher(Normalizer.normalize(interest, Normalizer.Form.NFKC)).replaceAll(""))
                .map(interest -> interest.toLowerCase(Locale.ROOT))
                .filter(interest -> !interest.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
