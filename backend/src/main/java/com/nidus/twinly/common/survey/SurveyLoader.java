package com.nidus.twinly.common.survey;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class SurveyLoader {
    @Autowired
    ObjectMapper objectMapper;

    private Map<Integer, SurveyQuestion> questionMap;
    private Integer lastKey;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("survey/survey_v1.json");
        JsonNode survey = objectMapper.readTree(resource.getInputStream());

        questionMap = new HashMap<>();
        for (JsonNode questionNode : survey.get("questions")) {
            SurveyQuestion question = objectMapper.treeToValue(questionNode, SurveyQuestion.class);
            questionMap.put(question.id(), question);
            lastKey = question.id();
        }
    }

    public SurveyQuestion getQuestion(Integer id) {
        return questionMap.get(id);
    }

    public Integer lastKey() {
        return lastKey;
    }
}
