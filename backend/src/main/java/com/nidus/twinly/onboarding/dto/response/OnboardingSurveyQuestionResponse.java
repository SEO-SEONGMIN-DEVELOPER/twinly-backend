package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;

import java.util.EnumMap;
import java.util.Map;

public record OnboardingSurveyQuestionResponse(
        Integer id,
        PersonaDimension dimension,
        String scenario,
        Map<SurveyOptionName, String> options
) {

    public static OnboardingSurveyQuestionResponse from(SurveyQuestion question) {
        Map<SurveyOptionName, String> options = new EnumMap<>(SurveyOptionName.class);
        question.options().forEach((name, option) -> options.put(name, option.label()));

        return new OnboardingSurveyQuestionResponse(
                question.id(),
                question.dimension(),
                question.scenario(),
                options
        );
    }
}
