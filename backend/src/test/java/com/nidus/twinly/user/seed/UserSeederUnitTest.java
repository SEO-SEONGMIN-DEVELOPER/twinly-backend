package com.nidus.twinly.user.seed;

import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.interest.InterestLoader;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.simulation.service.SimulationService;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSeederUnitTest {

    private static final int SEED_USER_COUNT = 20;
    private static final int INTERESTS_PER_USER = 5;
    private static final int DETAIL_ELEMENTS_PER_USER = 5;
    private static final int SUMMARY_ELEMENTS_PER_USER = 1;
    private static final int SCENARIO_DAY_COUNT = 390;
    private static final long EXPIRED_USER_ID = 6L;

    @Mock
    UserRepository userRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    BlindIndexHasher blindIndexHasher;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @Mock
    UserEntitlementRepository userEntitlementRepository;

    @Mock
    SceneRepository sceneRepository;

    @Mock
    SimulationService simulationService;

    UserSeeder userSeeder;

    SurveyLoader surveyLoader;
    InterestLoader interestLoader;

    AtomicLong sequence;

    @BeforeEach
    void setUp() throws IOException {
        sequence = new AtomicLong();

        surveyLoader = new SurveyLoader();
        ReflectionTestUtils.setField(surveyLoader, "objectMapper", new ObjectMapper());
        surveyLoader.load();

        interestLoader = new InterestLoader(new ObjectMapper());
        interestLoader.load();

        Season season = Season.create(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        ReflectionTestUtils.setField(season, "id", 7L);
        given(currentSeasonReader.read()).willReturn(season);

        // 시나리오 시딩은 유저 시딩과 별개라 이 테스트에서는 이미 채워진 것으로 두고 건너뛴다
        given(sceneRepository.existsByUserIdAndDate(any(), any())).willReturn(true);

        userSeeder = new UserSeeder(userRepository, personaElementRepository, blindIndexHasher, surveyLoader,
                interestLoader, currentSeasonReader, seasonParticipationRepository, userEntitlementRepository,
                sceneRepository, simulationService, new ObjectMapper());

        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", sequence.incrementAndGet());

            return user;
        });
    }

    @Test
    @DisplayName("시드된 적이 없으면 유저 20명과 설문 문항 수만큼의 성향을 저장한다")
    void run_seeds_users_and_elements() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 유저 20명 저장 + 유저별로 설문 23문항 + 관심사 5개 + 대화 5개 + 요약 1개
        then(userRepository).should(org.mockito.Mockito.times(SEED_USER_COUNT)).save(any(User.class));

        List<PersonaElement> elements = savedElements();
        assertThat(elements).hasSize(SEED_USER_COUNT * (elementsPerUser()));

        Map<Long, Map<PersonaDimension, Long>> countByUserAndDimension = elements.stream()
                .collect(Collectors.groupingBy(PersonaElement::getUserId,
                        Collectors.groupingBy(PersonaElement::getDimension, Collectors.counting())));

        assertThat(countByUserAndDimension).hasSize(SEED_USER_COUNT);
        assertThat(countByUserAndDimension.values()).allSatisfy(byDimension -> {
            assertThat(byDimension.get(PersonaDimension.INTEREST)).isEqualTo(INTERESTS_PER_USER);
            assertThat(byDimension.get(PersonaDimension.DETAIL)).isEqualTo(DETAIL_ELEMENTS_PER_USER);
            assertThat(byDimension.get(PersonaDimension.SUMMARY)).isEqualTo(1L);

            surveyLoader.getAllQuestions().stream()
                    .collect(Collectors.groupingBy(SurveyQuestion::dimension, Collectors.counting()))
                    .forEach((dimension, questionCount) ->
                            assertThat(byDimension.get(dimension)).isEqualTo(questionCount));
        });
    }

    @Test
    @DisplayName("설문 차원의 문장은 모두 실제 설문 선택지에서 나온다")
    void run_uses_survey_traits() throws IOException {
        // given: 실제 설문의 모든 선택지 문장
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        Set<String> traits = surveyLoader.getAllQuestions().stream()
                .flatMap(question -> Stream.of(SurveyOptionName.values()).map(question::traitFor))
                .collect(Collectors.toSet());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 관심사·대화·요약을 뺀 나머지는 전부 설문 선택지 문장이다
        List<String> surveyExplanations = savedElements().stream()
                .filter(element -> element.getDimension() != PersonaDimension.INTEREST)
                .filter(element -> element.getDimension() != PersonaDimension.DETAIL)
                .filter(element -> element.getDimension() != PersonaDimension.SUMMARY)
                .map(PersonaElement::getExplanation)
                .toList();

        assertThat(surveyExplanations).isNotEmpty();
        assertThat(traits).containsAll(surveyExplanations);
    }

    @Test
    @DisplayName("관심사는 모두 관심사 목록 안에 있고 유저끼리 겹친다")
    void run_uses_interests_from_the_master_list() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 목록 밖 관심사가 없다
        Map<Long, Set<String>> interestsByUser = savedElements().stream()
                .filter(element -> element.getDimension() == PersonaDimension.INTEREST)
                .collect(Collectors.groupingBy(PersonaElement::getUserId,
                        Collectors.mapping(PersonaElement::getExplanation, Collectors.toSet())));

        assertThat(interestLoader.getAllInterests())
                .containsAll(interestsByUser.values().stream().flatMap(Set::stream).toList());

        // then: 유저마다 관심사가 중복 없이 5개고, 서로 겹치는 쌍이 존재한다
        assertThat(interestsByUser.values()).allSatisfy(interests ->
                assertThat(interests).hasSize(INTERESTS_PER_USER));

        List<Set<String>> interestSets = List.copyOf(interestsByUser.values());
        long sharingPairs = 0;
        for (int i = 0; i < interestSets.size(); i++) {
            for (int j = i + 1; j < interestSets.size(); j++) {
                if (interestSets.get(i).stream().anyMatch(interestSets.get(j)::contains)) {
                    sharingPairs++;
                }
            }
        }

        assertThat(sharingPairs).isPositive();
    }

    @Test
    @DisplayName("유저마다 설문 답이 모두 같지는 않다")
    void run_varies_answers_between_users() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 유저별 성향 문장 조합이 한 종류로 수렴하지 않는다
        Map<Long, Set<String>> traitsByUser = savedElements().stream()
                .filter(element -> element.getDimension() != PersonaDimension.INTEREST)
                .filter(element -> element.getDimension() != PersonaDimension.DETAIL)
                .filter(element -> element.getDimension() != PersonaDimension.SUMMARY)
                .collect(Collectors.groupingBy(PersonaElement::getUserId,
                        Collectors.mapping(PersonaElement::getExplanation, Collectors.toSet())));

        assertThat(Set.copyOf(traitsByUser.values())).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("전화번호는 01000009001부터, 이메일은 test-seed01부터 순서대로 배정한다")
    void run_assigns_phone_and_email_in_order() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 전화번호·이메일·닉네임이 순번 규칙대로 부여됨
        List<User> users = savedUsers();

        assertThat(users.getFirst().getPhoneNumber()).isEqualTo("01000009001");
        assertThat(users.getLast().getPhoneNumber()).isEqualTo("01000009020");
        assertThat(users.getFirst().getEmail()).isEqualTo("test-seed01@skku.edu");
        assertThat(users.getLast().getEmail()).isEqualTo("test-seed20@sungshin.ac.kr");

        assertThat(users).extracting(User::getPhoneNumber).doesNotHaveDuplicates();
        assertThat(users).extracting(User::getEmail).doesNotHaveDuplicates();
        assertThat(users).extracting(User::getNickname).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("성별은 남녀 10명씩이고 학교는 세 곳이 섞여 있다")
    void run_keeps_gender_and_organization_mix() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 성비 1:1 + 학교 3종
        List<User> users = savedUsers();

        Map<Gender, Long> countByGender = users.stream()
                .collect(Collectors.groupingBy(User::getGender, Collectors.counting()));
        assertThat(countByGender).containsOnly(
                Map.entry(Gender.MALE, 10L),
                Map.entry(Gender.FEMALE, 10L));

        assertThat(users).extracting(User::getOrganization)
                .containsOnly("성균관대학교", "고려대학교", "성신여자대학교");
    }

    @Test
    @DisplayName("유저와 페르소나가 모두 있으면 아무것도 저장하지 않는다")
    void run_is_idempotent() throws IOException {
        // given: 시드 유저도 페르소나도 이미 존재하는 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 아무것도 저장하지 않음
        then(userRepository).should(never()).save(any(User.class));
        then(personaElementRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("페르소나는 있지만 SUMMARY만 없는 기존 시드 유저에게는 유저별 요약 한 건만 채운다")
    void run_backfills_summary_only() throws IOException {
        // given: 요약 시드가 생기기 전에 시드된 유저라 다른 요소는 있고 SUMMARY 만 없는 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(false);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 유저·다른 요소는 건드리지 않고 유저마다 SUMMARY 한 건씩만 저장하며, 문장은 유저 순번에 맞는다
        then(userRepository).should(never()).save(any(User.class));

        List<PersonaElement> elements = savedElements();
        assertThat(elements).hasSize(SEED_USER_COUNT);
        assertThat(elements).extracting(PersonaElement::getDimension).containsOnly(PersonaDimension.SUMMARY);
        assertThat(elements).extracting(PersonaElement::getUserId).doesNotHaveDuplicates();
        assertThat(elements.getFirst().getExplanation()).isEqualTo(PersonaSeedElements.SUMMARY.getFirst());
        assertThat(elements.getLast().getExplanation()).isEqualTo(PersonaSeedElements.SUMMARY.getLast());
    }

    @Test
    @DisplayName("요약 문장은 유저마다 다르고 모두 \"사람\"으로 끝난다")
    void run_assigns_distinct_summary_per_user() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 프로필 한 줄 소개로 쓰이므로 중복이 없고 "~한 사람" 형식을 지킨다
        List<String> summaries = savedElements().stream()
                .filter(element -> element.getDimension() == PersonaDimension.SUMMARY)
                .map(PersonaElement::getExplanation)
                .toList();

        assertThat(summaries).hasSize(SEED_USER_COUNT).doesNotHaveDuplicates();
        assertThat(summaries).allSatisfy(summary -> assertThat(summary).endsWith("사람").hasSizeLessThanOrEqualTo(60));
    }

    @Test
    @DisplayName("유저는 남아 있는데 페르소나만 비어 있으면 페르소나만 채운다")
    void run_backfills_elements_only() throws IOException {
        // given: 시드 유저는 존재하지만 페르소나가 전부 사라진 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(false);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 유저는 새로 만들지 않고 페르소나만 채움
        then(userRepository).should(never()).save(any(User.class));

        List<PersonaElement> elements = savedElements();
        assertThat(elements).hasSize(SEED_USER_COUNT * elementsPerUser());

        Set<Long> userIds = elements.stream()
                .map(PersonaElement::getUserId)
                .collect(Collectors.toSet());
        assertThat(userIds).hasSize(SEED_USER_COUNT);
    }

    private int elementsPerUser() {
        return surveyLoader.getAllQuestions().size() + INTERESTS_PER_USER + DETAIL_ELEMENTS_PER_USER + SUMMARY_ELEMENTS_PER_USER;
    }

    private User existingUser() {
        User user = User.create(
                "기존유저", "김", "hash", "도윤", "hash", Gender.MALE,
                "성균관대학교", "hash", "미디어커뮤니케이션학과", "hash",
                "20210001", "hash", "2000-01-01", "hash",
                "01000009001", "hash", "test-seed01@skku.edu", "hash", null, null);
        ReflectionTestUtils.setField(user, "id", sequence.incrementAndGet());

        return user;
    }

    private List<User> savedUsers() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should(org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        return captor.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private List<PersonaElement> savedElements() {
        ArgumentCaptor<List<PersonaElement>> captor = ArgumentCaptor.forClass(List.class);
        then(personaElementRepository).should().saveAll(captor.capture());

        return captor.getValue();
    }

    @Test
    @DisplayName("시드 유저 전원을 현재 시즌에 참가시킨다")
    void run_joins_every_seed_user_to_current_season() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 20명 모두 현재 시즌으로 upsert 된다
        for (long userId = 1; userId <= SEED_USER_COUNT; userId++) {
            then(seasonParticipationRepository).should().upsert(userId, 7L);
        }
    }

    @Test
    @DisplayName("시드 유저 전원에게 만료 없는 시뮬레이션 이용 권한을 부여한다")
    void run_grants_simulation_access_to_every_seed_user() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willReturn(List.of());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 20명 모두 만료 없는 simulation_access 를 받는다
        List<UserEntitlement> granted = savedEntitlements();

        assertThat(granted).hasSize(SEED_USER_COUNT);
        assertThat(granted).allSatisfy(entitlement -> {
            assertThat(entitlement.getEntitlement()).isEqualTo(EntitlementReader.SIMULATION_ACCESS);
            assertThat(entitlement.getExpiresAt()).isNull();
        });
        assertThat(granted.stream().map(UserEntitlement::getUserId).toList())
                .containsExactlyInAnyOrderElementsOf(
                        LongStream.rangeClosed(1, SEED_USER_COUNT).boxed().toList());
    }

    @Test
    @DisplayName("이미 권한이 있는 시드 유저에게는 다시 부여하지 않는다")
    void run_skips_simulation_access_when_already_granted() throws IOException {
        // given: 유저는 남아 있고 전원이 이미 권한을 가진 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willAnswer(invocation -> seedEntitlements(null));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 엔타이틀먼트 저장은 한 번도 일어나지 않는다
        then(userEntitlementRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("만료된 권한을 가진 시드 유저는 만료를 풀어 다시 이용할 수 있게 한다")
    void run_clears_expiry_when_simulation_access_expired() throws IOException {
        // given: 유저는 남아 있고 6번만 이미 만료된 권한을 가진 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willAnswer(invocation -> seedEntitlements(EXPIRED_USER_ID));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 만료됐던 한 명만 만료 없는 상태로 갱신된다
        List<UserEntitlement> granted = savedEntitlements();

        assertThat(granted).hasSize(1);
        assertThat(granted.getFirst().getUserId()).isEqualTo(EXPIRED_USER_ID);
        assertThat(granted.getFirst().getExpiresAt()).isNull();
    }

    private List<UserEntitlement> seedEntitlements(Long expiredUserId) {
        return LongStream.rangeClosed(1, SEED_USER_COUNT)
                .mapToObj(userId -> UserEntitlement.create(
                        userId,
                        EntitlementReader.SIMULATION_ACCESS,
                        Long.valueOf(userId).equals(expiredUserId) ? Instant.now().minusSeconds(60) : null,
                        Instant.now()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<UserEntitlement> savedEntitlements() {
        ArgumentCaptor<List<UserEntitlement>> captor = ArgumentCaptor.forClass(List.class);
        then(userEntitlementRepository).should().saveAll(captor.capture());

        return captor.getValue();
    }

    @Test
    @DisplayName("채워진 하루가 없으면 시드 시나리오를 하루씩 모두 적재한다")
    void run_seeds_scenarios_when_absent() throws IOException {
        // given: 아직 씬이 하나도 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(sceneRepository.existsByUserIdAndDate(any(), any())).willReturn(false);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 리소스에 담긴 하루 수만큼 시뮬레이션이 저장된다
        then(simulationService).should(times(SCENARIO_DAY_COUNT)).simulations(any(), any());
    }

    @Test
    @DisplayName("이미 채워진 하루는 다시 적재하지 않는다")
    void run_skips_scenarios_when_already_seeded() throws IOException {
        // given: 모든 하루가 이미 채워진 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(sceneRepository.existsByUserIdAndDate(any(), any())).willReturn(true);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 시뮬레이션 저장은 한 번도 일어나지 않는다
        then(simulationService).should(never()).simulations(any(), any());
    }

    @Test
    @DisplayName("일부만 채워져 있으면 빠진 하루만 이어서 적재한다")
    void run_resumes_only_missing_days() throws IOException {
        // given: 절반은 이미 채워지고 절반은 비어 있는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        AtomicLong calls = new AtomicLong();
        given(sceneRepository.existsByUserIdAndDate(any(), any()))
                .willAnswer(invocation -> calls.incrementAndGet() % 2 == 0);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 비어 있던 하루 수만큼만 적재된다
        then(simulationService).should(times(SCENARIO_DAY_COUNT / 2)).simulations(any(), any());
    }
}
