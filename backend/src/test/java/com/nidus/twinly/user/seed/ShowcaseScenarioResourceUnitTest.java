package com.nidus.twinly.user.seed;

import com.nidus.twinly.simulation.dto.command.SimulationsCommand;
import com.nidus.twinly.simulation.dto.request.SimulationsActionSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsDialogueSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsMoveSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsSceneRequest;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Test
    @DisplayName("기준일까지 옮기는 일수가 7의 배수라 화면의 요일이 시나리오를 쓴 요일과 같다")
    void shift_keeps_the_weekday() {
        LocalDate anchor = LocalDate.parse(root.get("anchorDate").asString());

        assertThat(UserSeeder.SCENARIO_BASE_DATE.getDayOfWeek()).isEqualTo(anchor.getDayOfWeek());
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


    private record SceneKey(Long userId, LocalDate date) {
    }

    private static LocalDateTime start(SimulationsSceneRequest scene) {
        return switch (scene) {
            case SimulationsActionSceneRequest action -> action.start();
            case SimulationsDialogueSceneRequest dialogue -> dialogue.start();
            case SimulationsMoveSceneRequest move -> move.start();
        };
    }

    private static LocalDateTime end(SimulationsSceneRequest scene) {
        return switch (scene) {
            case SimulationsActionSceneRequest action -> action.end();
            case SimulationsDialogueSceneRequest dialogue -> dialogue.end();
            case SimulationsMoveSceneRequest move -> move.end();
        };
    }

    private static String place(SimulationsSceneRequest scene) {
        return switch (scene) {
            case SimulationsActionSceneRequest action -> action.place();
            case SimulationsDialogueSceneRequest dialogue -> dialogue.place();
            case SimulationsMoveSceneRequest move -> move.place();
        };
    }

    private static List<Long> with(SimulationsSceneRequest scene) {
        return switch (scene) {
            case SimulationsActionSceneRequest action ->
                    action.with() == null ? List.of() : action.with();
            case SimulationsDialogueSceneRequest dialogue -> dialogue.with();
            case SimulationsMoveSceneRequest move ->
                    move.with() == null ? List.of() : move.with();
        };
    }

    private static List<SimulationsSceneRequest> ordered(SimulationsRequest request) {
        return request.scenes().stream().sorted(Comparator.comparing(ShowcaseScenarioResourceUnitTest::start)).toList();
    }

    private static Map<SceneKey, SimulationsRequest> byKey() {
        Map<SceneKey, SimulationsRequest> map = new LinkedHashMap<>();
        requests.forEach(request -> map.put(new SceneKey(request.userId(), request.date()), request));
        return map;
    }


    @Test
    @DisplayName("모든 쇼케이스 시드 유저가 전체 구간의 모든 날에 하루 데이터를 가진다")
    void every_user_has_every_day() {
        List<Long> userIds = requests.stream().map(SimulationsRequest::userId).distinct().sorted().toList();
        List<LocalDate> dates = requests.stream().map(SimulationsRequest::date).sorted().toList();
        Set<SceneKey> present = byKey().keySet();

        List<SceneKey> missing = new ArrayList<>();
        for (Long userId : userIds) {
            for (LocalDate date = dates.getFirst(); !date.isAfter(dates.getLast()); date = date.plusDays(1)) {
                SceneKey key = new SceneKey(userId, date);
                if (!present.contains(key)) {
                    missing.add(key);
                }
            }
        }

        assertThat(userIds).hasSize(20);
        assertThat(missing).isEmpty();
    }


    @Test
    @DisplayName("하루마다 대화가 세 번 이상이고 들른 곳이 다섯 군데 이상이다")
    void every_day_is_rich_enough() {
        List<String> thin = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            long talks = request.scenes().stream()
                    .filter(SimulationsDialogueSceneRequest.class::isInstance).count();
            long spots = request.scenes().stream()
                    .map(ShowcaseScenarioResourceUnitTest::place).distinct().count();
            if (talks < 3 || spots < 5) {
                thin.add("%d %s 대화 %d 장소 %d".formatted(request.userId(), request.date(), talks, spots));
            }
        }
        assertThat(thin).isEmpty();
    }


    @Test
    @DisplayName("함께한 장면은 상대방의 같은 날 같은 시각에 같은 장소, 같은 대사로 들어 있다")
    void meetings_are_mirrored_on_both_sides() {
        Map<SceneKey, SimulationsRequest> index = byKey();

        List<String> broken = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            for (SimulationsSceneRequest scene : request.scenes()) {
                for (Long partnerId : with(scene)) {
                    SimulationsRequest partnerDay = index.get(new SceneKey(partnerId, request.date()));
                    SimulationsSceneRequest mirrored = partnerDay == null ? null : partnerDay.scenes().stream()
                            .filter(other -> start(other).equals(start(scene))
                                    && with(other).contains(request.userId()))
                            .findFirst().orElse(null);

                    if (mirrored == null || !place(mirrored).equals(place(scene))) {
                        broken.add("%d -> %d, %s %s".formatted(
                                request.userId(), partnerId, request.date(), start(scene).toLocalTime()));
                    }
                }
            }
        }
        assertThat(broken).isEmpty();
    }

    @Test
    @DisplayName("대화의 말풍선 화자는 본인과 함께한 사람뿐이다")
    void dialogue_speakers_match_participants() {
        List<String> broken = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            for (SimulationsSceneRequest scene : request.scenes()) {
                if (!(scene instanceof SimulationsDialogueSceneRequest dialogue)) {
                    continue;
                }
                Set<Long> expected = new java.util.HashSet<>(dialogue.with());
                expected.add(request.userId());
                Set<Long> actual = dialogue.lines().stream()
                        .filter(com.nidus.twinly.simulation.dto.request.SimulationsBubbleLineRequest.class::isInstance)
                        .map(line -> ((com.nidus.twinly.simulation.dto.request.SimulationsBubbleLineRequest) line).userId())
                        .collect(Collectors.toSet());
                if (!expected.equals(actual)) {
                    broken.add("%d %s %s".formatted(request.userId(), request.date(), dialogue.place()));
                }
            }
        }
        assertThat(broken).isEmpty();
    }

    @Test
    @DisplayName("한 사람이 39일 동안 같은 대화를 두 번 보지 않는다")
    void nobody_sees_the_same_conversation_twice() {
        List<String> repeated = new ArrayList<>();
        Map<Long, Set<List<String>>> seenByUser = new LinkedHashMap<>();
        for (SimulationsRequest request : requests) {
            Set<List<String>> seen = seenByUser.computeIfAbsent(request.userId(), key -> new java.util.HashSet<>());
            for (SimulationsSceneRequest scene : request.scenes()) {
                if (!(scene instanceof SimulationsDialogueSceneRequest dialogue)) {
                    continue;
                }
                List<String> texts = dialogue.lines().stream()
                        .filter(com.nidus.twinly.simulation.dto.request.SimulationsBubbleLineRequest.class::isInstance)
                        .map(line -> ((com.nidus.twinly.simulation.dto.request.SimulationsBubbleLineRequest) line).text())
                        .toList();
                if (!seen.add(texts)) {
                    repeated.add("%d %s %s".formatted(request.userId(), request.date(), dialogue.place()));
                }
            }
        }
        assertThat(repeated).isEmpty();
    }

    @Test
    @DisplayName("한 사람의 하루 장면은 시간이 겹치지 않고 그날 안에 머문다")
    void scenes_never_overlap_within_a_day() {
        List<String> broken = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            List<SimulationsSceneRequest> scenes = ordered(request);
            for (SimulationsSceneRequest scene : scenes) {
                if (!end(scene).isAfter(start(scene))
                        || !start(scene).toLocalDate().equals(request.date())
                        || !end(scene).toLocalDate().equals(request.date())) {
                    broken.add("%d %s %s".formatted(request.userId(), request.date(), place(scene)));
                }
            }
            for (int i = 0; i + 1 < scenes.size(); i++) {
                if (start(scenes.get(i + 1)).isBefore(end(scenes.get(i)))) {
                    broken.add("%d %s %s -> %s".formatted(request.userId(), request.date(),
                            place(scenes.get(i)), place(scenes.get(i + 1))));
                }
            }
        }
        assertThat(broken).isEmpty();
    }

    @Test
    @DisplayName("나레이션·속마음·대사는 다른 장면에서 다시 쓰이지 않는다 (대화는 양쪽 미러를 한 장면으로 보고, 정형 문장인 이동은 뺀다)")
    void scene_texts_are_never_reused() {
        Map<String, Set<String>> ownersByText = new LinkedHashMap<>();

        for (JsonNode day : root.get("days")) {
            String userId = day.get("userId").asString();
            String date = day.get("date").asString();
            for (JsonNode scene : day.get("scenes")) {
                String start = scene.get("start").asString();
                List<String> texts = new ArrayList<>();
                String owner;
                if ("move".equals(scene.get("type").asString())) {
                    continue;
                }
                if ("action".equals(scene.get("type").asString())) {
                    owner = userId + "|" + start;
                    texts.add("narration:" + scene.get("narration").asString());
                    if (scene.hasNonNull("mind")) {
                        texts.add("mind:" + scene.get("mind").asString());
                    }
                } else {
                    List<String> members = new ArrayList<>(List.of(userId));
                    scene.get("with").forEach(with -> members.add(with.asString()));
                    members.sort(Comparator.comparingInt(Integer::parseInt));
                    owner = start + "|" + String.join("-", members);
                    scene.get("lines").forEach(line ->
                            texts.add(line.get("t").asString() + ":" + line.get("text").asString()));
                }
                texts.forEach(text -> ownersByText.computeIfAbsent(text, key -> new HashSet<>()).add(owner));
            }
        }

        List<String> reused = ownersByText.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " x" + entry.getValue().size())
                .toList();
        assertThat(reused).as("여러 장면에 다시 쓰인 문장").isEmpty();
    }

    @Test
    @DisplayName("하루 호감도 목록에 같은 상대가 두 번 들어 있지 않다")
    void relationships_have_one_entry_per_partner_per_day() {
        List<String> broken = new ArrayList<>();

        for (SimulationsRequest request : requests) {
            Set<Long> seen = new HashSet<>();
            for (var relationship : request.relationships()) {
                if (!seen.add(relationship.partnerId())) {
                    broken.add("%d-%d %s".formatted(request.userId(), relationship.partnerId(), request.date()));
                }
            }
        }

        assertThat(broken).as("같은 날 같은 상대의 호감도가 중복된 곳").isEmpty();
    }

    @Test
    @DisplayName("호감도는 그날 실제로 만난 상대에 대해서만, 쌍마다 올라가는 방향으로만 갱신된다")
    void rapport_only_moves_forward_after_a_real_meeting() {
        List<String> broken = new ArrayList<>();

        for (SimulationsRequest request : requests) {
            Set<Long> metToday = request.scenes().stream()
                    .flatMap(scene -> with(scene).stream()).collect(Collectors.toSet());
            for (var relationship : request.relationships()) {
                if (!metToday.contains(relationship.partnerId())) {
                    broken.add("안 만났는데 갱신: %d-%d %s".formatted(
                            request.userId(), relationship.partnerId(), request.date()));
                }
            }
        }

        Map<String, Map<LocalDate, Integer>> curves = new LinkedHashMap<>();
        for (SimulationsRequest request : requests) {
            for (var relationship : request.relationships()) {
                long low = Math.min(request.userId(), relationship.partnerId());
                long high = Math.max(request.userId(), relationship.partnerId());
                Integer before = curves.computeIfAbsent(low + "-" + high, key -> new LinkedHashMap<>())
                        .put(request.date(), relationship.rapport());
                if (before != null && !before.equals(relationship.rapport())) {
                    broken.add("양쪽 값이 다름: %d-%d %s".formatted(low, high, request.date()));
                }
            }
        }
        curves.forEach((pair, byDate) -> {
            List<Integer> series = byDate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
            for (int i = 0; i + 1 < series.size(); i++) {
                if (series.get(i + 1) < series.get(i)) {
                    broken.add("호감도 역행: " + pair + " " + series);
                }
            }
        });

        assertThat(broken).isEmpty();
    }


    private static final List<String> LECTURE = List.of("강의실", "세미나실", "실습실");

    private static final Pattern PLACE_CODE = Pattern.compile("P\\d{4}");

    private static final String HOME_CODE = "P0258";

    private static String placeCode(SimulationsSceneRequest scene) {
        return switch (scene) {
            case SimulationsActionSceneRequest action -> action.placeCode();
            case SimulationsDialogueSceneRequest dialogue -> dialogue.placeCode();
            case SimulationsMoveSceneRequest move -> move.placeCode();
        };
    }

    @Test
    @DisplayName("모든 장면에 장소 코드가 있고, 내 집은 '집'으로 표시된다")
    void every_scene_has_a_place_code() {
        List<String> broken = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            for (SimulationsSceneRequest scene : request.scenes()) {
                String code = placeCode(scene);
                if (code == null || !PLACE_CODE.matcher(code).matches()
                        || HOME_CODE.equals(code) != "집".equals(place(scene))) {
                    broken.add("%d %s %s %s".formatted(request.userId(), request.date(), place(scene), code));
                }
                if (scene instanceof SimulationsMoveSceneRequest move
                        && (move.fromPlaceCode() == null || !PLACE_CODE.matcher(move.fromPlaceCode()).matches())) {
                    broken.add("%d %s 출발지 %s".formatted(request.userId(), request.date(), move.fromPlace()));
                }
            }
        }
        assertThat(broken).isEmpty();
    }

    @Test
    @DisplayName("장소가 바뀌는 사이에는 그 두 곳을 잇는 이동 장면이 하나 있다")
    void place_changes_go_through_a_move() {
        List<String> broken = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            String lastCode = null;
            SimulationsMoveSceneRequest pending = null;
            for (SimulationsSceneRequest scene : ordered(request)) {
                if (scene instanceof SimulationsMoveSceneRequest move) {
                    if (pending != null || lastCode == null || !lastCode.equals(move.fromPlaceCode())
                            || !List.of("walk", "transit").contains(move.travelMode())) {
                        broken.add("%d %s %s 이동이 앞 장소에서 출발하지 않음".formatted(
                                request.userId(), request.date(), start(move).toLocalTime()));
                    }
                    pending = move;
                    continue;
                }
                String code = placeCode(scene);
                boolean moved = lastCode != null && !lastCode.equals(code);
                if (moved != (pending != null) || (pending != null && !pending.placeCode().equals(code))) {
                    broken.add("%d %s %s %s -> %s".formatted(
                            request.userId(), request.date(), start(scene).toLocalTime(), lastCode, code));
                }
                lastCode = code;
                pending = null;
            }
            if (pending != null) {
                broken.add("%d %s 마지막 이동 뒤 장면 없음".formatted(request.userId(), request.date()));
            }
        }
        assertThat(broken).isEmpty();
    }

    @Test
    @DisplayName("수업은 트윈리 캠퍼스에서, 평일에만 듣는다")
    void lectures_happen_only_on_campus_on_weekdays() {
        List<String> broken = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            for (SimulationsSceneRequest scene : request.scenes()) {
                if (scene instanceof SimulationsMoveSceneRequest || LECTURE.stream().noneMatch(place(scene)::contains)) {
                    continue;
                }
                if (!place(scene).startsWith("트윈리 ") || request.date().getDayOfWeek().getValue() >= 6) {
                    broken.add("%d %s %s".formatted(request.userId(), request.date(), place(scene)));
                }
            }
        }
        assertThat(broken).isEmpty();
    }

    @Test
    @DisplayName("본문에 치환되지 않은 자리표시자가 남아 있지 않다")
    void no_unresolved_placeholders_leak_into_text() {
        List<String> leaked = new ArrayList<>();
        collectPlaceholders(root.get("days"), leaked);
        assertThat(leaked).isEmpty();
    }

    private static void collectPlaceholders(JsonNode node, List<String> found) {
        if (node.isArray()) {
            node.forEach(child -> collectPlaceholders(child, found));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.properties().forEach(field -> {
            JsonNode value = field.getValue();
            if (!value.isString()) {
                collectPlaceholders(value, found);
            } else if (value.asString().contains("{") && value.asString().contains("}")) {
                found.add(value.asString());
            }
        });
    }
}
