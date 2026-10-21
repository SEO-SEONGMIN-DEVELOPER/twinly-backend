package com.nidus.twinly.admin.dto.result;

import java.util.List;

public record AdminSurveyResult(
        List<Question> questions
) {

    public record Question(
            Long id,
            String text,
            AdminGenderCountResult respondents,
            List<Option> options
    ) {
    }

    public record Option(
            Long id,
            String label,
            AdminGenderCountResult respondents
    ) {
    }
}
