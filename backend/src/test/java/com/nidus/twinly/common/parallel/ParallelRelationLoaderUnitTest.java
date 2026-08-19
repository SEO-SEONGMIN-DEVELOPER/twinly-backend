package com.nidus.twinly.common.parallel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelRelationLoaderUnitTest {

    ParallelRelationLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        // given: 실제 문구 파일을 그대로 로드한다
        loader = new ParallelRelationLoader(new ObjectMapper(), new ParallelStoryRenderer());
        loader.load();
    }

    @Test
    @DisplayName("모든 등급에 제목과 이야기가 여러 개 있다")
    void every_relation_has_contents() {
        // when & then: 등급을 추가하고 문구를 빠뜨리면 기동 시점에 실패한다
        assertThat(ParallelRelationType.values()).allSatisfy(relation -> {
            List<ParallelRelationContent> contents = loader.getContents(relation);

            assertThat(contents).isNotEmpty();
            assertThat(contents).allSatisfy(content -> {
                assertThat(content.title()).isNotBlank();
                assertThat(content.story()).isNotBlank();
            });
        });
    }

    @Test
    @DisplayName("모든 이야기에 두 사람의 이름 자리가 들어 있다")
    void every_story_has_both_name_placeholders() {
        // when & then: 공유용 문구에서 이름이 빠지는 사고를 막는다
        assertThat(ParallelRelationType.values()).allSatisfy(relation ->
                assertThat(loader.getContents(relation)).allSatisfy(content ->
                        assertThat(content.story()).contains("{A", "{B")));
    }

    @Test
    @DisplayName("이야기 제목이 전부 서로 다르다")
    void titles_are_distinct() {
        // given
        List<String> titles = Arrays.stream(ParallelRelationType.values())
                .flatMap(relation -> loader.getContents(relation).stream())
                .map(ParallelRelationContent::title)
                .toList();

        // when & then: 복사 후 수정을 잊은 문구를 잡아낸다
        assertThat(titles).doesNotHaveDuplicates();
    }
}
