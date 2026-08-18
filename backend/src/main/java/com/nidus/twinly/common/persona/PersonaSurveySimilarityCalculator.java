package com.nidus.twinly.common.persona;

import com.nidus.twinly.common.survey.SurveyTraitRef;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class PersonaSurveySimilarityCalculator {

    public Map<PersonaDimension, Double> dimensionScores(Map<Integer, SurveyTraitRef> answers, Map<Integer, SurveyTraitRef> otherAnswers) {
        Map<PersonaDimension, Integer> comparedCounts = new EnumMap<>(PersonaDimension.class);
        Map<PersonaDimension, Integer> matchedCounts = new EnumMap<>(PersonaDimension.class);

        for (Map.Entry<Integer, SurveyTraitRef> entry : answers.entrySet()) {
            SurveyTraitRef ref = entry.getValue();
            SurveyTraitRef otherRef = otherAnswers.get(entry.getKey());

            if (otherRef == null) {
                continue;
            }

            comparedCounts.merge(ref.dimension(), 1, Integer::sum);

            if (ref.optionName() == otherRef.optionName()) {
                matchedCounts.merge(ref.dimension(), 1, Integer::sum);
            }
        }

        Map<PersonaDimension, Double> scores = new EnumMap<>(PersonaDimension.class);
        comparedCounts.forEach((dimension, compared) ->
                scores.put(dimension, (double) matchedCounts.getOrDefault(dimension, 0) / compared));

        return scores;
    }
}
