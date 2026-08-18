package com.nidus.twinly.common.survey;

import com.nidus.twinly.common.persona.PersonaDimension;

public record SurveyTraitRef(
        Integer questionId,
        PersonaDimension dimension,
        SurveyOptionName optionName
) {
}
