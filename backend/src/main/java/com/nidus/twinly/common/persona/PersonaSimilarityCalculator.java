package com.nidus.twinly.common.persona;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PersonaSimilarityCalculator {

    private final PersonaSurveyAnswerResolver personaSurveyAnswerResolver;
    private final PersonaSurveySimilarityCalculator personaSurveySimilarityCalculator;
    private final PersonaInterestSimilarityCalculator personaInterestSimilarityCalculator;

    public PersonaSimilarity similarity(Map<PersonaDimension, List<String>> elements, Map<PersonaDimension, List<String>> otherElements) {
        Map<PersonaDimension, Double> scores = new EnumMap<>(PersonaDimension.class);

        scores.putAll(personaSurveySimilarityCalculator.dimensionScores(
                personaSurveyAnswerResolver.resolve(explanations(elements)),
                personaSurveyAnswerResolver.resolve(explanations(otherElements))
        ));

        personaInterestSimilarityCalculator.similarity(
                interests(elements),
                interests(otherElements)
        ).ifPresent(interestScore -> scores.put(PersonaDimension.INTEREST, interestScore));

        double score = scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return new PersonaSimilarity(score, Collections.unmodifiableMap(scores));
    }

    private List<String> explanations(Map<PersonaDimension, List<String>> elements) {
        return elements.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    private List<String> interests(Map<PersonaDimension, List<String>> elements) {
        return elements.getOrDefault(PersonaDimension.INTEREST, List.of());
    }
}
