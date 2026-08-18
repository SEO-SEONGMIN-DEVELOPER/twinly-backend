package com.nidus.twinly.common.parallel;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ParallelRelationLoader {

    private static final String RESOURCE_PATH = "parallel/parallel_relations_v1.json";
    private static final String NAME_PLACEHOLDER = "{A";
    private static final String OTHER_NAME_PLACEHOLDER = "{B";

    private final ObjectMapper objectMapper;
    private final ParallelStoryRenderer parallelStoryRenderer;

    private Map<ParallelRelation, ParallelRelationContent> contentMap;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        JsonNode relations = objectMapper.readTree(resource.getInputStream());

        contentMap = new EnumMap<>(ParallelRelation.class);
        for (JsonNode relationNode : relations.get("relations")) {
            ParallelRelationContent content = objectMapper.treeToValue(relationNode, ParallelRelationContent.class);

            if (!content.story().contains(NAME_PLACEHOLDER) || !content.story().contains(OTHER_NAME_PLACEHOLDER)) {
                throw new IllegalStateException("이름 자리가 빠진 평행우주 이야기가 있습니다: " + content.relation());
            }

            List<String> unsupportedPlaceholders = parallelStoryRenderer.unsupportedPlaceholders(content.story());
            if (!unsupportedPlaceholders.isEmpty()) {
                throw new IllegalStateException(
                        "채울 수 없는 이름 자리가 있는 평행우주 이야기가 있습니다: " + content.relation() + " " + unsupportedPlaceholders);
            }

            if (contentMap.put(content.relation(), content) != null) {
                throw new IllegalStateException("중복된 평행우주 관계가 있습니다: " + content.relation());
            }
        }

        for (ParallelRelation relation : ParallelRelation.values()) {
            if (!contentMap.containsKey(relation)) {
                throw new IllegalStateException("이야기가 없는 평행우주 관계가 있습니다: " + relation);
            }
        }
    }

    public ParallelRelationContent getContent(ParallelRelation relation) {
        return contentMap.get(relation);
    }
}
