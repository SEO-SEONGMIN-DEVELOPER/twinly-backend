package com.nidus.twinly.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    @DisplayName("서로 다른 ErrorCode가 같은 기본 메시지를 쓰지 않는다")
    void default_messages_are_distinct() {
        // given: 응답 message는 항상 defaultMessage로 나간다
        Map<String, List<String>> byMessage = Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(ErrorCode::getDefaultMessage,
                        Collectors.mapping(Enum::name, Collectors.toList())));

        // when & then: 문구가 겹치면 사용자가 서로 다른 상황을 구분할 수 없다
        assertThat(byMessage.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .isEmpty();
    }
}
