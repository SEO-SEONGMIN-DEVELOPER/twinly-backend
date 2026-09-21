package com.nidus.twinly.user.seed;

import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.SceneRepository.SceneDayProjection;
import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.interest.InterestLoader;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.repository.AgreementRepository;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.purchase.writer.PurchaseWriter;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.simulation.dto.command.SimulationsCommand;
import com.nidus.twinly.simulation.service.SimulationService;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.UserSurveyAnswer;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import com.nidus.twinly.user.seed.entity.SeedResourceHash;
import com.nidus.twinly.user.seed.repository.SeedResourceHashRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSeederUnitTest {

    private static final int SEED_USER_COUNT = 520;
    private static final int SHOWCASE_USER_COUNT = 20;
    private static final int INTERESTS_PER_USER = 5;
    private static final int DETAIL_ELEMENTS_PER_USER = 5;
    private static final int PERSONA_DETAILS_PER_USER = 8;
    private static final int SIMULATION_ACCESS_USER_COUNT = 50;
    private static final int SUMMARY_ELEMENTS_PER_USER = 1;
    private static final int SCENARIO_DAY_COUNT = scenarioDayCount(null);
    private static final int FIRST_USER_SCENARIO_DAY_COUNT = scenarioDayCount("1");
    private static final long EXPIRED_USER_ID = 26L;

    /** 시드 파일이 늘어나도 테스트가 따라오도록 개수는 리소스에서 읽는다. userRef 가 null 이면 전체. */
    private static int scenarioDayCount(String userRef) {
        try (InputStream in = new ClassPathResource("seed/showcase-scenarios.json").getInputStream()) {
            JsonNode days = new ObjectMapper().readTree(in).get("days");
            int count = 0;
            for (JsonNode day : days) {
                if (userRef == null || userRef.equals(day.get("userId").asString())) {
                    count++;
                }
            }
            return count;
        } catch (IOException e) {
            throw new IllegalStateException("시드 시나리오 리소스를 읽지 못했습니다.", e);
        }
    }

    @Mock
    UserRepository userRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    UserSurveyAnswerRepository userSurveyAnswerRepository;

    @Mock
    AiChatRepository aiChatRepository;

    @Mock
    BlindIndexHasher blindIndexHasher;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @Mock
    UserEntitlementRepository userEntitlementRepository;

    @Mock
    PolicyCatalog policyCatalog;

    @Mock
    AgreementRepository agreementRepository;

    @Mock
    PurchaseWriter purchaseWriter;

    @Mock
    SimulationService simulationService;

    @Mock
    SceneRepository sceneRepository;

    @Mock
    ScenarioCleaner scenarioCleaner;

    @Mock
    SeedResourceHashRepository seedResourceHashRepository;

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

        userSeeder = seederWith(new SeedProperties(true, SIMULATION_ACCESS_USER_COUNT));

        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", sequence.incrementAndGet());

            return user;
        });
    }

    @Test
    @DisplayName("시드된 적이 없으면 유저 520명과 설문 문항 수만큼의 성향을 저장한다")
    void run_seeds_users_and_elements() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 유저 520명 저장 + 유저별로 설문 23문항 + 관심사 5개 + 대화(기존 40명 5개, 나머지 8개) + 요약 1개
        then(userRepository).should(org.mockito.Mockito.times(SEED_USER_COUNT)).save(any(User.class));

        List<PersonaElement> elements = savedElements();
        assertThat(elements).hasSize(totalElements());

        Map<Long, Map<PersonaDimension, Long>> countByUserAndDimension = elements.stream()
                .collect(Collectors.groupingBy(PersonaElement::getUserId,
                        Collectors.groupingBy(PersonaElement::getDimension, Collectors.counting())));

        assertThat(countByUserAndDimension).hasSize(SEED_USER_COUNT);
        assertThat(countByUserAndDimension).allSatisfy((userId, byDimension) -> {
            assertThat(byDimension.get(PersonaDimension.INTEREST)).isEqualTo(INTERESTS_PER_USER);
            assertThat(byDimension.get(PersonaDimension.DETAIL)).isEqualTo(detailsFor(userId));
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
        assertThat(users.getLast().getPhoneNumber()).isEqualTo("01000009520");
        assertThat(users.getFirst().getEmail()).isEqualTo("test-seed01@skku.edu");
        assertThat(users.getLast().getEmail()).isEqualTo("test-seed520@sungshin.ac.kr");

        assertThat(users).extracting(User::getPhoneNumber).doesNotHaveDuplicates();
        assertThat(users).extracting(User::getEmail).doesNotHaveDuplicates();
        assertThat(users).extracting(User::getNickname).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("성별은 남녀 260명씩이고 학교는 세 곳이 섞여 있다")
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
                Map.entry(Gender.MALE, 260L),
                Map.entry(Gender.FEMALE, 260L));

        assertThat(users).extracting(User::getOrganization)
                .containsOnly("성균관대학교", "고려대학교", "성신여자대학교");
    }

    @Test
    @DisplayName("유저·페르소나·설문 답·AI 대화·대화 종료 시각이 모두 있으면 아무것도 저장하지 않는다")
    void run_is_idempotent() throws IOException {
        // given: 시드 유저·페르소나·설문 답·AI 대화가 이미 있고 대화 종료 시각도 기록된 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> {
            User user = existingUser();
            ReflectionTestUtils.setField(user, "aiChatCompletedAt", Instant.now());
            return Optional.of(user);
        });
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);
        given(userSurveyAnswerRepository.existsByUserId(any())).willReturn(true);
        given(aiChatRepository.existsByUserId(any())).willReturn(true);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 아무것도 저장하지 않음
        then(userRepository).should(never()).save(any(User.class));
        then(personaElementRepository).should(never()).saveAll(any());
        then(userSurveyAnswerRepository).should(never()).saveAll(any());
        then(aiChatRepository).should(never()).saveAll(any());
        then(userRepository).should(never()).markAiChatCompleted(any(), any());
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
        assertThat(elements.get(SHOWCASE_USER_COUNT - 1).getExplanation())
                .isEqualTo(PersonaSeedElements.SUMMARY.get(SHOWCASE_USER_COUNT - 1));
        assertThat(elements.get(SHOWCASE_USER_COUNT).getExplanation()).isEqualTo(personaSummaries().getFirst());
        assertThat(elements.getLast().getExplanation()).isEqualTo(personaSummaries().getLast());
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
        assertThat(elements).hasSize(totalElements());

        Set<Long> userIds = elements.stream()
                .map(PersonaElement::getUserId)
                .collect(Collectors.toSet());
        assertThat(userIds).hasSize(SEED_USER_COUNT);
    }

    @Test
    @DisplayName("시드 유저마다 설문 전 문항의 답을 저장하고, 그 답을 변환하면 저장된 설문 차원 페르소나와 정확히 같다")
    void run_seeds_survey_answers_consistent_with_persona() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 유저마다 설문 문항 수만큼 답이 저장됨
        List<SurveyQuestion> questions = surveyLoader.getAllQuestions();
        Map<Long, List<UserSurveyAnswer>> answersByUser = savedSurveyAnswers().stream()
                .collect(Collectors.groupingBy(UserSurveyAnswer::getUserId));
        assertThat(answersByUser).hasSize(SEED_USER_COUNT);

        // then: 답을 문항 순서대로 특성 문장으로 바꾸면, 같은 유저의 설문 차원 페르소나와 순서까지 일치
        Set<PersonaDimension> surveyDimensions = questions.stream().map(SurveyQuestion::dimension).collect(Collectors.toSet());
        Map<Long, List<String>> surveyTraitsByUser = savedElements().stream()
                .filter(element -> surveyDimensions.contains(element.getDimension()))
                .collect(Collectors.groupingBy(PersonaElement::getUserId,
                        Collectors.mapping(PersonaElement::getExplanation, Collectors.toList())));

        assertThat(answersByUser).allSatisfy((userId, answers) -> {
            assertThat(answers).extracting(UserSurveyAnswer::getQuestionId)
                    .containsExactlyElementsOf(questions.stream().map(SurveyQuestion::id).toList());
            List<String> traitsFromAnswers = answers.stream()
                    .map(answer -> surveyLoader.getQuestion(answer.getQuestionId()).traitFor(answer.getOptionName()))
                    .toList();
            assertThat(traitsFromAnswers).isEqualTo(surveyTraitsByUser.get(userId));
        });
    }

    @Test
    @DisplayName("시드 유저마다 대화 요소를 \"질문: 답\"으로 나눠 턴 0부터 AI 질문·유저 답을 한 쌍씩 저장한다")
    void run_seeds_ai_chats_from_details() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 유저마다 대화 요소 수 × 2(AI·USER)만큼 저장됨
        Map<Long, List<AiChat>> chatsByUser = savedAiChats().stream()
                .collect(Collectors.groupingBy(AiChat::getUserId));
        assertThat(chatsByUser).hasSize(SEED_USER_COUNT);
        assertThat(chatsByUser).allSatisfy((userId, chats) -> assertThat(chats).hasSize((int) detailsFor(userId) * 2));

        // then: 첫 쇼케이스 유저의 대화는 첫 대화 요소를 나눈 AI 질문·유저 답으로 시작
        String firstDetail = PersonaSeedElements.DETAIL.getFirst();
        int separator = firstDetail.indexOf(": ");
        assertThat(chatsByUser.get(1L).subList(0, 2))
                .extracting(AiChat::getSender, AiChat::getTurnIndex, AiChat::getMessage)
                .containsExactly(
                        tuple(AiChatSender.AI, 0, firstDetail.substring(0, separator)),
                        tuple(AiChatSender.USER, 0, firstDetail.substring(separator + 2)));
        assertThat(chatsByUser.get(1L).getLast().getTurnIndex()).isEqualTo(DETAIL_ELEMENTS_PER_USER - 1);
    }

    @Test
    @DisplayName("대화 종료 시각이 없는 시드 유저마다 대화 종료 시각을 기록한다")
    void run_marks_ai_chat_completed() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 유저마다 한 번씩 대화 종료 시각 기록을 요청
        then(userRepository).should(org.mockito.Mockito.times(SEED_USER_COUNT)).markAiChatCompleted(any(), any());
    }

    @Test
    @DisplayName("페르소나는 있지만 설문 답·AI 대화가 없는 기존 시드 유저에게는 설문 답·AI 대화만 채우고 페르소나는 건드리지 않는다")
    void run_backfills_survey_answers_and_ai_chats_for_existing_users() throws IOException {
        // given: 이번 기능 전에 시드된 유저라 페르소나는 모두 있고 설문 답·AI 대화만 없는 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 페르소나는 저장하지 않고, 유저마다 설문 답·AI 대화를 채우고 대화 종료 시각을 기록
        then(personaElementRepository).should(never()).saveAll(any());
        assertThat(savedSurveyAnswers()).hasSize(SEED_USER_COUNT * surveyLoader.getAllQuestions().size());
        assertThat(savedAiChats()).extracting(AiChat::getUserId).doesNotContainNull();
        then(userRepository).should(org.mockito.Mockito.times(SEED_USER_COUNT)).markAiChatCompleted(any(), any());
    }

    @SuppressWarnings("unchecked")
    private List<UserSurveyAnswer> savedSurveyAnswers() {
        ArgumentCaptor<List<UserSurveyAnswer>> captor = ArgumentCaptor.forClass(List.class);
        then(userSurveyAnswerRepository).should(org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());

        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    @SuppressWarnings("unchecked")
    private List<AiChat> savedAiChats() {
        ArgumentCaptor<List<AiChat>> captor = ArgumentCaptor.forClass(List.class);
        then(aiChatRepository).should(org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());

        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    private int totalElements() {
        int common = surveyLoader.getAllQuestions().size() + INTERESTS_PER_USER + SUMMARY_ELEMENTS_PER_USER;

        return SEED_USER_COUNT * common
                + SHOWCASE_USER_COUNT * DETAIL_ELEMENTS_PER_USER
                + (SEED_USER_COUNT - SHOWCASE_USER_COUNT) * PERSONA_DETAILS_PER_USER;
    }

    private long detailsFor(Long userId) {
        return userId <= SHOWCASE_USER_COUNT ? DETAIL_ELEMENTS_PER_USER : PERSONA_DETAILS_PER_USER;
    }

    private List<String> personaSummaries() throws IOException {
        try (InputStream in = new ClassPathResource("seed/ai-test-personas.json").getInputStream()) {
            return new ObjectMapper().readTree(in).valueStream()
                    .map(node -> node.get("summary").asString())
                    .toList();
        }
    }

    private User existingUser() {
        User user = User.create(
                "기존유저", "김", "hash", "도윤", "hash", Gender.MALE,
                "성균관대학교", "hash", "미디어커뮤니케이션학과", "hash",
                "20210001", "hash", "2000-01-01", "hash",
                "01000009001", "hash", "test-seed01@skku.edu", "hash", null, null, null, null);
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

        // then: 40명 모두 현재 시즌으로 upsert 된다
        for (long userId = 1; userId <= SEED_USER_COUNT; userId++) {
            then(seasonParticipationRepository).should().upsert(userId, 7L);
        }
    }

    @Test
    @DisplayName("AI 테스트용 시드 유저 앞쪽 50명에게만 만료 없는 시뮬레이션 이용 권한을 부여한다")
    void run_grants_simulation_access_to_ai_test_users_only() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willReturn(List.of());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 쇼케이스 20명 바로 뒤 50명만 만료 없는 simulation_access 를 받는다
        List<UserEntitlement> granted = savedEntitlements();

        assertThat(granted).hasSize(SIMULATION_ACCESS_USER_COUNT);
        assertThat(granted).allSatisfy(entitlement -> {
            assertThat(entitlement.getEntitlement()).isEqualTo(EntitlementReader.SIMULATION_ACCESS);
            assertThat(entitlement.getExpiresAt()).isNull();
            assertThat(entitlement.getSyncedAt()).isEqualTo(UserSeeder.SYNC_PROTECTED_SYNCED_AT);
        });
        assertThat(granted.stream().map(UserEntitlement::getUserId).toList())
                .containsExactlyInAnyOrderElementsOf(
                        LongStream.rangeClosed(SHOWCASE_USER_COUNT + 1L, SHOWCASE_USER_COUNT + SIMULATION_ACCESS_USER_COUNT)
                                .boxed().toList());
    }

    @Test
    @DisplayName("쇼케이스 유저와 권한 대상 밖 유저의 시뮬레이션 이용 권한은 지운다")
    void run_revokes_simulation_access_outside_grant_range() throws IOException {
        // given: 유저는 남아 있고 전원이 권한을 가진 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willAnswer(invocation -> entitlementsOf(invocation.getArgument(0), null));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 앞쪽 20명과 권한 대상 50명을 지난 나머지의 권한이 삭제된다
        ArgumentCaptor<List<UserEntitlement>> captor = ArgumentCaptor.forClass(List.class);
        then(userEntitlementRepository).should(times(2)).deleteAll(captor.capture());

        assertThat(captor.getAllValues().getFirst()).extracting(UserEntitlement::getUserId)
                .containsExactlyInAnyOrderElementsOf(
                        LongStream.rangeClosed(1, SHOWCASE_USER_COUNT).boxed().toList());
        assertThat(captor.getAllValues().getLast()).extracting(UserEntitlement::getUserId)
                .containsExactlyInAnyOrderElementsOf(
                        LongStream.rangeClosed(SHOWCASE_USER_COUNT + SIMULATION_ACCESS_USER_COUNT + 1L, SEED_USER_COUNT)
                                .boxed().toList());
    }

    @Test
    @DisplayName("시뮬레이션 권한을 받은 시드 유저는 평행우주 입장 필수 약관 최신 버전에 동의한 상태로 만든다")
    void run_agrees_parallel_entry_policies_for_ai_test_users() throws IOException {
        // given: 필수 약관 최신 버전이 2건이고, 첫 번째 권한 대상 유저만 그중 1건에 이미 동의한 상태
        long firstAccessUserId = SHOWCASE_USER_COUNT + 1L;
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willReturn(List.of());
        given(policyCatalog.loadRequiredPolicyIds(PolicyKind.PARALLEL_ENTRY)).willReturn(Set.of(7L, 8L));
        given(agreementRepository.findAllByUserIdInAndRevokedAtIsNull(any()))
                .willReturn(List.of(Agreement.create(firstAccessUserId, 7L, Instant.now())));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 권한 대상 50명 모두 두 약관에 동의가 채워지되, 이미 동의한 건은 다시 만들지 않는다
        ArgumentCaptor<List<Agreement>> captor = ArgumentCaptor.forClass(List.class);
        then(agreementRepository).should().saveAll(captor.capture());
        List<Agreement> saved = captor.getValue();

        assertThat(saved).hasSize(SIMULATION_ACCESS_USER_COUNT * 2 - 1);
        assertThat(saved).noneMatch(agreement -> agreement.getUserId() == firstAccessUserId && agreement.getPolicyId() == 7L);
        assertThat(saved.stream().map(Agreement::getUserId).distinct().toList())
                .containsExactlyInAnyOrderElementsOf(
                        LongStream.rangeClosed(firstAccessUserId, SHOWCASE_USER_COUNT + SIMULATION_ACCESS_USER_COUNT)
                                .boxed().toList());
    }

    @Test
    @DisplayName("시뮬레이션 권한과 관계없이 모든 시드 유저는 온보딩 필수 약관 최신 버전에 동의한 상태로 만든다")
    void run_agrees_onboarding_policies_for_every_seed_user() throws IOException {
        // given: 온보딩 필수 약관 최신 버전이 2건이고, 첫 번째 유저만 그중 1건에 이미 동의한 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willReturn(List.of());
        given(policyCatalog.loadRequiredPolicyIds(PolicyKind.ONBOARDING)).willReturn(Set.of(3L, 4L));
        given(agreementRepository.findAllByUserIdInAndRevokedAtIsNull(any()))
                .willReturn(List.of(Agreement.create(1L, 3L, Instant.now())));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 쇼케이스·권한 대상·권한 밖 유저 전원이 두 약관에 동의가 채워지되, 이미 동의한 건은 다시 만들지 않는다
        ArgumentCaptor<List<Agreement>> captor = ArgumentCaptor.forClass(List.class);
        then(agreementRepository).should().saveAll(captor.capture());
        List<Agreement> saved = captor.getValue();

        assertThat(saved).hasSize(SEED_USER_COUNT * 2 - 1);
        assertThat(saved).noneMatch(agreement -> agreement.getUserId() == 1L && agreement.getPolicyId() == 3L);
        assertThat(saved.stream().map(Agreement::getUserId).distinct().toList())
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, SEED_USER_COUNT).boxed().toList());
    }

    @Test
    @DisplayName("이미 권한이 있는 시드 유저에게는 다시 부여하지 않는다")
    void run_skips_simulation_access_when_already_granted() throws IOException {
        // given: 유저는 남아 있고 AI 테스트용 유저가 이미 권한을 가진 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willAnswer(invocation -> entitlementsOf(invocation.getArgument(0), null));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 엔타이틀먼트 저장은 한 번도 일어나지 않는다
        then(userEntitlementRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("만료된 권한을 가진 시드 유저는 만료를 풀어 다시 이용할 수 있게 한다")
    void run_clears_expiry_when_simulation_access_expired() throws IOException {
        // given: 유저는 남아 있고 26번만 이미 만료된 권한을 가진 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willAnswer(invocation -> entitlementsOf(invocation.getArgument(0), EXPIRED_USER_ID));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 만료됐던 한 명만 만료 없는 상태로 갱신된다
        List<UserEntitlement> granted = savedEntitlements();

        assertThat(granted).hasSize(1);
        assertThat(granted.getFirst().getUserId()).isEqualTo(EXPIRED_USER_ID);
        assertThat(granted.getFirst().getExpiresAt()).isNull();
        assertThat(granted.getFirst().getSyncedAt()).isEqualTo(UserSeeder.SYNC_PROTECTED_SYNCED_AT);
    }

    @Test
    @DisplayName("RevenueCat 동기화가 지울 수 있는 시각으로 부여됐던 시드 권한은 동기화가 덮어쓰지 못하는 시각으로 옮긴다")
    void run_moves_synced_at_out_of_revenue_cat_sync_reach() throws IOException {
        // given: 유저는 남아 있고 권한 대상 전원이 부팅 시각을 synced_at 으로 가진 권한을 가진 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);
        given(personaElementRepository.existsByUserIdAndDimension(any(), eq(PersonaDimension.SUMMARY))).willReturn(true);
        given(userEntitlementRepository.findAllByUserIdInAndEntitlement(any(), eq(EntitlementReader.SIMULATION_ACCESS)))
                .willAnswer(invocation -> ((List<Long>) invocation.getArgument(0)).stream()
                        .map(userId -> UserEntitlement.create(userId, EntitlementReader.SIMULATION_ACCESS, null, Instant.now()))
                        .toList());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 권한 대상 50명 전원의 synced_at 이 보호 시각으로 바뀐다
        List<UserEntitlement> granted = savedEntitlements();

        assertThat(granted).hasSize(SIMULATION_ACCESS_USER_COUNT);
        assertThat(granted).allSatisfy(entitlement -> {
            assertThat(entitlement.getExpiresAt()).isNull();
            assertThat(entitlement.getSyncedAt()).isEqualTo(UserSeeder.SYNC_PROTECTED_SYNCED_AT);
        });
    }

    private List<UserEntitlement> entitlementsOf(List<Long> userIds, Long expiredUserId) {
        return userIds.stream()
                .map(userId -> UserEntitlement.create(
                        userId,
                        EntitlementReader.SIMULATION_ACCESS,
                        userId.equals(expiredUserId) ? Instant.now().minusSeconds(60) : null,
                        UserSeeder.SYNC_PROTECTED_SYNCED_AT))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<UserEntitlement> savedEntitlements() {
        ArgumentCaptor<List<UserEntitlement>> captor = ArgumentCaptor.forClass(List.class);
        then(userEntitlementRepository).should().saveAll(captor.capture());

        return captor.getValue();
    }

    private UserSeeder seederWith(SeedProperties seedProperties) {
        return new UserSeeder(userRepository, personaElementRepository, userSurveyAnswerRepository, aiChatRepository, blindIndexHasher, surveyLoader,
                interestLoader, currentSeasonReader, seasonParticipationRepository, userEntitlementRepository,
                policyCatalog, agreementRepository, purchaseWriter, simulationService, sceneRepository, scenarioCleaner, seedResourceHashRepository, seedProperties, new ObjectMapper());
    }

    @Test
    @DisplayName("AI 테스트 유저 시드가 꺼져 있으면 쇼케이스 유저 20명만 만들고 시뮬레이션 권한은 건드리지 않는다")
    void run_seeds_only_showcase_users_when_ai_test_users_disabled() throws IOException {
        // given: 아직 시드 유저가 없고 AI 테스트 유저 시드가 꺼진 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        userSeeder = seederWith(new SeedProperties(false, SIMULATION_ACCESS_USER_COUNT));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 쇼케이스 유저만 저장되고, 권한 부여·풀 배정은 일어나지 않으며, 시나리오는 그대로 적재된다
        then(userRepository).should(times(SHOWCASE_USER_COUNT)).save(any(User.class));
        then(userEntitlementRepository).should(never()).saveAll(any());
        then(purchaseWriter).should(never()).assignPool(any());
        then(simulationService).should(times(SCENARIO_DAY_COUNT)).simulations(any(), any());
    }

    @Test
    @DisplayName("시나리오의 유저 번호는 실제 저장된 쇼케이스 유저 id 로 바꿔 적재한다")
    void run_maps_scenario_user_refs_to_seeded_user_ids() throws IOException {
        // given: 이미 다른 유저들이 있어 시드 유저 id 가 101 부터 시작하는 상태
        sequence.set(100);
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 1번 유저 시나리오는 id 101 로 저장되고, 등장하는 상대 id 도 전부 쇼케이스 유저 범위 안이다
        ArgumentCaptor<SimulationsCommand> captor = ArgumentCaptor.forClass(SimulationsCommand.class);
        then(simulationService).should(times(SCENARIO_DAY_COUNT)).simulations(any(), captor.capture());
        then(simulationService).should(times(FIRST_USER_SCENARIO_DAY_COUNT)).simulations(eq(101L), any());
        then(simulationService).should(never()).simulations(eq(1L), any());

        assertThat(captor.getAllValues()).allSatisfy(command -> {
            assertThat(command.userId()).isBetween(101L, 100L + SHOWCASE_USER_COUNT);
            assertThat(command.relationships()).allSatisfy(relationship ->
                    assertThat(relationship.partnerId()).isBetween(101L, 100L + SHOWCASE_USER_COUNT));
        });
    }

    @Test
    @DisplayName("시나리오에 쇼케이스 유저가 아닌 번호가 있으면 적재를 중단한다")
    void remapUserRefs_rejects_unknown_ref() {
        // given: 매핑 표에 없는 21번을 가리키는 장면
        ObjectMapper objectMapper = new ObjectMapper();
        var day = objectMapper.createObjectNode().put("userId", "21");

        // when & then: 조용히 넘기지 않고 예외로 알린다
        assertThatThrownBy(() -> UserSeeder.remapUserRefs(day, Map.of("1", "101")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("21");
    }

    @Test
    @DisplayName("시드 시나리오를 하루씩 모두 적재한다")
    void run_seeds_all_scenario_days() throws IOException {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 리소스에 담긴 하루 수만큼 시뮬레이션이 저장된다
        then(simulationService).should(times(SCENARIO_DAY_COUNT)).simulations(any(), any());
    }

    @Test
    @DisplayName("이미 시뮬레이션이 있는 날은 건너뛰고 없는 날만 적재한다")
    void run_seeds_only_missing_scenario_days() throws IOException {
        // given: 1번 유저의 날짜는 현재 기준일로 이미 적재된 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(sceneRepository.findAllDaysByUserIdIn(any())).willReturn(expectedDaysOf(1L));
        givenStoredHash(currentScenarioHash());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 지우지 않고, 1번 유저 날짜는 다시 넣지 않으며, 나머지 날만 저장한다
        then(scenarioCleaner).should(never()).clear(any());
        then(simulationService).should(never()).simulations(eq(1L), any());
        then(simulationService).should(times(SCENARIO_DAY_COUNT - FIRST_USER_SCENARIO_DAY_COUNT)).simulations(any(), any());
    }

    @Test
    @DisplayName("현재 기준일에 없는 날짜의 시나리오가 남아 있으면 쇼케이스 유저 시나리오를 지우고 전부 다시 적재한다")
    void run_reloads_all_scenario_days_when_stale_day_exists() throws IOException {
        // given: 시드 파일은 그대로인데, 1번 유저는 현재 날짜대로 있고 예전 기준일로 적재된 날짜 하나가 섞여 있는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        givenStoredHash(currentScenarioHash());
        List<SceneDayProjection> existing = new ArrayList<>(expectedDaysOf(1L));
        existing.add(day(1L, LocalDate.of(2000, 1, 1)));
        given(sceneRepository.findAllDaysByUserIdIn(any())).willReturn(existing);

        // when: 시더 실행
        userSeeder.run(null);

        // then: 쇼케이스 유저 20명의 시나리오를 지운 뒤 하루도 빠짐없이 다시 넣는다
        then(scenarioCleaner).should().clear(LongStream.rangeClosed(1, SHOWCASE_USER_COUNT).boxed().toList());
        then(simulationService).should(times(SCENARIO_DAY_COUNT)).simulations(any(), any());
    }

    @Test
    @DisplayName("모든 날짜가 현재 기준일대로 있으면 지우지도 넣지도 않는다")
    void run_leaves_scenarios_when_every_day_is_current() throws IOException {
        // given: 390일치가 전부 현재 기준일대로 적재됐고, 시드 파일도 마지막 적재 때와 같은 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(sceneRepository.findAllDaysByUserIdIn(any())).willReturn(expectedDays());
        givenStoredHash(currentScenarioHash());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 아무것도 지우지도 넣지도 않는다
        then(scenarioCleaner).should(never()).clear(any());
        then(simulationService).should(never()).simulations(any(), any());
    }

    @Test
    @DisplayName("날짜는 모두 그대로여도 시드 파일 내용이 마지막 적재 때와 다르면 지우고 전부 다시 적재한 뒤 새 해시를 저장한다")
    void run_reloads_all_scenario_days_when_resource_content_changed() throws IOException {
        // given: 390일치가 현재 기준일대로 있지만, 저장된 해시는 이전 시드 파일의 것
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(sceneRepository.findAllDaysByUserIdIn(any())).willReturn(expectedDays());
        SeedResourceHash stored = givenStoredHash("0".repeat(64));

        // when: 시더 실행
        userSeeder.run(null);

        // then: 쇼케이스 유저 시나리오를 지우고 하루도 빠짐없이 다시 넣은 뒤, 저장된 해시를 현재 파일 기준으로 바꾼다
        InOrder inOrder = inOrder(scenarioCleaner, simulationService, seedResourceHashRepository);
        inOrder.verify(scenarioCleaner).clear(LongStream.rangeClosed(1, SHOWCASE_USER_COUNT).boxed().toList());
        inOrder.verify(simulationService, times(SCENARIO_DAY_COUNT)).simulations(any(), any());
        inOrder.verify(seedResourceHashRepository).save(stored);
        assertThat(stored.getHash()).isEqualTo(currentScenarioHash());
    }

    @Test
    @DisplayName("저장된 해시가 없으면 전부 적재하고 현재 시드 파일의 해시를 새로 저장한다")
    void run_saves_scenario_hash_when_none_stored() throws IOException {
        // given: 해시를 저장한 적이 없는 상태 (해시 도입 전에 적재된 DB 포함)
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());
        given(sceneRepository.findAllDaysByUserIdIn(any())).willReturn(expectedDays());

        // when: 시더 실행
        userSeeder.run(null);

        // then: 기존 시나리오를 지우고 다시 적재한 뒤, 현재 파일의 해시를 시드 파일 이름으로 저장한다
        then(scenarioCleaner).should().clear(any());
        then(simulationService).should(times(SCENARIO_DAY_COUNT)).simulations(any(), any());
        ArgumentCaptor<SeedResourceHash> captor = ArgumentCaptor.forClass(SeedResourceHash.class);
        then(seedResourceHashRepository).should().save(captor.capture());
        assertThat(captor.getValue().getResource()).isEqualTo("seed/showcase-scenarios.json");
        assertThat(captor.getValue().getHash()).isEqualTo(currentScenarioHash()).hasSize(64);
    }

    @Test
    @DisplayName("시나리오 해시는 같은 내용이면 같고, 한 바이트만 달라도 달라진다")
    void scenarioHash_changes_with_content() {
        // given: 한 글자만 다른 두 내용
        byte[] original = "{\"anchorDate\":\"2026-09-09\"}".getBytes(StandardCharsets.UTF_8);
        byte[] edited = "{\"anchorDate\":\"2026-09-10\"}".getBytes(StandardCharsets.UTF_8);

        // when & then: 같은 내용은 같은 해시, 다른 내용은 다른 해시
        assertThat(UserSeeder.scenarioHash(original)).isEqualTo(UserSeeder.scenarioHash(original.clone()));
        assertThat(UserSeeder.scenarioHash(original)).isNotEqualTo(UserSeeder.scenarioHash(edited));
    }

    private SeedResourceHash givenStoredHash(String hash) {
        SeedResourceHash stored = SeedResourceHash.create("seed/showcase-scenarios.json", hash, Instant.now());
        given(seedResourceHashRepository.findByResource("seed/showcase-scenarios.json")).willReturn(Optional.of(stored));

        return stored;
    }

    private String currentScenarioHash() {
        try (InputStream in = new ClassPathResource("seed/showcase-scenarios.json").getInputStream()) {
            return UserSeeder.scenarioHash(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<SceneDayProjection> expectedDaysOf(Long userId) {
        return expectedDays().stream().filter(day -> day.getUserId().equals(userId)).toList();
    }

    private List<SceneDayProjection> expectedDays() {
        JsonNode root;
        try (InputStream in = new ClassPathResource("seed/showcase-scenarios.json").getInputStream()) {
            root = new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        long shift = ChronoUnit.DAYS.between(LocalDate.parse(root.get(UserSeeder.ANCHOR_DATE).asString()), UserSeeder.SCENARIO_BASE_DATE);
        List<SceneDayProjection> days = new ArrayList<>();
        root.get("days").forEach(day -> days.add(day(
                Long.parseLong(day.get("userId").asString()),
                LocalDate.parse(day.get("date").asString()).plusDays(shift))));

        return days;
    }

    private SceneDayProjection day(Long userId, LocalDate date) {
        return new SceneDayProjection() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public LocalDate getDate() {
                return date;
            }
        };
    }

}
