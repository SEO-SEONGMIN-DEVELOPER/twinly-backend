package com.nidus.twinly.common.feedback;

import com.nidus.twinly.common.jackson.EnumJsonNames;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FeedbackOptionLoader {

    private static final String RESOURCE_PATH = "feedback/feedback_options_v1.json";

    private final ObjectMapper objectMapper;

    private Map<FeedbackType, List<FeedbackOption>> optionsByType;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        JsonNode root = objectMapper.readTree(resource.getInputStream());

        Map<FeedbackType, List<FeedbackOption>> loaded = new EnumMap<>(FeedbackType.class);
        Set<Long> optionIds = new HashSet<>();
        for (FeedbackType type : FeedbackType.values()) {
            JsonNode optionsNode = root.get(EnumJsonNames.of(type));
            if (optionsNode == null || !optionsNode.isArray()) {
                throw new IllegalStateException("피드백 선택지 목록이 없습니다: " + EnumJsonNames.of(type));
            }

            List<FeedbackOption> options = new ArrayList<>();
            for (JsonNode optionNode : optionsNode) {
                FeedbackOption option = objectMapper.treeToValue(optionNode, FeedbackOption.class);

                validateOption(option);

                if (!optionIds.add(option.id())) {
                    throw new IllegalStateException("중복된 피드백 선택지 id가 있습니다: " + option.id());
                }
                options.add(option);
            }
            loaded.put(type, List.copyOf(options));
        }

        optionsByType = loaded;
    }

    private void validateOption(FeedbackOption option) {
        if (option.id() == null || option.label() == null || option.label().isBlank()) {
            throw new IllegalStateException("id나 문구가 비어 있는 피드백 선택지가 있습니다: " + option);
        }
    }

    public List<FeedbackOption> getOptions(FeedbackType type) {
        return optionsByType.get(type);
    }
}
