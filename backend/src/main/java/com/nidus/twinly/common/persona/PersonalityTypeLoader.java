package com.nidus.twinly.common.persona;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class PersonalityTypeLoader {

    private static final String RESOURCE_PATH = "personality/personality_types_v1.json";
    private static final Set<String> ADJECTIVE_CODES = Set.of("00", "01", "10", "11");
    private static final Set<String> NOUN_CODES = Set.of("000", "001", "010", "011", "100", "101", "110", "111");
    private static final int ADJECTIVE_CODE_LENGTH = 2;
    private static final String NOUN_CODE_PLACEHOLDER = "{nounCode}";
    private static final char HIGH = '1';

    private final ObjectMapper objectMapper;

    private String imageKeyPattern;
    private List<PersonalityTypeAxis> axes;
    private Map<String, PersonalityTypePart> adjectives;
    private Map<String, PersonalityTypePart> nouns;

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        JsonNode root = objectMapper.readTree(resource.getInputStream());

        imageKeyPattern = loadImageKeyPattern(root.get("imageKeyPattern"));
        axes = loadAxes(root.get("axes"));
        adjectives = loadParts(root.get("adjectives"), ADJECTIVE_CODES, "형용사");
        nouns = loadParts(root.get("nouns"), NOUN_CODES, "명사");
    }

    private String loadImageKeyPattern(JsonNode node) {
        String pattern = node != null && node.isString() ? node.asString() : null;

        if (pattern == null || !pattern.contains(NOUN_CODE_PLACEHOLDER)) {
            throw new IllegalStateException("imageKeyPattern 에 " + NOUN_CODE_PLACEHOLDER + " 가 있어야 합니다: " + pattern);
        }

        return pattern;
    }

    private List<PersonalityTypeAxis> loadAxes(JsonNode nodes) {
        List<PersonalityTypeAxis> loaded = new ArrayList<>();

        for (JsonNode node : nodes) {
            loaded.add(objectMapper.treeToValue(node, PersonalityTypeAxis.class));
        }

        List<PersonaDimension> order = loaded.stream()
                .map(PersonalityTypeAxis::axis)
                .toList();

        if (!order.equals(PersonalityTypeCalculator.AXES)) {
            throw new IllegalStateException("axes 순서가 유형 코드 순서 " + PersonalityTypeCalculator.AXES + " 와 다릅니다: " + order);
        }

        return List.copyOf(loaded);
    }

    private Map<String, PersonalityTypePart> loadParts(JsonNode nodes, Set<String> expectedCodes, String kind) {
        Map<String, PersonalityTypePart> parts = new HashMap<>();

        for (JsonNode node : nodes) {
            PersonalityTypePart part = objectMapper.treeToValue(node, PersonalityTypePart.class);

            if (parts.put(part.code(), part) != null) {
                throw new IllegalStateException("중복된 " + kind + " 코드가 있습니다: " + part.code());
            }
        }

        if (!parts.keySet().equals(expectedCodes)) {
            throw new IllegalStateException(kind + " 코드가 " + expectedCodes + " 와 다릅니다: " + parts.keySet());
        }

        return Map.copyOf(parts);
    }

    public PersonalityType get(String code) {
        PersonalityTypePart adjective = adjectives.get(code.substring(0, ADJECTIVE_CODE_LENGTH));
        PersonalityTypePart noun = nouns.get(code.substring(ADJECTIVE_CODE_LENGTH));

        return new PersonalityType(
                code,
                adjective.name() + " " + noun.name(),
                keywords(code),
                adjective,
                noun,
                imageKeyPattern.replace(NOUN_CODE_PLACEHOLDER, noun.code())
        );
    }

    private List<String> keywords(String code) {
        return IntStream.range(0, axes.size())
                .mapToObj(i -> code.charAt(i) == HIGH ? axes.get(i).high() : axes.get(i).low())
                .toList();
    }
}
