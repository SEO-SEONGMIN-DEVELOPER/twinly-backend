package com.nidus.twinly.common.parallel;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ParallelRelationResolver {

    private final ParallelRelationProperties parallelRelationProperties;
    private final ParallelRelationLoader parallelRelationLoader;
    private final ParallelStoryRenderer parallelStoryRenderer;

    @PostConstruct
    public void validateThresholds() {
        for (ParallelRelation relation : ParallelRelation.values()) {
            if (!parallelRelationProperties.relationThresholds().containsKey(relation)) {
                throw new IllegalStateException("경계값이 설정되지 않은 평행우주 관계가 있습니다: " + relation);
            }
        }
    }

    public ParallelRelationResult resolve(double score, String name, String otherName) {
        ParallelRelation relation = relationOf(score);
        ParallelRelationContent content = parallelRelationLoader.getContent(relation);

        return new ParallelRelationResult(
                relation,
                content.title(),
                parallelStoryRenderer.render(content.story(), name, otherName)
        );
    }

    private ParallelRelation relationOf(double score) {
        return parallelRelationProperties.relationThresholds().entrySet().stream()
                .filter(entry -> score >= entry.getValue())
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("점수에 해당하는 평행우주 관계가 없습니다: " + score));
    }
}
