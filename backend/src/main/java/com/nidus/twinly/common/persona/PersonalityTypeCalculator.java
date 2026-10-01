package com.nidus.twinly.common.persona;

import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.survey.SurveyTraitRef;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PersonalityTypeCalculator {

    static final List<PersonaDimension> AXES = List.of(
            PersonaDimension.EXTRAVERSION,
            PersonaDimension.OPENNESS,
            PersonaDimension.CONSCIENTIOUSNESS,
            PersonaDimension.AGREEABLENESS,
            PersonaDimension.NEUROTICISM
    );

    private final SurveyLoader surveyLoader;
    private final PersonaSurveyAnswerResolver personaSurveyAnswerResolver;

    private Map<PersonaDimension, Integer> questionCounts;

    @PostConstruct
    public void init() {
        questionCounts = surveyLoader.getAllQuestions().stream()
                .filter(question -> question.dimension().isBigFive())
                .collect(Collectors.groupingBy(
                        SurveyQuestion::dimension,
                        () -> new EnumMap<>(PersonaDimension.class),
                        Collectors.summingInt(question -> 1)));

        for (PersonaDimension axis : AXES) {
            int count = questionCounts.getOrDefault(axis, 0);

            if (count % 2 == 0) {
                throw new IllegalStateException("Big5 축의 문항 수는 홀수여야 합니다: " + axis + "=" + count);
            }
        }
    }

    public Optional<String> calculate(List<String> explanations) {
        Map<PersonaDimension, Integer> answered = new EnumMap<>(PersonaDimension.class);
        Map<PersonaDimension, Integer> high = new EnumMap<>(PersonaDimension.class);

        for (SurveyTraitRef ref : personaSurveyAnswerResolver.resolve(explanations).values()) {
            SurveyQuestion question = surveyLoader.getQuestion(ref.questionId());

            if (!question.dimension().isBigFive()) {
                continue;
            }

            answered.merge(question.dimension(), 1, Integer::sum);

            if (ref.optionName() == question.highOption()) {
                high.merge(question.dimension(), 1, Integer::sum);
            }
        }

        StringBuilder code = new StringBuilder();

        for (PersonaDimension axis : AXES) {
            int answeredCount = answered.getOrDefault(axis, 0);

            if (answeredCount != questionCounts.get(axis)) {
                return Optional.empty();
            }

            code.append(high.getOrDefault(axis, 0) * 2 > answeredCount ? '1' : '0');
        }

        return Optional.of(code.toString());
    }
}
