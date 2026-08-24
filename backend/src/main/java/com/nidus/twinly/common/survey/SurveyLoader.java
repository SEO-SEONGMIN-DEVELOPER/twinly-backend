package com.nidus.twinly.common.survey;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SurveyLoader {
    @Autowired
    ObjectMapper objectMapper;

    private Map<Integer, SurveyQuestion> questionMap;
    private Map<String, SurveyTraitRef> traitIndex;
    private Integer lastQuestionId;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("survey/survey_v2_mixed.json");
        JsonNode survey = objectMapper.readTree(resource.getInputStream());

        questionMap = new LinkedHashMap<>();
        for (JsonNode questionNode : survey.get("questions")) {
            SurveyQuestion question = objectMapper.treeToValue(questionNode, SurveyQuestion.class);
            questionMap.put(question.id(), question);
        }

        traitIndex = new HashMap<>();
        for (SurveyQuestion question : questionMap.values()) {
            for (Map.Entry<SurveyOptionName, SurveyOption> option : question.options().entrySet()) {
                SurveyTraitRef ref = new SurveyTraitRef(question.id(), question.dimension(), option.getKey());
                SurveyTraitRef duplicated = traitIndex.put(option.getValue().trait(), ref);

                if (duplicated != null) {
                    throw new IllegalStateException("중복된 trait 문자열이 있습니다: " + option.getValue().trait());
                }
            }
        }

        lastQuestionId = questionMap.keySet().stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("설문 문항이 비어 있습니다: survey/survey_v2_mixed.json"));
    }

    public SurveyQuestion getQuestion(Integer id) {
        return questionMap.get(id);
    }

    public Optional<SurveyTraitRef> findTraitRef(String trait) {
        return Optional.ofNullable(traitIndex.get(trait));
    }

    public List<SurveyQuestion> getAllQuestions() {
        return List.copyOf(questionMap.values());
    }

    public boolean isLastQuestion(Integer id) {
        return lastQuestionId.equals(id);
    }
}
