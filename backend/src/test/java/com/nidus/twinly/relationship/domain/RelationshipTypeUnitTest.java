package com.nidus.twinly.relationship.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RelationshipTypeUnitTest {

    @ParameterizedTest(name = "친밀도 {0} → {1}")
    @DisplayName("친밀도 경계값에서 관계 타입이 바뀐다 (35 미만 지인, 70 미만 친구, 이상 베프)")
    @CsvSource({
            "0, ACQUAINTANCE",
            "34, ACQUAINTANCE",
            "35, FRIEND",
            "69, FRIEND",
            "70, BEST_FRIEND",
            "100, BEST_FRIEND"
    })
    void fromIntimacy_boundaries(int intimacy, RelationshipType expected) {
        assertThat(RelationshipType.fromIntimacy(intimacy)).isEqualTo(expected);
    }

    @Test
    @DisplayName("각 타입의 최소 친밀도가 fromIntimacy 판정과 같은 값을 쓴다")
    void minIntimacy_is_the_boundary_used_by_fromIntimacy() {
        for (RelationshipType type : RelationshipType.values()) {
            assertThat(RelationshipType.fromIntimacy(type.minIntimacy())).isEqualTo(type);
        }
    }
}
