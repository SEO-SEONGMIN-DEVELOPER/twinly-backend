package com.nidus.twinly.common.survey;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SurveyLoader {
    @Autowired
    ObjectMapper objectMapper;

    private Map<Integer, SurveyQuestion> questionMap;
    private Integer lastKey;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("survey/survey_v1_mixed.json");
        JsonNode survey = objectMapper.readTree(resource.getInputStream());

        questionMap = new LinkedHashMap<>();
        for (JsonNode questionNode : survey.get("questions")) {
            SurveyQuestion question = objectMapper.treeToValue(questionNode, SurveyQuestion.class);
            questionMap.put(question.id(), question);
        }

        lastKey = questionMap.size();
    }

    public SurveyQuestion getQuestion(Integer id) {
        return questionMap.get(id);
    }

    public List<SurveyQuestion> getAllQuestions() {
        return List.copyOf(questionMap.values());
    }

    public Integer lastKey() {
        return lastKey;
    }
}
