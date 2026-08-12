package com.nidus.twinly.user.seed;

import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Profile({"stage", "local"})
@RequiredArgsConstructor
public class PersonaSeeder implements ApplicationRunner {

    private static final int ELEMENTS_PER_DIMENSION = 5;
    private static final String PHONE_PREFIX = "0100000";
    private static final int PHONE_START = 9001;
    private static final String EMAIL_LOCAL_PREFIX = "test-seed";

    private final UserRepository userRepository;
    private final PersonaElementRepository personaElementRepository;
    private final BlindIndexHasher blindIndexHasher;

    private enum SeedSchool {
        SKKU("성균관대학교", "skku.edu"),
        KOREA("고려대학교", "korea.ac.kr"),
        SUNGSHIN("성신여자대학교", "sungshin.ac.kr");

        private final String schoolName;
        private final String domain;

        SeedSchool(String schoolName, String domain) {
            this.schoolName = schoolName;
            this.domain = domain;
        }
    }

    private record SeedUser(
            String familyName,
            String givenName,
            Gender gender,
            SeedSchool school,
            String affiliation
    ) {
    }

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("김", "도윤", Gender.MALE, SeedSchool.SKKU, "미디어커뮤니케이션학과"),
            new SeedUser("이", "시우", Gender.MALE, SeedSchool.SKKU, "경영학과"),
            new SeedUser("박", "하준", Gender.MALE, SeedSchool.SKKU, "심리학과"),
            new SeedUser("최", "은우", Gender.MALE, SeedSchool.SKKU, "통계학과"),
            new SeedUser("정", "지호", Gender.MALE, SeedSchool.SKKU, "사회학과"),
            new SeedUser("강", "서윤", Gender.FEMALE, SeedSchool.SKKU, "영어영문학과"),
            new SeedUser("조", "하윤", Gender.FEMALE, SeedSchool.SKKU, "경제학과"),
            new SeedUser("윤", "준서", Gender.MALE, SeedSchool.KOREA, "사회학과"),
            new SeedUser("장", "유찬", Gender.MALE, SeedSchool.KOREA, "철학과"),
            new SeedUser("임", "서준", Gender.MALE, SeedSchool.KOREA, "영어영문학과"),
            new SeedUser("한", "이든", Gender.MALE, SeedSchool.KOREA, "국어국문학과"),
            new SeedUser("오", "재이", Gender.MALE, SeedSchool.KOREA, "한국사학과"),
            new SeedUser("서", "지우", Gender.FEMALE, SeedSchool.KOREA, "사학과"),
            new SeedUser("신", "서연", Gender.FEMALE, SeedSchool.KOREA, "독어독문학과"),
            new SeedUser("권", "다인", Gender.FEMALE, SeedSchool.SUNGSHIN, "국어국문학과"),
            new SeedUser("황", "예린", Gender.FEMALE, SeedSchool.SUNGSHIN, "영어영문학과"),
            new SeedUser("안", "시아", Gender.FEMALE, SeedSchool.SUNGSHIN, "일본어문·문화학과"),
            new SeedUser("송", "유주", Gender.FEMALE, SeedSchool.SUNGSHIN, "문화예술경영학과"),
            new SeedUser("류", "채원", Gender.FEMALE, SeedSchool.SUNGSHIN, "중국어문·문화학과"),
            new SeedUser("전", "지아", Gender.FEMALE, SeedSchool.SUNGSHIN, "프랑스어문·문화학과")
    );


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailHash(blindIndexHasher.hash(email(SEED_USERS.getFirst(), 0)))) {
            return;
        }

        requireEnoughElements();

        Instant now = Instant.now();
        List<PersonaElement> elements = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();

        for (int index = 0; index < SEED_USERS.size(); index++) {
            User user = userRepository.save(toUser(SEED_USERS.get(index), index));

            userIds.add(user.getId());
            elements.addAll(toPersonaElements(user.getId(), index, now));
        }

        personaElementRepository.saveAll(elements);

        log.info("페르소나 시드 유저를 생성했습니다. userCount={}, elementCount={}, userIdFrom={}, userIdTo={}",
                userIds.size(), elements.size(), userIds.getFirst(), userIds.getLast());
    }

    private void requireEnoughElements() {
        int required = SEED_USERS.size() * ELEMENTS_PER_DIMENSION;

        for (PersonaDimension dimension : PersonaDimension.values()) {
            List<String> pool = PersonaSeedElements.BY_DIMENSION.get(dimension);

            if (pool == null || pool.size() < required) {
                throw new IllegalStateException("페르소나 시드 문장이 부족합니다. dimension=%s, required=%d, actual=%d"
                        .formatted(dimension, required, pool == null ? 0 : pool.size()));
            }
        }
    }

    private User toUser(SeedUser seed, int index) {
        String nickname = seed.givenName() + sequence(index);
        String school = seed.school().schoolName;
        String affiliationNumber = affiliationNumber(index);
        String birthDate = birthDate(index);
        String phoneNumber = PHONE_PREFIX + (PHONE_START + index);
        String email = email(seed, index);

        return User.create(
                nickname,
                seed.familyName(), blindIndexHasher.hash(seed.familyName()),
                seed.givenName(), blindIndexHasher.hash(seed.givenName()),
                seed.gender(),
                school, blindIndexHasher.hash(school),
                seed.affiliation(), blindIndexHasher.hash(seed.affiliation()),
                affiliationNumber, blindIndexHasher.hash(affiliationNumber),
                birthDate, blindIndexHasher.hash(birthDate),
                phoneNumber, blindIndexHasher.hash(phoneNumber),
                email, blindIndexHasher.hash(email)
        );
    }

    private List<PersonaElement> toPersonaElements(Long userId, int index, Instant createdAt) {
        return Arrays.stream(PersonaDimension.values())
                .flatMap(dimension -> explanations(dimension, index).stream()
                        .map(explanation -> PersonaElement.create(userId, dimension, explanation, createdAt)))
                .toList();
    }

    private List<String> explanations(PersonaDimension dimension, int index) {
        List<String> pool = PersonaSeedElements.BY_DIMENSION.get(dimension);
        int from = index * ELEMENTS_PER_DIMENSION;

        return List.copyOf(pool.subList(from, from + ELEMENTS_PER_DIMENSION));
    }

    private String email(SeedUser seed, int index) {
        return EMAIL_LOCAL_PREFIX + sequence(index) + "@" + seed.school().domain;
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
