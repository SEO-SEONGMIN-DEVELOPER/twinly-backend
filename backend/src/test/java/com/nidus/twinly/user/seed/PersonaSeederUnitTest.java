package com.nidus.twinly.user.seed;

import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonaSeederUnitTest {

    private static final int SEED_USER_COUNT = 20;
    private static final int ELEMENTS_PER_DIMENSION = 5;

    @Mock
    UserRepository userRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    BlindIndexHasher blindIndexHasher;

    @InjectMocks
    PersonaSeeder personaSeeder;

    @BeforeEach
    void setUp() {
        AtomicLong sequence = new AtomicLong();

        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", sequence.incrementAndGet());

            return user;
        });
    }

    @Test
    @DisplayName("시드된 적이 없으면 유저 20명과 차원당 5개씩의 페르소나를 저장한다")
    void run_seeds_users_and_elements() {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.existsByEmailHash(any())).willReturn(false);

        // when: 시더 실행
        personaSeeder.run(null);

        // then: 유저 20명 저장 + 유저별로 10개 차원에 5개씩 페르소나 저장
        then(userRepository).should(org.mockito.Mockito.times(SEED_USER_COUNT)).save(any(User.class));

        List<PersonaElement> elements = savedElements();
        assertThat(elements).hasSize(SEED_USER_COUNT * PersonaDimension.values().length * ELEMENTS_PER_DIMENSION);

        Map<Long, Map<PersonaDimension, Long>> countByUserAndDimension = elements.stream()
                .collect(Collectors.groupingBy(PersonaElement::getUserId,
                        Collectors.groupingBy(PersonaElement::getDimension, Collectors.counting())));

        assertThat(countByUserAndDimension).hasSize(SEED_USER_COUNT);
        assertThat(countByUserAndDimension.values()).allSatisfy(byDimension -> {
            assertThat(byDimension).hasSize(PersonaDimension.values().length);
            assertThat(byDimension.values()).allMatch(count -> count == ELEMENTS_PER_DIMENSION);
        });
    }

    @Test
    @DisplayName("유저끼리 같은 페르소나 문장을 공유하지 않는다")
    void run_gives_every_user_distinct_elements() {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.existsByEmailHash(any())).willReturn(false);

        // when: 시더 실행
        personaSeeder.run(null);

        // then: 저장된 모든 성향 문장이 서로 다름
        List<String> explanations = savedElements().stream()
                .map(PersonaElement::getExplanation)
                .toList();

        assertThat(explanations).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("전화번호는 01000009001부터, 이메일은 test-seed01부터 순서대로 배정한다")
    void run_assigns_phone_and_email_in_order() {
        // given: 아직 시드 유저가 없는 상태
        given(userRepository.existsByEmailHash(any())).willReturn(false);

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
        given(userRepository.existsByEmailHash(any())).willReturn(false);

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
    @DisplayName("이미 시드된 상태면 유저도 페르소나도 저장하지 않는다")
    void run_is_idempotent() {
        // given: 첫 시드 유저의 이메일이 이미 존재하는 상태
        given(userRepository.existsByEmailHash(any())).willReturn(true);

        // when: 시더 실행
        personaSeeder.run(null);

        // then: 아무것도 저장하지 않음
        then(userRepository).should(never()).save(any(User.class));
        then(personaElementRepository).should(never()).saveAll(any());
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
