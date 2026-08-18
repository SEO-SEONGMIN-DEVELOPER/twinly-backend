package com.nidus.twinly.common.persona;

import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyTraitRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PersonaSurveyAnswerResolver {

    private final SurveyLoader surveyLoader;

    public Map<Integer, SurveyTraitRef> resolve(List<String> explanations) {
        Map<Integer, SurveyTraitRef> answers = new LinkedHashMap<>();

        for (String explanation : explanations) {
            surveyLoader.findTraitRef(explanation)
                    .ifPresent(ref -> answers.put(ref.questionId(), ref));
        }

        return answers;
    }
}
