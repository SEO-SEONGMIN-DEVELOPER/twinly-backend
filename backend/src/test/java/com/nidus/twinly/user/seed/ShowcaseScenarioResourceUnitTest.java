package com.nidus.twinly.user.seed;

import com.nidus.twinly.simulation.dto.command.SimulationsCommand;
import com.nidus.twinly.simulation.dto.request.SimulationsRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShowcaseScenarioResourceUnitTest {

    static JsonNode root;
    static List<SimulationsRequest> requests;

    @BeforeAll
    static void loadResource() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        try (InputStream in = new ClassPathResource("seed/showcase-scenarios.json").getInputStream()) {
            root = objectMapper.readTree(in);
        }

        requests = new ArrayList<>();
        for (JsonNode day : root.get("days")) {
            requests.add(objectMapper.treeToValue(day, SimulationsRequest.class));
        }
    }

    @Test
    @DisplayName("시드 리소스는 시뮬레이션 요청 형식으로 그대로 역직렬화된다")
    void deserializes_into_simulations_request() {
        assertThat(requests).isNotEmpty();
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.userId()).isNotNull();
            assertThat(request.date()).isNotNull();
            assertThat(request.scenes()).isNotEmpty();
            assertThat(request.questions()).isNotNull();
            assertThat(request.relationships()).isNotNull();
        });
    }

    @Test
    @DisplayName("모든 하루 데이터는 커맨드 변환까지 통과한다")
    void converts_into_command() {
        assertThat(requests).allSatisfy(request ->
                assertThat(SimulationsCommand.from(request).scenes()).isNotEmpty());
    }

    @Test
    @DisplayName("기준일이 있고 날짜 범위가 기준일을 가운데 낀다")
    void anchor_date_sits_inside_the_range() {
        LocalDate anchor = LocalDate.parse(root.get("anchorDate").asString());
        List<LocalDate> dates = requests.stream().map(SimulationsRequest::date).sorted().toList();

        assertThat(dates.getFirst()).isBeforeOrEqualTo(anchor);
        assertThat(dates.getLast()).isAfter(anchor);
    }

    private static final Pattern DATE_LIKE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}.*");

    private static void collectTemporal(JsonNode node, List<String> found) {
        if (node.isArray()) {
            node.forEach(child -> collectTemporal(child, found));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.properties().forEach(field -> {
            if (field.getValue().isString() && DATE_LIKE.matcher(field.getValue().asString()).matches()) {
                found.add(field.getValue().asString());
            } else {
                collectTemporal(field.getValue(), found);
            }
        });
    }

    @Test
    @DisplayName("날짜 형태 값은 이름과 무관하게 전부 같은 일수만큼 밀린다")
    void every_temporal_value_shifts() throws IOException {
        // given: 리소스를 다시 읽어 원본 시각을 모아둔다
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode fresh;
        try (InputStream in = new ClassPathResource("seed/showcase-scenarios.json").getInputStream()) {
            fresh = objectMapper.readTree(in);
        }
        List<String> before = new ArrayList<>();
        collectTemporal(fresh.get("days"), before);

        // when: 임의의 일수만큼 민다 (오늘이 기준일이면 shift 가 0 이라 이동 누락이 안 보인다)
        long shift = 66;
        fresh.get("days").forEach(day -> UserSeeder.shiftDates(day, shift));

        List<String> after = new ArrayList<>();
        collectTemporal(fresh.get("days"), after);

        // then: 하나도 빠짐없이 정확히 66일 이동한다
        assertThat(after).hasSameSizeAs(before);
        assertThat(before).isNotEmpty();
        for (int i = 0; i < before.size(); i++) {
            String original = before.get(i);
            String expected = original.length() == 10
                    ? LocalDate.parse(original).plusDays(shift).toString()
                    : LocalDateTime.parse(original).plusDays(shift).toString();
            assertThat(after.get(i)).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("호감도 갱신 시각은 그 하루의 날짜 안에 있다")
    void relationship_update_time_stays_within_its_day() {
        assertThat(requests).allSatisfy(request ->
                assertThat(request.relationships()).allSatisfy(relationship ->
                        assertThat(relationship.updateTime().toLocalDate()).isEqualTo(request.date())));
    }

    @Test
    @DisplayName("임계점을 넘긴 쌍이 과거와 미래에 모두 있어 즉시 개설과 예약이 함께 검증된다")
    void threshold_crossings_span_past_and_future() {
        LocalDate anchor = LocalDate.parse(root.get("anchorDate").asString());
        long past = 0;
        long future = 0;

        for (SimulationsRequest request : requests) {
            for (var relationship : request.relationships()) {
                if (relationship.rapport() < 70) {
                    continue;
                }
                LocalDateTime at = relationship.updateTime();
                if (at.toLocalDate().isBefore(anchor)) {
                    past++;
                } else {
                    future++;
                }
            }
        }

        assertThat(past).isPositive();
        assertThat(future).isPositive();
    }
}
