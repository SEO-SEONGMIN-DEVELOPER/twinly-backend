package com.nidus.twinly.common.survey;

import com.nidus.twinly.common.persona.PersonaDimension;

import java.util.Map;

public record SurveyQuestion(
        Integer id,
        PersonaDimension dimension,
        SurveyOptionName highOption,
        String scenario,
        Map<SurveyOptionName, SurveyOption> options
) {

    public String traitFor(SurveyOptionName answer) {
        return options.get(answer).trait();
    }
}