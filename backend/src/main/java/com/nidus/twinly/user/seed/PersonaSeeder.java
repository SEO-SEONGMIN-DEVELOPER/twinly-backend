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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@Profile({"stage", "local"})
@RequiredArgsConstructor
public class PersonaSeeder implements ApplicationRunner {

    private static final int DETAIL_ELEMENTS_PER_USER = 5;
    private static final int INTERESTS_PER_USER = 5;
    private static final long ANSWER_SEED = 20260817L;
    private static final String PHONE_PREFIX = "0100000";
    private static final int PHONE_START = 9001;
    private static final String EMAIL_LOCAL_PREFIX = "test-seed";

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
        requireEnoughElements();

        Instant now = Instant.now();

        List<User> users = new ArrayList<>();
        for (int index = 0; index < SEED_USERS.size(); index++) {
            users.add(findOrCreateUser(SEED_USERS.get(index), index));
        }

        List<PersonaElement> elements = new ArrayList<>();
        for (int index = 0; index < users.size(); index++) {
            Long userId = users.get(index).getId();

            if (!personaElementRepository.existsByUserId(userId)) {
                elements.addAll(toPersonaElements(userId, index, now));
            }
        }

        if (elements.isEmpty()) {
            return;
        }

        personaElementRepository.saveAll(elements);

        log.info("페르소나 시드 요소를 채웠습니다. userCount={}, elementCount={}", users.size(), elements.size());
    }

    private User findOrCreateUser(SeedUser seed, int index) {
        return userRepository.findByEmailHash(blindIndexHasher.hash(email(seed, index)))
                .orElseGet(() -> userRepository.save(toUser(seed, index)));
    }

    private void requireEnoughElements() {
        int required = SEED_USERS.size() * DETAIL_ELEMENTS_PER_USER;

        if (PersonaSeedElements.DETAIL.size() < required) {
            throw new IllegalStateException("페르소나 시드 문장이 부족합니다. dimension=%s, required=%d, actual=%d"
                    .formatted(PersonaDimension.DETAIL, required, PersonaSeedElements.DETAIL.size()));
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
        Random random = new Random(ANSWER_SEED + index);
        List<PersonaElement> elements = new ArrayList<>();

        for (SurveyQuestion question : surveyLoader.getAllQuestions()) {
            SurveyOptionName answer = random.nextBoolean() ? SurveyOptionName.A : SurveyOptionName.B;

            elements.add(PersonaElement.create(userId, question.dimension(), question.traitFor(answer), createdAt));
        }

        for (String interest : interests(random)) {
            elements.add(PersonaElement.create(userId, PersonaDimension.INTEREST, interest, createdAt));
        }

        for (String detail : details(index)) {
            elements.add(PersonaElement.create(userId, PersonaDimension.DETAIL, detail, createdAt));
        }

        return List.copyOf(elements);
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
