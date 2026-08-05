package com.nidus.twinly.auth.integration;

import com.jayway.jsonpath.JsonPath;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.entity.RefreshToken;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.auth.repository.RefreshTokenRepository;
import com.nidus.twinly.auth.dto.result.AuthTokenResult;
import com.nidus.twinly.auth.repository.VerificationSessionRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AnonSessionRepository anonSessionRepository;

    @Autowired
    AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;

    @Autowired
    VerificationSessionRepository verificationSessionRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    BlindIndexHasher blindIndexHasher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("온보딩 이메일 인증번호 발송: 실제 익명 세션 인증을 통과해 인증 세션이 DB에 생성되고 코드가 메일로 나간다")
    void onboarding_email_send_end_to_end() throws Exception {
        // given: 실제 익명 세션과 가입 가능한 학교를 DB에 저장하고, 외부 메일 발송은 목으로 차단
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = anonSessionRepository.save(
                AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1))));
        saveSchool("니두스대학교", "nidus.ac.kr");
        willDoNothing().given(sesService).send(anyString(), anyString(), anyString());

        // when: 익명 세션 토큰을 Bearer로 붙여 온보딩 이메일 발송 API 호출
        String responseBody = mockMvc.perform(post("/api/v1/auth/onboarding/email/send")
                        .header("Authorization", "Bearer " + anonToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"onboarding@nidus.ac.kr"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerificationToken").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();

        // then: DB에 EMAIL 인증 세션이 생성되고, 응답 토큰이 DB 값과 같으며, 코드가 담긴 메일이 발송됨
        AnonSessionVerificationSession session = anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.EMAIL)
                .orElseThrow();
        assertThat(session.getContact()).isEqualTo("onboarding@nidus.ac.kr");
        assertThat(session.getCode()).hasSize(6).containsOnlyDigits();
        assertThat(session.getVerifiedAt()).isNull();
        assertThat(responseBody).contains(session.getVerificationToken().toString());

        then(sesService).should().send(eq("onboarding@nidus.ac.kr"), anyString(), contains(session.getCode()));
    }

    @Test
    @DisplayName("온보딩 이메일 인증번호 발송: 가입 가능한 학교 도메인이 아니면 422를 반환하고 인증 세션도 메일도 생기지 않는다")
    void onboarding_email_send_with_unsupported_domain_returns_422() throws Exception {
        // given: 학교 목록에는 니두스대학교만 등록되어 있음
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = anonSessionRepository.save(
                AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1))));
        saveSchool("니두스대학교", "nidus.ac.kr");

        // when: 목록에 없는 도메인으로 발송 요청 (앱 UI를 우회한 직접 호출)
        mockMvc.perform(post("/api/v1/auth/onboarding/email/send")
                        .header("Authorization", "Bearer " + anonToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"someone@gmail.com"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED.name()));

        // then: 인증 세션이 생기지 않고 메일도 나가지 않음
        assertThat(anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.EMAIL))
                .isEmpty();
        then(sesService).should(never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("회원가입: SMS·이메일 인증이 끝난 익명 세션으로 실제 유저가 생성되고 리프레시 토큰이 저장된다")
    void signup_end_to_end() throws Exception {
        // given: 온보딩 정보가 채워진 익명 세션 + SMS/EMAIL 인증이 완료된 인증 세션을 실제 DB에 저장
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1)));
        anonSession.changeNickname("signup-nick");
        anonSession.changeFamilyName("홍");
        anonSession.changeGivenName("길동");
        anonSession.changeGender(Gender.MALE);
        anonSession.changeSchool("트윈리대학교");
        anonSession.changeAffiliation("트윈리대학교");
        anonSession.changeAffiliationNumber("20250001");
        anonSession.changeBirthDate("2000-01-01");
        anonSessionRepository.save(anonSession);

        String phone = "01099998888";
        String email = "signup@test.com";
        anonSessionVerificationSessionRepository.save(verifiedAnonSession(anonSession.getId(), VerificationType.SMS, phone));
        anonSessionVerificationSessionRepository.save(verifiedAnonSession(anonSession.getId(), VerificationType.EMAIL, email));

        // when: 익명 세션 토큰을 Bearer로 붙여 회원가입 API 호출
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer " + anonToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.accessExpiresAt").exists())
                .andExpect(jsonPath("$.refreshExpiresAt").exists());

        // then: 인증 세션의 연락처와 익명 세션의 프로필로 실제 users 행이 생성됨
        User created = userRepository.findByPhoneNumberHash(blindIndexHasher.hash(phone)).orElseThrow();
        assertThat(created.getNickname()).isEqualTo("signup-nick");
        assertThat(created.getEmail()).isEqualTo(email);
        assertThat(created.getFamilyName()).isEqualTo("홍");
        assertThat(created.getGender()).isEqualTo(Gender.MALE);

        // then: 발급된 리프레시 토큰이 해당 유저 소유로 DB에 저장됨
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getUserId)
                .contains(created.getId());
    }

    @Test
    @DisplayName("회원가입 실패: 온보딩 프로필이 비어 있으면 422 PROFILE_NOT_COMPLETED를 반환하고 유저를 만들지 않는다")
    void signup_without_completed_profile_returns_422() throws Exception {
        // given: SMS·EMAIL 인증은 끝났지만 프로필을 입력하지 않은 익명 세션 (users는 해당 컬럼이 전부 NOT NULL이다)
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = anonSessionRepository.save(
                AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1))));

        String phone = "01055554444";
        anonSessionVerificationSessionRepository.save(verifiedAnonSession(anonSession.getId(), VerificationType.SMS, phone));
        anonSessionVerificationSessionRepository.save(verifiedAnonSession(anonSession.getId(), VerificationType.EMAIL, "incomplete@test.com"));

        // when: 익명 세션 토큰을 Bearer로 붙여 회원가입 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/signup")
                .header("Authorization", "Bearer " + anonToken));

        // then: NOT NULL 위반으로 500이 되는 대신 422 도메인 에러로 나가고 users 행도 생기지 않는다
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_COMPLETED"));
        assertThat(userRepository.findByPhoneNumberHash(blindIndexHasher.hash(phone))).isEmpty();
    }

    @Test
    @DisplayName("로그인: SMS 인증 완료 토큰으로 실제 유저의 토큰이 발급되고 리프레시 토큰이 DB에 저장된다")
    void login_end_to_end() throws Exception {
        // given: 실제 유저와, 그 전화번호로 SMS 인증이 끝난 인증 세션을 DB에 저장
        String phone = "01077776666";
        User user = userRepository.save(User.create(
                "login-nick",
                "김", blindIndexHasher.hash("김"),
                "영희", blindIndexHasher.hash("영희"),
                Gender.FEMALE,
                "트윈리대학교", blindIndexHasher.hash("트윈리대학교"),
                "트윈리대학교", blindIndexHasher.hash("트윈리대학교"),
                "20250002", blindIndexHasher.hash("20250002"),
                "2000-02-02", blindIndexHasher.hash("2000-02-02"),
                phone, blindIndexHasher.hash(phone),
                "login@test.com", blindIndexHasher.hash("login@test.com")));

        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, phone, "123456", Instant.now().plus(Duration.ofMinutes(5)));
        session.verify(Instant.now().plus(Duration.ofMinutes(30)));
        verificationSessionRepository.save(session);

        // when: 인증 완료 토큰으로 로그인 API 호출
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"smsVerifiedToken":"%s"}
                                """.formatted(session.getVerifiedToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());

        // then: 해당 유저 소유의 리프레시 토큰이 DB에 저장됨
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getUserId)
                .contains(user.getId());
    }

    @Test
    @DisplayName("회원가입: 익명 세션과 그 자식 행(인증 세션)이 모두 정리된다")
    void signup_cleans_up_anon_session_and_children() throws Exception {
        // given: 온보딩 정보와 SMS/EMAIL 인증 세션(= anon_sessions를 FK로 참조하는 자식 행)을 가진 익명 세션
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1)));
        anonSession.changeNickname("cleanup-nick");
        anonSession.changeFamilyName("정");
        anonSession.changeGivenName("수민");
        anonSession.changeGender(Gender.FEMALE);
        anonSession.changeSchool("트윈리대학교");
        anonSession.changeAffiliation("트윈리대학교");
        anonSession.changeAffiliationNumber("20250004");
        anonSession.changeBirthDate("2000-04-04");
        anonSessionRepository.save(anonSession);

        String phone = "01033332222";
        String email = "cleanup@test.com";
        anonSessionVerificationSessionRepository.save(verifiedAnonSession(anonSession.getId(), VerificationType.SMS, phone));
        anonSessionVerificationSessionRepository.save(verifiedAnonSession(anonSession.getId(), VerificationType.EMAIL, email));

        // when: 회원가입 API 호출
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer " + anonToken))
                .andExpect(status().isCreated());

        // then: 익명 세션과 자식 인증 세션이 모두 삭제된다
        // (이 조회가 anon_sessions 쿼리 스페이스를 건드려 auto-flush를 유발하므로, 삭제 순서가 잘못되면 여기서 FK 위반이 드러난다)
        assertThat(anonSessionRepository.findById(anonSession.getId())).isEmpty();
        assertThat(anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.SMS)).isEmpty();
        assertThat(anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("같은 초에 로그인을 두 번 해도 각각 다른 리프레시 토큰이 발급되고 둘 다 성공한다")
    void login_twice_in_same_second_issues_distinct_tokens() throws Exception {
        // given: 실제 유저와, 그 전화번호로 SMS 인증이 끝난 인증 세션을 DB에 저장
        String phone = "01055554444";
        User user = userRepository.save(User.create(
                "double-nick",
                "박", blindIndexHasher.hash("박"),
                "철수", blindIndexHasher.hash("철수"),
                Gender.MALE,
                "트윈리대학교", blindIndexHasher.hash("트윈리대학교"),
                "트윈리대학교", blindIndexHasher.hash("트윈리대학교"),
                "20250003", blindIndexHasher.hash("20250003"),
                "2000-03-03", blindIndexHasher.hash("2000-03-03"),
                phone, blindIndexHasher.hash(phone),
                "double@test.com", blindIndexHasher.hash("double@test.com")));

        VerificationSession session = VerificationSession.create(
                VerificationType.SMS, phone, "123456", Instant.now().plus(Duration.ofMinutes(5)));
        session.verify(Instant.now().plus(Duration.ofMinutes(30)));
        verificationSessionRepository.save(session);

        String body = """
                {"smsVerifiedToken":"%s"}
                """.formatted(session.getVerifiedToken());

        // when: 같은 인증 완료 토큰으로 지연 없이 로그인을 두 번 호출 (버튼 더블클릭·클라이언트 재시도 상황)
        String firstResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then: 두 번째 호출도 성공하고, 두 리프레시 토큰은 서로 다른 값이다
        String firstRefreshToken = JsonPath.read(firstResponse, "$.refreshToken");
        String secondRefreshToken = JsonPath.read(secondResponse, "$.refreshToken");
        assertThat(firstRefreshToken).isNotEqualTo(secondRefreshToken);

        // then: 두 토큰이 각각 DB에 저장되어 두 세션이 독립적으로 살아 있다
        assertThat(refreshTokenRepository.findAll())
                .filteredOn(token -> token.getUserId().equals(user.getId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("온보딩 SMS 인증번호 발송: 익명 세션 인증을 통과해 SMS 인증 세션이 생성되고 코드가 문자로 나간다")
    void onboarding_sms_send_end_to_end() throws Exception {
        // given: 실제 익명 세션을 저장하고 외부 문자 발송은 목으로 차단
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = anonSessionRepository.save(
                AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1))));
        willDoNothing().given(solapiService).send(anyString(), anyString());

        // when: 익명 세션 토큰을 Bearer로 붙여 온보딩 SMS 발송 API 호출
        mockMvc.perform(post("/api/v1/auth/onboarding/sms/send")
                        .header("Authorization", "Bearer " + anonToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"01011112222"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsVerificationToken").exists())
                .andExpect(jsonPath("$.expiresAt").exists());

        // then: DB에 SMS 인증 세션이 생성되고, 그 코드가 담긴 문자가 발송됨
        AnonSessionVerificationSession session = anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.SMS)
                .orElseThrow();
        assertThat(session.getContact()).isEqualTo("01011112222");
        assertThat(session.getCode()).hasSize(6).containsOnlyDigits();
        then(solapiService).should().send(eq("01011112222"), contains(session.getCode()));
    }

    @Test
    @DisplayName("온보딩 이메일 인증 확인: 올바른 토큰·코드면 verifiedAt이 채워지고 익명 세션에 학교가 기록된다")
    void onboarding_email_verify_end_to_end() throws Exception {
        // given: 아직 인증되지 않은 EMAIL 인증 세션과, 그 도메인을 쓰는 학교를 실제 DB에 저장
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = anonSessionRepository.save(
                AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1))));
        saveSchool("니두스대학교", "nidus.ac.kr");
        AnonSessionVerificationSession session = anonSessionVerificationSessionRepository.save(
                AnonSessionVerificationSession.create(VerificationType.EMAIL, anonSession.getId(),
                        "verify@nidus.ac.kr", "123456", Instant.now().plus(Duration.ofMinutes(5))));

        // when: 저장된 토큰·코드로 온보딩 이메일 인증 확인 API 호출
        mockMvc.perform(post("/api/v1/auth/onboarding/email/verify")
                        .header("Authorization", "Bearer " + anonToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailVerificationToken":"%s","code":"123456"}
                                """.formatted(session.getVerificationToken())))
                .andExpect(status().isOk());

        // then: DB에 인증 완료 시각이 기록되고, 학교는 사용자 입력이 아니라 인증된 도메인으로 결정됨
        AnonSessionVerificationSession verified = anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.EMAIL).orElseThrow();
        assertThat(verified.getVerifiedAt()).isNotNull();
        assertThat(anonSessionRepository.findById(anonSession.getId()).orElseThrow().getSchool())
                .isEqualTo("니두스대학교");
    }

    @Test
    @DisplayName("온보딩 SMS 인증 확인 실패: 코드가 다르면 422와 VERIFICATION_CODE_MISMATCH 코드를 반환한다")
    void onboarding_sms_verify_with_wrong_code_returns_422() throws Exception {
        // given: 코드가 123456인 SMS 인증 세션을 저장
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = anonSessionRepository.save(
                AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1))));
        AnonSessionVerificationSession session = anonSessionVerificationSessionRepository.save(
                AnonSessionVerificationSession.create(VerificationType.SMS, anonSession.getId(),
                        "01033334444", "123456", Instant.now().plus(Duration.ofMinutes(5))));

        // when: 틀린 코드로 온보딩 SMS 인증 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/sms/verify")
                .header("Authorization", "Bearer " + anonToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"smsVerificationToken":"%s","code":"000000"}
                        """.formatted(session.getVerificationToken())));

        // then: 도메인 예외가 422 + VERIFICATION_CODE_MISMATCH로 매핑되고 verifiedAt은 그대로 null
        result.andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value(ErrorCode.VERIFICATION_CODE_MISMATCH.name()));
        assertThat(anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.SMS)
                .orElseThrow().getVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("온보딩 SMS 인증 확인 성공: 올바른 토큰·코드면 DB 인증 세션의 verifiedAt이 채워진다")
    void onboarding_sms_verify_end_to_end() throws Exception {
        // given: 아직 인증되지 않은 SMS 인증 세션을 실제 DB에 저장
        UUID anonToken = UUID.randomUUID();
        AnonSession anonSession = anonSessionRepository.save(
                AnonSession.create(anonToken, Instant.now().plus(Duration.ofDays(1))));
        AnonSessionVerificationSession session = anonSessionVerificationSessionRepository.save(
                AnonSessionVerificationSession.create(VerificationType.SMS, anonSession.getId(),
                        "01055556666", "123456", Instant.now().plus(Duration.ofMinutes(5))));

        // when: 저장된 토큰·코드로 온보딩 SMS 인증 확인 API 호출
        mockMvc.perform(post("/api/v1/auth/onboarding/sms/verify")
                        .header("Authorization", "Bearer " + anonToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"smsVerificationToken":"%s","code":"123456"}
                                """.formatted(session.getVerificationToken())))
                .andExpect(status().isOk());

        // then: DB에 인증 완료 시각이 기록됨
        assertThat(anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.SMS)
                .orElseThrow().getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("로그인용 이메일 인증번호 발송: 가입된 이메일이면 인증 세션이 생성되고 코드가 메일로 나간다")
    void email_send_end_to_end() throws Exception {
        // given: 해당 이메일로 가입된 실제 유저 저장 (블라인드 인덱스 해시 조회를 실제로 태운다)
        String email = "member@test.com";
        saveUserWith("01012340000", email);
        willDoNothing().given(sesService).send(anyString(), anyString(), anyString());

        // when: 로그인용 이메일 인증번호 발송 API 호출
        mockMvc.perform(post("/api/v1/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerificationToken").exists());

        // then: verification_sessions에 EMAIL 세션이 생성되고 그 코드가 메일로 발송됨
        VerificationSession session = verificationSessionRepository.findAll().stream()
                .filter(s -> s.getType() == VerificationType.EMAIL && email.equals(s.getContact()))
                .findFirst().orElseThrow();
        then(sesService).should().send(eq(email), anyString(), contains(session.getCode()));
    }

    @Test
    @DisplayName("로그인용 이메일 인증번호 발송 실패: 가입되지 않은 이메일이면 404와 EMAIL_NOT_REGISTERED 코드를 반환한다")
    void email_send_when_not_registered_returns_404() throws Exception {
        // when: 가입 이력이 없는 이메일로 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"nobody@test.com"}
                        """));

        // then: 도메인 예외가 404 + EMAIL_NOT_REGISTERED로 매핑됨
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_NOT_REGISTERED.name()));
    }

    @Test
    @DisplayName("로그인용 이메일 인증 확인: 올바른 토큰·코드면 verifiedToken이 발급되고 DB에 저장된다")
    void email_verify_end_to_end() throws Exception {
        // given: 코드가 123456인 EMAIL 인증 세션을 실제 DB에 저장
        VerificationSession session = verificationSessionRepository.save(VerificationSession.create(
                VerificationType.EMAIL, "verify-login@test.com", "123456", Instant.now().plus(Duration.ofMinutes(5))));

        // when: 저장된 토큰·코드로 인증 확인 API 호출
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailVerificationToken":"%s","code":"123456"}
                                """.formatted(session.getVerificationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerifiedToken").exists())
                .andExpect(jsonPath("$.expiresAt").exists());

        // then: DB 세션에 verifiedToken/verifiedAt이 채워짐
        VerificationSession verified = verificationSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(verified.getVerifiedToken()).isNotNull();
        assertThat(verified.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("로그인용 SMS 인증번호 발송: 가입된 번호면 인증 세션이 생성되고 코드가 문자로 나간다")
    void sms_send_end_to_end() throws Exception {
        // given: 해당 번호로 가입된 실제 유저 저장
        String phone = "01043210000";
        saveUserWith(phone, "sms-member@test.com");
        willDoNothing().given(solapiService).send(anyString(), anyString());

        // when: 로그인용 SMS 인증번호 발송 API 호출
        mockMvc.perform(post("/api/v1/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsVerificationToken").exists());

        // then: verification_sessions에 SMS 세션이 생성되고 그 코드가 문자로 발송됨
        VerificationSession session = verificationSessionRepository.findAll().stream()
                .filter(s -> s.getType() == VerificationType.SMS && phone.equals(s.getContact()))
                .findFirst().orElseThrow();
        then(solapiService).should().send(eq(phone), contains(session.getCode()));
    }

    @Test
    @DisplayName("로그인용 SMS 인증번호 발송 실패: 가입되지 않은 번호면 404와 PHONE_NOT_REGISTERED 코드를 반환한다")
    void sms_send_when_not_registered_returns_404() throws Exception {
        // when: 가입 이력이 없는 번호로 발송 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"01000000000"}
                        """));

        // then: 도메인 예외가 404 + PHONE_NOT_REGISTERED로 매핑됨
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.PHONE_NOT_REGISTERED.name()));
    }

    @Test
    @DisplayName("로그인용 SMS 인증 확인: 올바른 토큰·코드면 verifiedToken이 발급되고 DB에 저장된다")
    void sms_verify_end_to_end() throws Exception {
        // given: 코드가 123456인 SMS 인증 세션을 실제 DB에 저장
        VerificationSession session = verificationSessionRepository.save(VerificationSession.create(
                VerificationType.SMS, "01098760000", "123456", Instant.now().plus(Duration.ofMinutes(5))));

        // when: 저장된 토큰·코드로 인증 확인 API 호출
        mockMvc.perform(post("/api/v1/auth/sms/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"smsVerificationToken":"%s","code":"123456"}
                                """.formatted(session.getVerificationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsVerifiedToken").exists());

        // then: DB 세션에 verifiedToken/verifiedAt이 채워짐
        VerificationSession verified = verificationSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(verified.getVerifiedToken()).isNotNull();
        assertThat(verified.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("토큰 재발급: 유효한 리프레시 토큰이면 새 토큰이 발급되고 기존 토큰 행은 회전(삭제)된다")
    void refresh_end_to_end() throws Exception {
        // given: 실제 유저와, 그 유저에게 발급된 리프레시 토큰 행을 DB에 저장
        // (발급 직후 곧바로 재발급한다. jti 덕분에 같은 초에 발급해도 토큰이 달라지므로 지연이 필요 없다)
        User user = saveUser();
        AuthTokenResult issued = jwtService.generateAuthTokenResult(user.getId());
        refreshTokenRepository.save(RefreshToken.create(
                user.getId(), blindIndexHasher.hash(issued.refreshToken()), issued.refreshExpiresAt()));

        // when: 해당 리프레시 토큰으로 재발급 API 호출
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(issued.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());

        // then: 기존 토큰 행은 사라지고(회전) 해당 유저의 새 토큰 행이 남아 있음
        assertThat(refreshTokenRepository.findByTokenHash(blindIndexHasher.hash(issued.refreshToken()))).isEmpty();
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getUserId)
                .contains(user.getId());
    }

    @Test
    @DisplayName("토큰 재발급 실패: DB에 없는(이미 회수된) 리프레시 토큰이면 401과 REFRESH_TOKEN_ALREADY_REVOKED 코드를 반환한다")
    void refresh_when_revoked_returns_401() throws Exception {
        // given: 형식은 유효하지만 DB에 저장되지 않은 리프레시 토큰
        User user = saveUser();
        String orphanToken = jwtService.generateRefreshToken(user.getId()).value();

        // when: 해당 토큰으로 재발급 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(orphanToken)));

        // then: 도메인 예외가 401 + REFRESH_TOKEN_ALREADY_REVOKED로 매핑됨
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.REFRESH_TOKEN_ALREADY_REVOKED.name()));
    }

    @Test
    @DisplayName("로그아웃: 해당 리프레시 토큰 행이 DB에서 삭제된다")
    void logout_end_to_end() throws Exception {
        // given: 실제 유저와 저장된 리프레시 토큰 행
        User user = saveUser();
        AuthTokenResult issued = jwtService.generateAuthTokenResult(user.getId());
        refreshTokenRepository.save(RefreshToken.create(
                user.getId(), blindIndexHasher.hash(issued.refreshToken()), issued.refreshExpiresAt()));

        // when: 해당 리프레시 토큰으로 로그아웃 API 호출
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(issued.refreshToken())))
                .andExpect(status().isOk());

        // then: DB에서 해당 토큰 행이 사라짐
        assertThat(refreshTokenRepository.findByTokenHash(blindIndexHasher.hash(issued.refreshToken()))).isEmpty();
    }

    @Test
    @DisplayName("로그아웃 멱등: 이미 없는 리프레시 토큰이어도 200으로 응답한다")
    void logout_when_token_absent_is_idempotent() throws Exception {
        // given: DB에 저장되지 않은 리프레시 토큰
        User user = saveUser();
        String orphanToken = jwtService.generateRefreshToken(user.getId()).value();

        // when: 해당 토큰으로 로그아웃 API 호출
        var result = mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(orphanToken)));

        // then: 예외 없이 200 (멱등)
        result.andExpect(status().isOk());
    }

    /** 지정한 전화번호·이메일로 실제 users 행을 만든다 (블라인드 인덱스 해시 포함). */
    /** 가입 가능한 학교와 이메일 도메인을 직접 insert한다. (운영에서도 마이그레이션 SQL로 주입되는 카탈로그 데이터) */
    private void saveSchool(String name, String domain) {
        jdbcTemplate.update("INSERT INTO schools (name) VALUES (?)", name);
        Long schoolId = jdbcTemplate.queryForObject("SELECT id FROM schools WHERE name = ?", Long.class, name);
        jdbcTemplate.update("INSERT INTO school_domains (school_id, domain) VALUES (?, ?)", schoolId, domain);
    }

    private User saveUserWith(String phone, String email) {
        return userRepository.save(User.create(
                "nick-" + phone,
                "김", blindIndexHasher.hash("김"),
                "철수", blindIndexHasher.hash("철수"),
                Gender.MALE,
                "트윈리대학교", blindIndexHasher.hash("트윈리대학교"),
                "트윈리대학교", blindIndexHasher.hash("트윈리대학교"),
                "20250003", blindIndexHasher.hash("20250003"),
                "2000-03-03", blindIndexHasher.hash("2000-03-03"),
                phone, blindIndexHasher.hash(phone),
                email, blindIndexHasher.hash(email)));
    }

    private AnonSessionVerificationSession verifiedAnonSession(Long anonSessionId, VerificationType type, String contact) {
        AnonSessionVerificationSession session = AnonSessionVerificationSession.create(
                type, anonSessionId, contact, "123456", Instant.now().plus(Duration.ofMinutes(5)));
        session.verify();
        return session;
    }
}
