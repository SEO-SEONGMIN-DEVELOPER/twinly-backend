package com.nidus.twinly.user.seed;

import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.interest.InterestLoader;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonaSeederUnitTest {

    private static final int SEED_USER_COUNT = 20;
    private static final int INTERESTS_PER_USER = 5;
    private static final int DETAIL_ELEMENTS_PER_USER = 5;

    @Mock
    UserRepository userRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    BlindIndexHasher blindIndexHasher;

    PersonaSeeder personaSeeder;

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

        personaSeeder = new PersonaSeeder(userRepository, personaElementRepository, blindIndexHasher, surveyLoader, interestLoader);

        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", sequence.incrementAndGet());

            return user;
        });
    }

    @Test
    @DisplayName("시드된 적이 없으면 유저 20명과 설문 문항 수만큼의 성향을 저장한다")
    void run_seeds_users_and_elements() {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        personaSeeder.run(null);

        // then: 유저 20명 저장 + 유저별로 설문 23문항 + 관심사 5개 + 대화 5개
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

            surveyLoader.getAllQuestions().stream()
                    .collect(Collectors.groupingBy(SurveyQuestion::dimension, Collectors.counting()))
                    .forEach((dimension, questionCount) ->
                            assertThat(byDimension.get(dimension)).isEqualTo(questionCount));
        });
    }

    @Test
    @DisplayName("설문 차원의 문장은 모두 실제 설문 선택지에서 나온다")
    void run_uses_survey_traits() {
        // given: 실제 설문의 모든 선택지 문장
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        Set<String> traits = surveyLoader.getAllQuestions().stream()
                .flatMap(question -> Stream.of(SurveyOptionName.values()).map(question::traitFor))
                .collect(Collectors.toSet());

        // when: 시더 실행
        personaSeeder.run(null);

        // then: 관심사와 대화를 뺀 나머지는 전부 설문 선택지 문장이다
        List<String> surveyExplanations = savedElements().stream()
                .filter(element -> element.getDimension() != PersonaDimension.INTEREST)
                .filter(element -> element.getDimension() != PersonaDimension.DETAIL)
                .map(PersonaElement::getExplanation)
                .toList();

        assertThat(surveyExplanations).isNotEmpty();
        assertThat(traits).containsAll(surveyExplanations);
    }

    @Test
    @DisplayName("관심사는 모두 관심사 목록 안에 있고 유저끼리 겹친다")
    void run_uses_interests_from_the_master_list() {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        personaSeeder.run(null);

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
    void run_varies_answers_between_users() {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        personaSeeder.run(null);

        // then: 유저별 성향 문장 조합이 한 종류로 수렴하지 않는다
        Map<Long, Set<String>> traitsByUser = savedElements().stream()
                .filter(element -> element.getDimension() != PersonaDimension.INTEREST)
                .filter(element -> element.getDimension() != PersonaDimension.DETAIL)
                .collect(Collectors.groupingBy(PersonaElement::getUserId,
                        Collectors.mapping(PersonaElement::getExplanation, Collectors.toSet())));

        assertThat(Set.copyOf(traitsByUser.values())).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("전화번호는 01000009001부터, 이메일은 test-seed01부터 순서대로 배정한다")
    void run_assigns_phone_and_email_in_order() {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        personaSeeder.run(null);

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
    void run_keeps_gender_and_school_mix() {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.findByEmailHash(any())).willReturn(Optional.empty());

        // when: 시더 실행
        personaSeeder.run(null);

        // then: 성비 1:1 + 학교 3종
        List<User> users = savedUsers();

        Map<Gender, Long> countByGender = users.stream()
                .collect(Collectors.groupingBy(User::getGender, Collectors.counting()));
        assertThat(countByGender).containsOnly(
                Map.entry(Gender.MALE, 10L),
                Map.entry(Gender.FEMALE, 10L));

        assertThat(users).extracting(User::getSchool)
                .containsOnly("성균관대학교", "고려대학교", "성신여자대학교");
    }

    @Test
    @DisplayName("유저와 페르소나가 모두 있으면 아무것도 저장하지 않는다")
    void run_is_idempotent() {
        // given: 시드 유저도 페르소나도 이미 존재하는 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(true);

        // when: 시더 실행
        personaSeeder.run(null);

        // then: 아무것도 저장하지 않음
        then(userRepository).should(never()).save(any(User.class));
        then(personaElementRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("유저는 남아 있는데 페르소나만 비어 있으면 페르소나만 채운다")
    void run_backfills_elements_only() {
        // given: 시드 유저는 존재하지만 페르소나가 전부 사라진 상태
        given(userRepository.findByEmailHash(any())).willAnswer(invocation -> Optional.of(existingUser()));
        given(personaElementRepository.existsByUserId(any())).willReturn(false);

        // when: 시더 실행
        personaSeeder.run(null);

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
        return surveyLoader.getAllQuestions().size() + INTERESTS_PER_USER + DETAIL_ELEMENTS_PER_USER;
    }

    private User existingUser() {
        User user = User.create(
                "기존유저", "김", "hash", "도윤", "hash", Gender.MALE,
                "성균관대학교", "hash", "미디어커뮤니케이션학과", "hash",
                "20210001", "hash", "2000-01-01", "hash",
                "01000009001", "hash", "test-seed01@skku.edu", "hash");
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
}
