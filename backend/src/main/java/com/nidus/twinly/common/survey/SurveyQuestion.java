package com.nidus.twinly.common.survey;

import java.util.Map;

public record SurveyQuestion(Integer id, PersonaDimension dimension, String scenario, Map<SurveyOptionName, SurveyOption> options) {

    public String traitFor(SurveyOptionName answer) {
        return options.get(answer).trait();
    }
}