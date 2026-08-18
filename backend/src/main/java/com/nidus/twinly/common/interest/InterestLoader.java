package com.nidus.twinly.common.interest;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class InterestLoader {

    private static final String RESOURCE_PATH = "interest/interests_v1.json";

    private final ObjectMapper objectMapper;

    private List<String> interests;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        JsonNode root = objectMapper.readTree(resource.getInputStream());

        List<String> loaded = new ArrayList<>();
        for (JsonNode categoryNode : root.get("categories")) {
            for (JsonNode interestNode : categoryNode.get("interests")) {
                loaded.add(interestNode.asString());
            }
        }

        Set<String> distinct = new LinkedHashSet<>(loaded);
        if (distinct.size() != loaded.size()) {
            throw new IllegalStateException("중복된 관심사가 있습니다: " + RESOURCE_PATH);
        }

        if (loaded.isEmpty()) {
            throw new IllegalStateException("관심사 목록이 비어 있습니다: " + RESOURCE_PATH);
        }

        interests = List.copyOf(loaded);
    }

    public List<String> getAllInterests() {
        return interests;
    }
}
