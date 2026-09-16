package com.nidus.twinly.user.seed;

import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.interest.InterestLoader;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.purchase.writer.PurchaseWriter;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.simulation.dto.command.SimulationsCommand;
import com.nidus.twinly.simulation.dto.request.SimulationsRequest;
import com.nidus.twinly.simulation.service.SimulationService;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@Order(2)
@Profile({"prod", "stage", "local"})
@RequiredArgsConstructor
public class UserSeeder implements ApplicationRunner {

    private static final int DETAIL_ELEMENTS_PER_USER = 5;
    private static final int INTERESTS_PER_USER = 5;
    private static final long ANSWER_SEED = 20260817L;
    private static final String PHONE_PREFIX = "0100000";
    private static final int PHONE_START = 9001;
    private static final String EMAIL_LOCAL_PREFIX = "test-seed";
    private static final String SCENARIO_RESOURCE = "seed/showcase-scenarios.json";
    private static final LocalDate SCENARIO_BASE_DATE = LocalDate.of(2026, 9, 16);
    private static final String PERSONA_RESOURCE = "seed/ai-test-personas.json";
    private static final int PERSONA_DETAILS_PER_USER = 8;
    private static final int SIMULATION_ACCESS_USER_COUNT = 50;
    static final String ANCHOR_DATE = "anchorDate";
    static final Set<String> USER_REF_FIELDS = Set.of("userId", "partnerId", "with");
    private static final String DAYS = "days";
    static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    static final Pattern DATE_TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

    private static final List<String> INTEREST_POOL = List.of(
            "영화", "애니메이션", "드라마", "음악", "K-POP", "힙합", "인디음악", "콘서트",
            "운동", "헬스", "러닝", "등산", "클라이밍", "수영", "테니스", "볼링",
            "맛집", "카페", "커피", "디저트", "요리", "와인", "맥주",
            "여행", "캠핑", "호캉스", "독서", "웹툰", "게임", "보드게임", "방탈출",
            "사진", "전시", "뮤지컬", "반려동물", "강아지", "고양이",
            "패션", "인테리어", "식물키우기", "재테크", "주식", "MBTI", "타로"
    );

    private final UserRepository userRepository;
    private final PersonaElementRepository personaElementRepository;
    private final BlindIndexHasher blindIndexHasher;
    private final SurveyLoader surveyLoader;
    private final InterestLoader interestLoader;
    private final CurrentSeasonReader currentSeasonReader;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final PurchaseWriter purchaseWriter;
    private final SimulationService simulationService;
    private final SceneRepository sceneRepository;
    private final SeedProperties seedProperties;
    private final ObjectMapper objectMapper;

    enum SeedOrganization {
        SKKU("성균관대학교", "skku.edu"),
        KOREA("고려대학교", "korea.ac.kr"),
        SUNGSHIN("성신여자대학교", "sungshin.ac.kr");

        private final String organizationName;
        private final String domain;

        SeedOrganization(String organizationName, String domain) {
            this.organizationName = organizationName;
            this.domain = domain;
        }
    }

    private record SeedUser(
            String familyName,
            String givenName,
            Gender gender,
            SeedOrganization organization,
            String affiliation,
            List<String> details,
            String summary
    ) {
        SeedUser(String familyName, String givenName, Gender gender, SeedOrganization organization, String affiliation) {
            this(familyName, givenName, gender, organization, affiliation, List.of(), null);
        }

        SeedUser withPersona(List<String> details, String summary) {
            return new SeedUser(familyName, givenName, gender, organization, affiliation, details, summary);
        }
    }

    record AiTestPersona(
            String familyName,
            String givenName,
            Gender gender,
            SeedOrganization organization,
            String affiliation,
            List<String> details,
            String summary
    ) {
        SeedUser toSeedUser() {
            return new SeedUser(familyName, givenName, gender, organization, affiliation, details, summary);
        }
    }

    private static final List<SeedUser> SHOWCASE_USERS = List.of(
            new SeedUser("김", "도윤", Gender.MALE, SeedOrganization.SKKU, "미디어커뮤니케이션학과"),
            new SeedUser("이", "시우", Gender.MALE, SeedOrganization.SKKU, "경영학과"),
            new SeedUser("박", "하준", Gender.MALE, SeedOrganization.SKKU, "심리학과"),
            new SeedUser("최", "은우", Gender.MALE, SeedOrganization.SKKU, "통계학과"),
            new SeedUser("정", "지호", Gender.MALE, SeedOrganization.SKKU, "사회학과"),
            new SeedUser("강", "서윤", Gender.FEMALE, SeedOrganization.SKKU, "영어영문학과"),
            new SeedUser("조", "하윤", Gender.FEMALE, SeedOrganization.SKKU, "경제학과"),
            new SeedUser("윤", "준서", Gender.MALE, SeedOrganization.KOREA, "사회학과"),
            new SeedUser("장", "유찬", Gender.MALE, SeedOrganization.KOREA, "철학과"),
            new SeedUser("임", "서준", Gender.MALE, SeedOrganization.KOREA, "영어영문학과"),
            new SeedUser("한", "이든", Gender.MALE, SeedOrganization.KOREA, "국어국문학과"),
            new SeedUser("오", "재이", Gender.MALE, SeedOrganization.KOREA, "한국사학과"),
            new SeedUser("서", "지우", Gender.FEMALE, SeedOrganization.KOREA, "사학과"),
            new SeedUser("신", "서연", Gender.FEMALE, SeedOrganization.KOREA, "독어독문학과"),
            new SeedUser("권", "다인", Gender.FEMALE, SeedOrganization.SUNGSHIN, "국어국문학과"),
            new SeedUser("황", "예린", Gender.FEMALE, SeedOrganization.SUNGSHIN, "영어영문학과"),
            new SeedUser("안", "시아", Gender.FEMALE, SeedOrganization.SUNGSHIN, "일본어문·문화학과"),
            new SeedUser("송", "유주", Gender.FEMALE, SeedOrganization.SUNGSHIN, "문화예술경영학과"),
            new SeedUser("류", "채원", Gender.FEMALE, SeedOrganization.SUNGSHIN, "중국어문·문화학과"),
            new SeedUser("전", "지아", Gender.FEMALE, SeedOrganization.SUNGSHIN, "프랑스어문·문화학과")
    );

    @Override
    public void run(ApplicationArguments args) throws IOException {
        requireEnoughElements();

        List<SeedUser> seedUsers = loadSeedUsers();
        Instant now = Instant.now();

        List<User> users = new ArrayList<>();
        for (int index = 0; index < seedUsers.size(); index++) {
            users.add(findOrCreateUser(seedUsers.get(index), index));
        }

        Long currentSeasonId = currentSeasonReader.read().getId();
        users.forEach(user -> seasonParticipationRepository.upsert(user.getId(), currentSeasonId));

        int accessEnd = Math.min(SHOWCASE_USERS.size() + SIMULATION_ACCESS_USER_COUNT, users.size());

        revokeSimulationAccess(users.subList(0, SHOWCASE_USERS.size()));
        grantSimulationAccess(users.subList(SHOWCASE_USERS.size(), accessEnd), now);
        revokeSimulationAccess(users.subList(accessEnd, users.size()));

        List<PersonaElement> elements = new ArrayList<>();
        for (int index = 0; index < users.size(); index++) {
            Long userId = users.get(index).getId();
            SeedUser seed = seedUsers.get(index);

            if (!personaElementRepository.existsByUserId(userId)) {
                elements.addAll(toPersonaElements(userId, seed, index, now));
            } else if (!personaElementRepository.existsByUserIdAndDimension(userId, PersonaDimension.SUMMARY)) {
                elements.add(summaryElement(userId, seed, now));
            }
        }

        if (!elements.isEmpty()) {
            personaElementRepository.saveAll(elements);
        }

        seedScenarios(users.subList(0, SHOWCASE_USERS.size()));

        InfoLog.log(log, "시드 유저를 채웠습니다.", field("userCount", users.size()), field("elementCount", elements.size()));
    }

    private void revokeSimulationAccess(List<User> users) {
        if (users.isEmpty()) {
            return;
        }

        List<UserEntitlement> granted = userEntitlementRepository.findAllByUserIdInAndEntitlement(
                users.stream().map(User::getId).toList(), EntitlementReader.SIMULATION_ACCESS);

        if (!granted.isEmpty()) {
            userEntitlementRepository.deleteAll(granted);
        }
    }

    private void grantSimulationAccess(List<User> users, Instant now) {
        if (users.isEmpty()) {
            return;
        }

        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<Long, UserEntitlement> existing = userEntitlementRepository
                .findAllByUserIdInAndEntitlement(userIds, EntitlementReader.SIMULATION_ACCESS)
                .stream()
                .collect(Collectors.toMap(UserEntitlement::getUserId, Function.identity()));

        List<UserEntitlement> granted = new ArrayList<>();
        for (Long userId : userIds) {
            UserEntitlement entitlement = existing.get(userId);

            if (entitlement == null) {
                granted.add(UserEntitlement.create(userId, EntitlementReader.SIMULATION_ACCESS, null, now));
            } else if (entitlement.getExpiresAt() != null) {
                entitlement.sync(null, now);
                granted.add(entitlement);
            }
        }

        if (!granted.isEmpty()) {
            userEntitlementRepository.saveAll(granted);
        }

        userIds.forEach(purchaseWriter::assignPool);
    }

    private void seedScenarios(List<User> showcaseUsers) throws IOException {
        JsonNode root;
        try (InputStream in = new ClassPathResource(SCENARIO_RESOURCE).getInputStream()) {
            root = objectMapper.readTree(in);
        }

        long shift = ChronoUnit.DAYS.between(LocalDate.parse(root.get(ANCHOR_DATE).asString()), SCENARIO_BASE_DATE);
        Map<String, String> userIdByRef = userIdByRef(showcaseUsers);

        List<SimulationsRequest> requests = new ArrayList<>();
        for (JsonNode day : root.get(DAYS)) {
            shiftDates(day, shift);
            remapUserRefs(day, userIdByRef);
            requests.add(objectMapper.treeToValue(day, SimulationsRequest.class));
        }

        List<SimulationsRequest> missing = requests.stream()
                .filter(request -> !sceneRepository.existsByUserIdAndDate(request.userId(), request.date()))
                .toList();

        missing.forEach(request -> simulationService.simulations(request.userId(), SimulationsCommand.from(request)));

        InfoLog.log(log, "쇼케이스 시나리오를 채웠습니다.", field("dayCount", requests.size()), field("insertedCount", missing.size()), field("shiftDays", shift));
    }

    /**
     * 값이 날짜 형태면 무조건 민다. 필드 이름 목록을 두면 새 시각 필드가 생길 때마다
     * 사람이 목록을 갱신해야 하고, 빠뜨려도 아무 데서도 안 걸린다.
     */
    static void shiftDates(JsonNode node, long shift) {
        if (node.isArray()) {
            node.forEach(child -> shiftDates(child, shift));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        ObjectNode object = (ObjectNode) node;
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            JsonNode value = field.getValue();

            if (!value.isString()) {
                shiftDates(value, shift);
                continue;
            }

            String shifted = shiftTemporal(value.asString(), shift);
            if (shifted != null) {
                object.put(field.getKey(), shifted);
            }
        }
    }

    private static Map<String, String> userIdByRef(List<User> showcaseUsers) {
        Map<String, String> userIdByRef = new HashMap<>();
        for (int index = 0; index < showcaseUsers.size(); index++) {
            userIdByRef.put(String.valueOf(index + 1), String.valueOf(showcaseUsers.get(index).getId()));
        }

        return Map.copyOf(userIdByRef);
    }

    static void remapUserRefs(JsonNode node, Map<String, String> userIdByRef) {
        if (node.isArray()) {
            node.forEach(child -> remapUserRefs(child, userIdByRef));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        ObjectNode object = (ObjectNode) node;
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            JsonNode value = field.getValue();

            if (!USER_REF_FIELDS.contains(field.getKey())) {
                remapUserRefs(value, userIdByRef);
                continue;
            }

            if (value.isString()) {
                object.put(field.getKey(), toUserId(value.asString(), userIdByRef));
            } else if (value.isArray()) {
                List<String> userIds = new ArrayList<>();
                value.forEach(ref -> userIds.add(toUserId(ref.asString(), userIdByRef)));
                ArrayNode array = object.putArray(field.getKey());
                userIds.forEach(array::add);
            }
        }
    }

    private static String toUserId(String ref, Map<String, String> userIdByRef) {
        String userId = userIdByRef.get(ref);
        if (userId == null) {
            throw new IllegalStateException("시나리오에 쇼케이스 유저가 아닌 번호가 있습니다. ref=" + ref);
        }

        return userId;
    }

    private static String shiftTemporal(String value, long shift) {
        if (DATE.matcher(value).matches()) {
            return LocalDate.parse(value).plusDays(shift).toString();
        }
        if (DATE_TIME.matcher(value).matches()) {
            return LocalDateTime.parse(value).plusDays(shift).toString();
        }
        return null;
    }

    private User findOrCreateUser(SeedUser seed, int index) {
        return userRepository.findByEmailHash(blindIndexHasher.hash(email(seed, index)))
                .orElseGet(() -> userRepository.save(toUser(seed, index)));
    }

    private List<SeedUser> loadSeedUsers() throws IOException {
        List<SeedUser> seedUsers = new ArrayList<>();
        for (int index = 0; index < SHOWCASE_USERS.size(); index++) {
            seedUsers.add(SHOWCASE_USERS.get(index).withPersona(details(index), PersonaSeedElements.SUMMARY.get(index)));
        }

        if (!seedProperties.aiTestUsers()) {
            return List.copyOf(seedUsers);
        }

        List<AiTestPersona> personas;
        try (InputStream in = new ClassPathResource(PERSONA_RESOURCE).getInputStream()) {
            personas = objectMapper.readValue(in, objectMapper.getTypeFactory().constructCollectionType(List.class, AiTestPersona.class));
        }

        for (AiTestPersona persona : personas) {
            if (persona.details() == null || persona.details().size() != PERSONA_DETAILS_PER_USER) {
                throw new IllegalStateException("AI 테스트 페르소나의 detail 수가 맞지 않습니다. name=%s%s, required=%d"
                        .formatted(persona.familyName(), persona.givenName(), PERSONA_DETAILS_PER_USER));
            }
            if (persona.summary() == null || persona.summary().isBlank()) {
                throw new IllegalStateException("AI 테스트 페르소나의 summary 가 비어 있습니다. name=%s%s"
                        .formatted(persona.familyName(), persona.givenName()));
            }
            seedUsers.add(persona.toSeedUser());
        }

        return List.copyOf(seedUsers);
    }

    private void requireEnoughElements() {
        int required = SHOWCASE_USERS.size() * DETAIL_ELEMENTS_PER_USER;

        if (PersonaSeedElements.DETAIL.size() < required) {
            throw new IllegalStateException("페르소나 시드 문장이 부족합니다. dimension=%s, required=%d, actual=%d"
                    .formatted(PersonaDimension.DETAIL, required, PersonaSeedElements.DETAIL.size()));
        }

        if (PersonaSeedElements.SUMMARY.size() < SHOWCASE_USERS.size()) {
            throw new IllegalStateException("페르소나 시드 문장이 부족합니다. dimension=%s, required=%d, actual=%d"
                    .formatted(PersonaDimension.SUMMARY, SHOWCASE_USERS.size(), PersonaSeedElements.SUMMARY.size()));
        }

        if (INTEREST_POOL.size() < INTERESTS_PER_USER) {
            throw new IllegalStateException("시드 관심사가 부족합니다. required=%d, actual=%d"
                    .formatted(INTERESTS_PER_USER, INTEREST_POOL.size()));
        }

        List<String> unknown = INTEREST_POOL.stream()
                .filter(interest -> !interestLoader.getAllInterests().contains(interest))
                .toList();

        if (!unknown.isEmpty()) {
            throw new IllegalStateException("관심사 목록에 없는 시드 관심사가 있습니다: " + unknown);
        }
    }

    private User toUser(SeedUser seed, int index) {
        String nickname = seed.givenName() + sequence(index);
        String organization = seed.organization().organizationName;
        String affiliationNumber = affiliationNumber(index);
        String birthDate = birthDate(index);
        String phoneNumber = PHONE_PREFIX + (PHONE_START + index);
        String email = email(seed, index);

        return User.create(
                nickname,
                seed.familyName(), blindIndexHasher.hash(seed.familyName()),
                seed.givenName(), blindIndexHasher.hash(seed.givenName()),
                seed.gender(),
                organization, blindIndexHasher.hash(organization),
                seed.affiliation(), blindIndexHasher.hash(seed.affiliation()),
                affiliationNumber, blindIndexHasher.hash(affiliationNumber),
                birthDate, blindIndexHasher.hash(birthDate),
                phoneNumber, blindIndexHasher.hash(phoneNumber),
                email, blindIndexHasher.hash(email),
                null, null,
                null, null
        );
    }

    private List<PersonaElement> toPersonaElements(Long userId, SeedUser seed, int index, Instant createdAt) {
        Random random = new Random(ANSWER_SEED + index);
        List<PersonaElement> elements = new ArrayList<>();

        for (SurveyQuestion question : surveyLoader.getAllQuestions()) {
            SurveyOptionName answer = random.nextBoolean() ? SurveyOptionName.A : SurveyOptionName.B;

            elements.add(PersonaElement.create(userId, question.dimension(), question.traitFor(answer), createdAt));
        }

        for (String interest : interests(random)) {
            elements.add(PersonaElement.create(userId, PersonaDimension.INTEREST, interest, createdAt));
        }

        for (String detail : seed.details()) {
            elements.add(PersonaElement.create(userId, PersonaDimension.DETAIL, detail, createdAt));
        }

        elements.add(summaryElement(userId, seed, createdAt));

        return List.copyOf(elements);
    }

    private PersonaElement summaryElement(Long userId, SeedUser seed, Instant createdAt) {
        return PersonaElement.create(userId, PersonaDimension.SUMMARY, seed.summary(), createdAt);
    }

    private List<String> interests(Random random) {
        List<String> shuffled = new ArrayList<>(INTEREST_POOL);
        Collections.shuffle(shuffled, random);

        return List.copyOf(shuffled.subList(0, INTERESTS_PER_USER));
    }

    private List<String> details(int index) {
        int from = index * DETAIL_ELEMENTS_PER_USER;

        return List.copyOf(PersonaSeedElements.DETAIL.subList(from, from + DETAIL_ELEMENTS_PER_USER));
    }

    private String email(SeedUser seed, int index) {
        return EMAIL_LOCAL_PREFIX + sequence(index) + "@" + seed.organization().domain;
    }

    private String affiliationNumber(int index) {
        return "%d%04d".formatted(2021 + (index % 4), index + 1);
    }

    private String birthDate(int index) {
        return "%d-%02d-%02d".formatted(2000 + (index % 6), 1 + (index % 12), 1 + (index % 28));
    }

    private String sequence(int index) {
        return "%02d".formatted(index + 1);
    }
}
