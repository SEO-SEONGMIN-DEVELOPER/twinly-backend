package com.nidus.twinly.common.parallel;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ParallelRelationLoader {

    private static final String RESOURCE_PATH = "parallel/parallel_relations_v1.json";
    private static final String NAME_PLACEHOLDER = "{A";
    private static final String OTHER_NAME_PLACEHOLDER = "{B";

    private final ObjectMapper objectMapper;
    private final ParallelStoryRenderer parallelStoryRenderer;

    private Map<ParallelRelationType, List<ParallelRelationContent>> contentMap;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        JsonNode relations = objectMapper.readTree(resource.getInputStream());

        contentMap = new EnumMap<>(ParallelRelationType.class);
        for (JsonNode relationNode : relations.get("relations")) {
            ParallelRelationStories relationStories =
                    objectMapper.treeToValue(relationNode, ParallelRelationStories.class);

            validate(relationStories);

            if (contentMap.put(relationStories.relation(), List.copyOf(relationStories.stories())) != null) {
                throw new IllegalStateException("중복된 평행우주 관계가 있습니다: " + relationStories.relation());
            }
        }

        for (ParallelRelationType relation : ParallelRelationType.values()) {
            if (!contentMap.containsKey(relation)) {
                throw new IllegalStateException("이야기가 없는 평행우주 관계가 있습니다: " + relation);
            }
        }
    }

    public List<ParallelRelationContent> getContents(ParallelRelationType relation) {
        return contentMap.get(relation);
    }

    private void validate(ParallelRelationStories relationStories) {
        ParallelRelationType relation = relationStories.relation();

        if (relationStories.stories() == null || relationStories.stories().isEmpty()) {
            throw new IllegalStateException("이야기가 없는 평행우주 관계가 있습니다: " + relation);
        }

        Set<String> titles = new HashSet<>();
        for (ParallelRelationContent content : relationStories.stories()) {
            if (!content.story().contains(NAME_PLACEHOLDER) || !content.story().contains(OTHER_NAME_PLACEHOLDER)) {
                throw new IllegalStateException(
                        "이름 자리가 빠진 평행우주 이야기가 있습니다: " + relation + " " + content.title());
            }

            List<String> unsupportedPlaceholders = parallelStoryRenderer.unsupportedPlaceholders(content.story());
            if (!unsupportedPlaceholders.isEmpty()) {
                throw new IllegalStateException("채울 수 없는 이름 자리가 있는 평행우주 이야기가 있습니다: "
                        + relation + " " + content.title() + " " + unsupportedPlaceholders);
            }

            if (!titles.add(content.title())) {
                throw new IllegalStateException(
                        "제목이 중복된 평행우주 이야기가 있습니다: " + relation + " " + content.title());
            }
        }
    }
}
