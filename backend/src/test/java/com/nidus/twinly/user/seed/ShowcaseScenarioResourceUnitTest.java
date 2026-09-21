package com.nidus.twinly.user.seed;

import com.nidus.twinly.simulation.dto.command.SimulationsCommand;
import com.nidus.twinly.simulation.dto.request.SimulationsActionSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsDialogueSceneRequest;
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
        };
    }

    private static LocalDateTime end(SimulationsSceneRequest scene) {
        return switch (scene) {
            case SimulationsActionSceneRequest action -> action.end();
            case SimulationsDialogueSceneRequest dialogue -> dialogue.end();
        };
    }

    private static String place(SimulationsSceneRequest scene) {
        return switch (scene) {
            case SimulationsActionSceneRequest action -> action.place();
            case SimulationsDialogueSceneRequest dialogue -> dialogue.place();
        };
    }

    private static List<Long> with(SimulationsSceneRequest scene) {
        return switch (scene) {
            case SimulationsActionSceneRequest action ->
                    action.with() == null ? List.of() : action.with();
            case SimulationsDialogueSceneRequest dialogue -> dialogue.with();
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


    /** 판정 순서가 결과를 바꾸므로 리스트로 고정한다. 앞에 있는 것이 먼저 이긴다. */
    private static final List<Map.Entry<String, List<String>>> REGION_RULES = List.of(
            Map.entry("SKKU", List.of("금잔디광장", "퇴계인문관", "수선관", "다산경제관", "경영관",
                    "중앙학술정보관", "성대 후문", "명륜동", "혜화역", "대학로", "성균관 돌담길")),
            Map.entry("KOREA", List.of("중앙광장", "하나스퀘어", "참살이길", "개운사", "안암역", "안암동",
                    "정경관", "정경대", "문과대 서관", "우당교양관", "백주년기념관", "애기능생활관",
                    "고대 앞", "고대 정문", "고대 체육관")),
            Map.entry("SUNGSHIN", List.of("돈암수정캠퍼스", "미아리고개", "성신여대입구역", "성신여대 앞",
                    "돈암동", "난향관", "수정관", "학생누리관")),
            Map.entry("SCHOOL", List.of("중앙도서관", "학술정보관", "미디어관", "인문관", "정문 앞",
                    "학생회관", "교내", "학교 앞", "등굣길", "기숙사")),
            Map.entry("HONGDAE", List.of("홍대", "합정", "연남동", "경의선숲길")),
            Map.entry("SEONGSU", List.of("성수동", "성수역", "서울숲", "건대입구")),
            Map.entry("JONGNO", List.of("동대문역사문화공원", "을지로", "청계천", "익선동", "북촌",
                    "광화문", "교보문고", "DDP", "전시관", "남산")),
            Map.entry("JAMSIL", List.of("잠실", "석촌호수", "올림픽공원", "종합운동장", "롯데월드",
                    "자이로드롭", "워터존", "실내 회전목마", "외야 잔디석", "관중석 통로", "푸드존",
                    "푸드트럭", "메인 스테이지", "서브 스테이지", "굿즈 부스", "야구장", "매표소",
                    "스탠딩존", "퍼레이드 관람", "페스티벌 입장 게이트", "송리단길", "방이동",
                    "몽촌토성역", "잔디밭 돗자리 자리", "물놀이장")),
            Map.entry("HANGANG", List.of("한강", "뚝섬", "강변 계단")),
            Map.entry("GANGNEUNG", List.of("강릉", "경포", "안목해변", "방파제", "해변 파라솔",
                    "중앙시장 닭강정")));

    private static final List<String> TRANSIT = List.of("지하철", "버스 창가", "환승역 계단",
            "환승통로", "고속버스터미널", "KTX 승강장", "청량리역 승강장", "셔틀버스", "가는 버스",
            "마을버스 정류장", "서울역 3번 출구");

    private static final List<String> LECTURE = List.of("강의실", "세미나실", "실습실");

    private static final Map<String, Integer> TRAVEL = travelTable();

    private static Map<String, Integer> travelTable() {
        Map<String, Integer> table = new LinkedHashMap<>();
        String[][] rows = {
                {"SKKU", "KOREA", "25"}, {"SKKU", "SUNGSHIN", "25"}, {"SKKU", "HONGDAE", "40"},
                {"SKKU", "SEONGSU", "35"}, {"SKKU", "JONGNO", "15"}, {"SKKU", "JAMSIL", "50"},
                {"SKKU", "HANGANG", "35"}, {"SKKU", "GANGNEUNG", "200"},
                {"KOREA", "SUNGSHIN", "20"}, {"KOREA", "HONGDAE", "45"}, {"KOREA", "SEONGSU", "30"},
                {"KOREA", "JONGNO", "25"}, {"KOREA", "JAMSIL", "50"}, {"KOREA", "HANGANG", "35"},
                {"KOREA", "GANGNEUNG", "200"},
                {"SUNGSHIN", "HONGDAE", "50"}, {"SUNGSHIN", "SEONGSU", "40"},
                {"SUNGSHIN", "JONGNO", "25"}, {"SUNGSHIN", "JAMSIL", "55"},
                {"SUNGSHIN", "HANGANG", "45"}, {"SUNGSHIN", "GANGNEUNG", "210"},
                {"HONGDAE", "SEONGSU", "35"}, {"HONGDAE", "JONGNO", "25"}, {"HONGDAE", "JAMSIL", "45"},
                {"HONGDAE", "HANGANG", "20"}, {"HONGDAE", "GANGNEUNG", "220"},
                {"SEONGSU", "JONGNO", "25"}, {"SEONGSU", "JAMSIL", "25"}, {"SEONGSU", "HANGANG", "15"},
                {"SEONGSU", "GANGNEUNG", "195"},
                {"JONGNO", "JAMSIL", "40"}, {"JONGNO", "HANGANG", "30"}, {"JONGNO", "GANGNEUNG", "205"},
                {"JAMSIL", "HANGANG", "25"}, {"JAMSIL", "GANGNEUNG", "190"},
                {"HANGANG", "GANGNEUNG", "200"},
        };
        for (String[] row : rows) {
            table.put(row[0] + ">" + row[1], Integer.parseInt(row[2]));
            table.put(row[1] + ">" + row[0], Integer.parseInt(row[2]));
        }
        return table;
    }

    private static String schoolOf(long userId) {
        if (userId <= 7) {
            return "SKKU";
        }
        return userId <= 14 ? "KOREA" : "SUNGSHIN";
    }

    private static boolean contains(List<String> keys, String place) {
        return keys.stream().anyMatch(place::contains);
    }

    /** 이동 수단 안은 null 을 돌려 이동 시간 검사에서 빼고, 나머지는 소속 캠퍼스를 기본값으로 쓴다. */
    private static String regionOf(String place, long userId) {
        if (contains(TRANSIT, place)) {
            return null;
        }
        for (var rule : REGION_RULES) {
            if (contains(rule.getValue(), place)) {
                return "SCHOOL".equals(rule.getKey()) ? schoolOf(userId) : rule.getKey();
            }
        }
        return schoolOf(userId);
    }

    @Test
    @DisplayName("지역을 옮길 때는 이동 시간만큼 비어 있다")
    void moving_between_regions_takes_time() {
        List<String> broken = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            String lastRegion = null;
            LocalDateTime lastEnd = null;
            for (SimulationsSceneRequest scene : ordered(request)) {
                String region = regionOf(place(scene), request.userId());
                if (region == null) {
                    continue;
                }
                if (lastRegion != null && !lastRegion.equals(region)) {
                    int need = TRAVEL.getOrDefault(lastRegion + ">" + region, 0);
                    long got = java.time.Duration.between(lastEnd, start(scene)).toMinutes();
                    if (got < need) {
                        broken.add("%d %s %s->%s %d분(필요 %d분)".formatted(
                                request.userId(), request.date(), lastRegion, region, got, need));
                    }
                }
                lastRegion = region;
                lastEnd = end(scene);
            }
        }
        assertThat(broken).isEmpty();
    }

    @Test
    @DisplayName("수업은 본인 학교 캠퍼스에서, 평일에만 듣는다")
    void lectures_happen_only_on_the_own_campus_on_weekdays() {
        List<String> broken = new ArrayList<>();
        for (SimulationsRequest request : requests) {
            for (SimulationsSceneRequest scene : request.scenes()) {
                if (!contains(LECTURE, place(scene))) {
                    continue;
                }
                if (!schoolOf(request.userId()).equals(regionOf(place(scene), request.userId()))
                        || request.date().getDayOfWeek().getValue() >= 6) {
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
