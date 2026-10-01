package com.nidus.twinly.common.tendency;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TendencyLoader {

    private static final String RESOURCE_PATH = "tendency/tendency_v1.json";
    private static final int MIN_OPTION_COUNT = 2;

    private final ObjectMapper objectMapper;

    private Map<Long, TendencyQuestion> questionMap;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        JsonNode root = objectMapper.readTree(resource.getInputStream());

        Map<Long, TendencyQuestion> loaded = new LinkedHashMap<>();
        for (JsonNode questionNode : root.get("questions")) {
            TendencyQuestion question = objectMapper.treeToValue(questionNode, TendencyQuestion.class);

            validateOptions(question);

            if (loaded.put(question.id(), question) != null) {
                throw new IllegalStateException("중복된 성향 문항 id가 있습니다: " + question.id());
            }
        }

        if (loaded.isEmpty()) {
            throw new IllegalStateException("성향 문항이 비어 있습니다: " + RESOURCE_PATH);
        }

        questionMap = loaded;
    }

    private void validateOptions(TendencyQuestion question) {
        List<TendencyOption> options = question.options();

        if (options.size() < MIN_OPTION_COUNT) {
            throw new IllegalStateException("선택지가 " + MIN_OPTION_COUNT + "개 미만인 성향 문항이 있습니다: " + question.id());
        }

        Set<Long> optionIds = options.stream()
                .map(TendencyOption::id)
                .collect(Collectors.toSet());

        if (optionIds.size() != options.size()) {
            throw new IllegalStateException("선택지 id가 중복된 성향 문항이 있습니다: " + question.id());
        }
    }

    public Optional<TendencyQuestion> findQuestion(Long id) {
        return Optional.ofNullable(questionMap.get(id));
    }

    public List<TendencyQuestion> getAllQuestions() {
        return List.copyOf(questionMap.values());
    }
}
