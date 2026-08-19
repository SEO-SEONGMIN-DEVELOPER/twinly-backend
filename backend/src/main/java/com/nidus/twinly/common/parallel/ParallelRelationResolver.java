package com.nidus.twinly.common.parallel;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class ParallelRelationResolver {

    private final ParallelRelationProperties parallelRelationProperties;
    private final ParallelRelationLoader parallelRelationLoader;
    private final ParallelStoryRenderer parallelStoryRenderer;

    @PostConstruct
    public void validateThresholds() {
        for (ParallelRelationType relation : ParallelRelationType.values()) {
            if (!parallelRelationProperties.relationThresholds().containsKey(relation)) {
                throw new IllegalStateException("경계값이 설정되지 않은 평행우주 관계가 있습니다: " + relation);
            }
        }
    }

    public ParallelRelationType relationOf(double score) {
        return parallelRelationProperties.relationThresholds().entrySet().stream()
                .filter(entry -> score >= entry.getValue())
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("점수에 해당하는 평행우주 관계가 없습니다: " + score));
    }

    public int pickStoryIndex(ParallelRelationType relation) {
        return ThreadLocalRandom.current().nextInt(parallelRelationLoader.getContents(relation).size());
    }

    public String title(ParallelRelationType relation, int storyIndex) {
        return content(relation, storyIndex).title();
    }

    public ParallelRelationResult render(ParallelRelationType relation, int storyIndex, String name, String otherName) {
        ParallelRelationContent content = content(relation, storyIndex);

        return new ParallelRelationResult(
                relation,
                content.title(),
                parallelStoryRenderer.render(content.story(), name, otherName)
        );
    }

    private ParallelRelationContent content(ParallelRelationType relation, int storyIndex) {
        List<ParallelRelationContent> contents = parallelRelationLoader.getContents(relation);

        return contents.get(Math.floorMod(storyIndex, contents.size()));
    }
}
