package com.nidus.twinly.common.balancegame;

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
public class BalanceGameQuestionLoader {

    private static final String RESOURCE_PATH = "balancegame/balance_game_v1.json";
    private static final int OPTION_COUNT = 2;

    private final ObjectMapper objectMapper;

    private Map<Long, BalanceGameQuestion> questionMap;
    private List<BalanceGameQuestion> questions;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        JsonNode root = objectMapper.readTree(resource.getInputStream());

        Map<Long, BalanceGameQuestion> loaded = new LinkedHashMap<>();
        for (JsonNode questionNode : root.get("questions")) {
            BalanceGameQuestion question = objectMapper.treeToValue(questionNode, BalanceGameQuestion.class);

            validateOptions(question);

            if (loaded.put(question.id(), question) != null) {
                throw new IllegalStateException("중복된 밸런스 게임 질문 id가 있습니다: " + question.id());
            }
        }

        if (loaded.isEmpty()) {
            throw new IllegalStateException("밸런스 게임 질문이 비어 있습니다: " + RESOURCE_PATH);
        }

        questionMap = loaded;
        questions = List.copyOf(loaded.values());
    }

    private void validateOptions(BalanceGameQuestion question) {
        List<BalanceGameOption> options = question.options();

        if (options.size() != OPTION_COUNT) {
            throw new IllegalStateException("선택지가 " + OPTION_COUNT + "개가 아닌 밸런스 게임 질문이 있습니다: " + question.id());
        }

        Set<Long> optionIds = options.stream()
                .map(BalanceGameOption::id)
                .collect(Collectors.toSet());

        if (optionIds.size() != options.size()) {
            throw new IllegalStateException("선택지 id가 중복된 밸런스 게임 질문이 있습니다: " + question.id());
        }
    }

    public Optional<BalanceGameQuestion> findQuestion(Long id) {
        return Optional.ofNullable(questionMap.get(id));
    }

    public BalanceGameQuestion questionFor(long sequence) {
        return questions.get((int) Math.floorMod(sequence, (long) questions.size()));
    }
}
