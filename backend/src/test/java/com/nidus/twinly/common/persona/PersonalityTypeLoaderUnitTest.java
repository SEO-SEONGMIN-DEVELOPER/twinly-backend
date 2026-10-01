package com.nidus.twinly.common.persona;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityTypeLoaderUnitTest {

    PersonalityTypeLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        // given: 실제 유형 문구 파일을 그대로 로드한다
        loader = new PersonalityTypeLoader(new ObjectMapper());
        loader.load();
    }

    @Test
    @DisplayName("유형 이름은 형용사 뒤에 명사를 붙인 것이고, 형용사·명사는 각자의 한 줄 소개와 설명을 그대로 갖고, 그림 key 는 코드로 짓는다")
    void get_combines_adjective_and_noun() {
        // when: 차분·탐험·계획·다정·안정
        PersonalityType type = loader.get("01110");

        // then: 설계 문서 2장의 탐구적인(01)·수호자(110) 항목과 같다
        assertThat(type.code()).isEqualTo("01110");
        assertThat(type.imageKey()).isEqualTo("personality-types/v1/01110.png");
        assertThat(type.name()).isEqualTo("탐구적인 수호자");
        assertThat(type.keywords()).containsExactly("차분", "탐험", "계획", "다정", "안정");
        assertThat(type.adjective().tagline()).isEqualTo("혼자 깊이 파고들며 넓혀 가는");
        assertThat(type.adjective().description())
                .isEqualTo("사람이 많은 곳보다 조용한 시간에 힘을 얻어요. 궁금한 게 있으면 이것저것 찾아보고, 오래 곱씹으며 생각을 키워요.");
        assertThat(type.noun().tagline()).isEqualTo("한결같이 곁을 지키는 사람");
        assertThat(type.noun().description())
                .isEqualTo("한번 맺은 관계와 약속은 끝까지 책임져요. 웬만한 일에는 흔들리지 않고, 변함없는 모습으로 주변에 든든한 버팀목이 되어 줘요.");
    }

    @Test
    @DisplayName("축 단어는 외향성·개방성·성실성·우호성·신경성 순서의 5개로, 코드가 1이면 높은 쪽 0이면 낮은 쪽 단어를 쓴다")
    void get_keywords_follow_code_order() {
        // when & then: 모든 축이 높은 쪽인 유형과 낮은 쪽인 유형
        assertThat(loader.get("11111").keywords()).containsExactly("활발", "탐험", "계획", "다정", "섬세");
        assertThat(loader.get("00000").keywords()).containsExactly("차분", "현실", "즉흥", "소신", "안정");
    }

    @Test
    @DisplayName("00000부터 11111까지 32개 코드 모두 이름과 형용사·명사의 한 줄 소개·설명이 있고 축 단어 조합과 그림 key 가 서로 겹치지 않는다")
    void get_resolves_every_code() {
        // given: 다섯 자리 이진수 32개
        List<String> codes = IntStream.range(0, 32)
                .mapToObj(n -> String.format("%5s", Integer.toBinaryString(n)).replace(' ', '0'))
                .toList();

        // when: 32개 코드 모두 조회
        List<PersonalityType> types = codes.stream().map(loader::get).toList();

        // then: 모두 이름과 한 줄 소개·설명이 있고, 32유형이 서로 다른 축 단어 조합과 서로 다른 그림을 가진다
        assertThat(types).allSatisfy(type -> {
            assertThat(type.name()).as(type.code()).isNotBlank();
            assertThat(type.keywords()).as(type.code()).hasSize(5);
            assertThat(type.adjective().tagline()).as(type.code()).isNotBlank();
            assertThat(type.adjective().description()).as(type.code()).isNotBlank();
            assertThat(type.noun().tagline()).as(type.code()).isNotBlank();
            assertThat(type.noun().description()).as(type.code()).isNotBlank();
        });
        assertThat(types).extracting(PersonalityType::keywords).doesNotHaveDuplicates();
        assertThat(types).extracting(PersonalityType::imageKey).doesNotHaveDuplicates();
    }
}
