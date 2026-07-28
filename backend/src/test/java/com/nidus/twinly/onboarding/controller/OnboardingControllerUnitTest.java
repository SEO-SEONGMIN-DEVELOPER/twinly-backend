package com.nidus.twinly.onboarding.controller;

import com.nidus.twinly.aichat.service.AiChatService;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.common.survey.SurveyAnswerInput;
import com.nidus.twinly.common.survey.SurveyOption;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.onboarding.dto.command.OnboardingAiChatMessageCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingBasicInfoCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingGrantConsentsCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingGrantConsentsItemCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingInterestsCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingProfileNicknameCheckCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingProfileNicknameCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingProfilePhotoCommitCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingProfilePhotoPresignCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingRevokeConsentsCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingRevokeConsentsItemCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingSurveyAnswerCommand;
import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatMessageResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatStartResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfileNicknameCheckResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoCommitResult;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoPresignResult;
import com.nidus.twinly.onboarding.service.OnboardingService;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnboardingController.class)
class OnboardingControllerUnitTest {

    private static final UUID ANON_TOKEN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ANON_BEARER = "Bearer " + ANON_TOKEN;

    private static final AnonSessionSnapshot ANON_SESSION = new AnonSessionSnapshot(
            1L,
            ANON_TOKEN,
            Instant.parse("2999-01-01T00:00:00Z"),
            "닉네임",
            "홍",
            "길동",
            Gender.MALE,
            "니두스대학교",
            "2024001",
            "2000-01-01",
            "01012345678",
            "phoneHash",
            "test@test.com",
            "emailHash",
            Instant.parse("2026-01-01T00:00:00Z")
    );

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OnboardingService onboardingService;

    @MockitoBean
    AiChatService aiChatService;

    // OnboardingController는 @CurrentAnonSession만 쓰지만, WebMvcConfig가 두 resolver를 모두 주입받고
    // 각 resolver가 이 서비스에 의존하므로 슬라이스 기동에 UserService mock도 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(anonService.resolveByToken(any())).willReturn(ANON_SESSION);
    }

    @Test
    @DisplayName("기본 정보 입력 성공 시 200을 반환하고 익명 세션과 요청 값으로 만든 커맨드로 서비스를 호출한다")
    void basicInfo_success() throws Exception {
        // when: 익명 세션 인증 상태로 기본 정보 입력 API 호출
        var result = mockMvc.perform(put("/api/v1/onboarding/basic-info")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {
                          "familyName": "홍",
                          "givenName": "길동",
                          "gender": "male",
                          "affiliation": "니두스대학교",
                          "affiliationNumber": "2024001",
                          "birthDate": "2000-01-01"
                        }
                        """));

        // then: 200 반환 + 익명 세션 스냅샷과 변환된 커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(onboardingService).should().basicInfo(ANON_SESSION, new OnboardingBasicInfoCommand(
                "홍", "길동", Gender.MALE, "니두스대학교", "2024001", LocalDate.of(2000, 1, 1)));
    }

    @Test
    @DisplayName("기본 정보 입력에 필수 필드가 빠지면 400을 반환하고 서비스를 호출하지 않는다")
    void basicInfo_with_missing_field_returns_400() throws Exception {
        // when: familyName이 빠진 요청으로 기본 정보 입력 API 호출
        var result = mockMvc.perform(put("/api/v1/onboarding/basic-info")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {
                          "givenName": "길동",
                          "gender": "male",
                          "affiliation": "니두스대학교",
                          "affiliationNumber": "2024001",
                          "birthDate": "2000-01-01"
                        }
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
        then(onboardingService).should(never()).basicInfo(any(), any());
    }

    @Test
    @DisplayName("익명 세션 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void basicInfo_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 기본 정보 입력 API 호출
        var result = mockMvc.perform(put("/api/v1/onboarding/basic-info")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {
                          "familyName": "홍",
                          "givenName": "길동",
                          "gender": "male",
                          "affiliation": "니두스대학교",
                          "affiliationNumber": "2024001",
                          "birthDate": "2000-01-01"
                        }
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(onboardingService).should(never()).basicInfo(any(), any());
    }

    @Test
    @DisplayName("설문 문항 조회는 인증 없이 200을 반환하고 문항을 응답 JSON으로 변환한다")
    void surveyQuestions_success() throws Exception {
        // given: 서비스가 설문 문항 1개를 반환
        given(onboardingService.surveyQuestions()).willReturn(List.of(new SurveyQuestion(
                8,
                PersonaDimension.OPENNESS,
                "다음 주 일정을 정리하고 있어요.",
                Map.of(
                        SurveyOptionName.A, new SurveyOption("A 선택지 라벨", "A 특성"),
                        SurveyOptionName.B, new SurveyOption("B 선택지 라벨", "B 특성")
                ))));

        // when: 설문 문항 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/onboarding/survey-questions"));

        // then: 200 반환 + id/dimension/scenario/선택지 라벨만 담긴 JSON 응답
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8))
                .andExpect(jsonPath("$[0].dimension").value("openness"))
                .andExpect(jsonPath("$[0].scenario").value("다음 주 일정을 정리하고 있어요."))
                .andExpect(jsonPath("$[0].options.A").value("A 선택지 라벨"))
                .andExpect(jsonPath("$[0].options.B").value("B 선택지 라벨"));
    }

    @Test
    @DisplayName("설문 답변 저장 성공 시 200을 반환하고 qId/optionName 커맨드로 서비스를 호출한다")
    void surveyAnswer_success() throws Exception {
        // when: 익명 세션 인증 상태로 설문 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/survey-answers")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answer": {"qId": 8, "optionName": "A"}}
                        """));

        // then: 200 반환 + 변환된 커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(onboardingService).should().surveyAnswer(ANON_SESSION,
                new OnboardingSurveyAnswerCommand(new SurveyAnswerInput(8, SurveyOptionName.A)));
    }

    @Test
    @DisplayName("설문 답변의 optionName이 빠지면 400을 반환하고 서비스를 호출하지 않는다")
    void surveyAnswer_with_missing_option_returns_400() throws Exception {
        // when: optionName이 빠진 요청으로 설문 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/survey-answers")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answer": {"qId": 8}}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(onboardingService).should(never()).surveyAnswer(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 설문 문항이면 서비스 예외가 404로 매핑된다")
    void surveyAnswer_when_question_not_found_returns_404() throws Exception {
        // given: 서비스가 SURVEY_QUESTION_NOT_FOUND 예외를 던짐
        willThrow(new BusinessException(ErrorCode.SURVEY_QUESTION_NOT_FOUND))
                .given(onboardingService).surveyAnswer(any(), any());

        // when: 존재하지 않는 qId로 설문 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/survey-answers")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answer": {"qId": 999, "optionName": "B"}}
                        """));

        // then: 404 반환 + 에러 코드 JSON
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SURVEY_QUESTION_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("관심사 저장 성공 시 200을 반환하고 관심사 목록 커맨드로 서비스를 호출한다")
    void interests_success() throws Exception {
        // when: 익명 세션 인증 상태로 관심사 저장 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/interests")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {"interests": ["등산", "독서"]}
                        """));

        // then: 200 반환 + 관심사 목록이 담긴 커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(onboardingService).should().interests(ANON_SESSION,
                new OnboardingInterestsCommand(List.of("등산", "독서")));
    }

    @Test
    @DisplayName("관심사 목록 필드가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void interests_with_null_list_returns_400() throws Exception {
        // when: interests 필드가 없는 요청으로 관심사 저장 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/interests")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(onboardingService).should(never()).interests(any(), any());
    }

    @Test
    @DisplayName("프로필 사진 presign 성공 시 200을 반환하고 업로드 정보를 응답 JSON으로 변환한다")
    void profilePhotoPresign_success() throws Exception {
        // given: 서비스가 presign 결과를 반환
        given(onboardingService.profilePhotoPresign(any(), any()))
                .willReturn(new OnboardingProfilePhotoPresignResult(
                        "https://s3.example.com/upload",
                        "profile/1/9f0f0f0f",
                        "PUT",
                        new RequiredHeaders("image/jpeg"),
                        10485760,
                        Instant.parse("2026-07-26T00:05:00Z")));

        // when: 익명 세션 인증 상태로 presign API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/profile/photo/presign")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contentType": "image/jpeg"}
                        """));

        // then: 200 반환 + presign 정보 JSON + contentType 커맨드로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.example.com/upload"))
                .andExpect(jsonPath("$.key").value("profile/1/9f0f0f0f"))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.requiredHeaders.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.maxBytes").value(10485760));
        then(onboardingService).should().profilePhotoPresign(ANON_SESSION,
                new OnboardingProfilePhotoPresignCommand("image/jpeg"));
    }

    @Test
    @DisplayName("프로필 사진 commit 성공 시 200을 반환하고 사진 URL과 위치 정보를 응답 JSON으로 변환한다")
    void profilePhotoCommit_success() throws Exception {
        // given: 서비스가 commit 결과를 반환
        PhotoPosInfo position = new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 300, 400);
        given(onboardingService.profilePhotoCommit(any(), any()))
                .willReturn(new OnboardingProfilePhotoCommitResult("https://cdn.example.com/profile/1/photo", position));

        // when: 익명 세션 인증 상태로 commit API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/profile/photo/commit")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "key": "profile/1/photo",
                          "position": {"startPos": {"x": 10, "y": 20}, "width": 300, "height": 400}
                        }
                        """));

        // then: 200 반환 + 사진 URL·위치 JSON + key/위치 커맨드로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://cdn.example.com/profile/1/photo"))
                .andExpect(jsonPath("$.position.startPos.x").value(10))
                .andExpect(jsonPath("$.position.startPos.y").value(20))
                .andExpect(jsonPath("$.position.width").value(300))
                .andExpect(jsonPath("$.position.height").value(400));
        then(onboardingService).should().profilePhotoCommit(ANON_SESSION,
                new OnboardingProfilePhotoCommitCommand("profile/1/photo", position));
    }

    @Test
    @DisplayName("프로필 사진 commit 요청에 위치 정보가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void profilePhotoCommit_without_position_returns_400() throws Exception {
        // when: position이 빠진 요청으로 commit API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/profile/photo/commit")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "profile/1/photo"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(onboardingService).should(never()).profilePhotoCommit(any(), any());
    }

    @Test
    @DisplayName("닉네임 중복 확인 성공 시 200과 사용 가능 여부를 반환한다")
    void profileNicknameCheck_success() throws Exception {
        // given: 서비스가 사용 가능(true)을 반환
        given(onboardingService.profileNicknameCheck(any(), any()))
                .willReturn(new OnboardingProfileNicknameCheckResult(true));

        // when: 익명 세션 인증 상태로 닉네임 중복 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/profile/nickname/check")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "twinly"}
                        """));

        // then: 200 반환 + isAvailable JSON + 닉네임 커맨드로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));
        then(onboardingService).should().profileNicknameCheck(ANON_SESSION,
                new OnboardingProfileNicknameCheckCommand("twinly"));
    }

    @Test
    @DisplayName("닉네임이 공백뿐이면 400을 반환하고 서비스를 호출하지 않는다")
    void profileNicknameCheck_with_blank_nickname_returns_400() throws Exception {
        // when: 공백 닉네임으로 중복 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/profile/nickname/check")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "   "}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(onboardingService).should(never()).profileNicknameCheck(any(), any());
    }

    @Test
    @DisplayName("닉네임 설정 성공 시 200을 반환하고 닉네임 커맨드로 서비스를 호출한다")
    void profileNickname_success() throws Exception {
        // when: 익명 세션 인증 상태로 닉네임 설정 API 호출
        var result = mockMvc.perform(put("/api/v1/onboarding/profile/nickname")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "twinly"}
                        """));

        // then: 200 반환 + 닉네임 커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(onboardingService).should().profileNickname(ANON_SESSION,
                new OnboardingProfileNicknameCommand("twinly"));
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임이면 서비스 예외가 409로 매핑된다")
    void profileNickname_when_already_used_returns_409() throws Exception {
        // given: 서비스가 NICKNAME_ALREADY_USED 예외를 던짐
        willThrow(new BusinessException(ErrorCode.NICKNAME_ALREADY_USED))
                .given(onboardingService).profileNickname(any(), any());

        // when: 중복 닉네임으로 닉네임 설정 API 호출
        var result = mockMvc.perform(put("/api/v1/onboarding/profile/nickname")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "twinly"}
                        """));

        // then: 409 반환 + 에러 코드 JSON
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NICKNAME_ALREADY_USED.name()));
    }

    @Test
    @DisplayName("AI 채팅 시작 성공 시 200을 반환하고 첫 질문을 응답 JSON으로 변환한다")
    void aiChatStart_success() throws Exception {
        // given: AI 채팅 서비스가 첫 질문을 반환
        given(aiChatService.aiChatStart(any()))
                .willReturn(new OnboardingAiChatStartResult("요즘 제일 자주 가는 곳은 어디야?", 0, false));

        // when: 익명 세션 인증 상태로 AI 채팅 시작 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/ai-chat/start")
                .header("Authorization", ANON_BEARER));

        // then: 200 반환 + 첫 질문 JSON + 익명 세션 스냅샷으로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("요즘 제일 자주 가는 곳은 어디야?"))
                .andExpect(jsonPath("$.turnIndex").value(0))
                .andExpect(jsonPath("$.isEnd").value(false));
        then(aiChatService).should().aiChatStart(ANON_SESSION);
    }

    @Test
    @DisplayName("AI 채팅 메시지 전송 성공 시 200을 반환하고 다음 질문을 응답 JSON으로 변환한다")
    void aiChatMessage_success() throws Exception {
        // given: AI 채팅 서비스가 다음 턴 질문을 반환
        given(aiChatService.aiChatMessage(any(), any()))
                .willReturn(new OnboardingAiChatMessageResult("그럼 거기서 뭘 주로 해?", 1, false));

        // when: 익명 세션 인증 상태로 AI 채팅 메시지 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/ai-chat/messages")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {"message": "요즘은 한강에 자주 가", "turnIndex": 0}
                        """));

        // then: 200 반환 + 다음 질문 JSON + 메시지/턴 커맨드로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("그럼 거기서 뭘 주로 해?"))
                .andExpect(jsonPath("$.turnIndex").value(1))
                .andExpect(jsonPath("$.isEnd").value(false));
        then(aiChatService).should().aiChatMessage(ANON_SESSION,
                new OnboardingAiChatMessageCommand("요즘은 한강에 자주 가", 0));
    }

    @Test
    @DisplayName("AI 채팅 메시지에 turnIndex가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void aiChatMessage_without_turnIndex_returns_400() throws Exception {
        // when: turnIndex가 빠진 요청으로 AI 채팅 메시지 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/ai-chat/messages")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("""
                        {"message": "요즘은 한강에 자주 가"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(aiChatService).should(never()).aiChatMessage(any(), any());
    }

    @Test
    @DisplayName("동의 등록 성공 시 200을 반환하고 policyId/version 커맨드로 서비스를 호출한다")
    void grantConsents_success() throws Exception {
        // when: 익명 세션 인증 상태로 동의 등록 API 호출 (version은 문자열로 전달)
        var result = mockMvc.perform(post("/api/v1/onboarding/consents")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants": [{"policyId": "terms_of_service", "version": "1"}]}
                        """));

        // then: 200 반환 + 문자열 version이 숫자로 매핑된 커맨드로 위임
        result.andExpect(status().isOk());
        then(onboardingService).should().grantConsents(ANON_SESSION, new OnboardingGrantConsentsCommand(
                List.of(new OnboardingGrantConsentsItemCommand("terms_of_service", 1))));
    }

    @Test
    @DisplayName("동의 등록 항목의 policyId가 비어 있으면 400을 반환하고 서비스를 호출하지 않는다")
    void grantConsents_with_blank_policyId_returns_400() throws Exception {
        // when: policyId가 빈 문자열인 요청으로 동의 등록 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/consents")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants": [{"policyId": "", "version": "1"}]}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(onboardingService).should(never()).grantConsents(any(), any());
    }

    @Test
    @DisplayName("동의 철회 성공 시 200을 반환하고 policyId/version 커맨드로 서비스를 호출한다")
    void revokeConsents_success() throws Exception {
        // when: 익명 세션 인증 상태로 동의 철회 API 호출
        var result = mockMvc.perform(delete("/api/v1/onboarding/consents")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants": [{"policyId": "marketing", "version": "2"}]}
                        """));

        // then: 200 반환 + 변환된 커맨드로 서비스에 위임
        result.andExpect(status().isOk());
        then(onboardingService).should().revokeConsents(ANON_SESSION, new OnboardingRevokeConsentsCommand(
                List.of(new OnboardingRevokeConsentsItemCommand("marketing", 2))));
    }

    @Test
    @DisplayName("필수 정책 철회 시 서비스 예외가 403으로 매핑된다")
    void revokeConsents_when_required_policy_returns_403() throws Exception {
        // given: 서비스가 REQUIRED_POLICY_REVOKE_DENIED 예외를 던짐
        willThrow(new BusinessException(ErrorCode.REQUIRED_POLICY_REVOKE_DENIED))
                .given(onboardingService).revokeConsents(any(), any());

        // when: 필수 정책 철회 API 호출
        var result = mockMvc.perform(delete("/api/v1/onboarding/consents")
                .header("Authorization", ANON_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants": [{"policyId": "terms_of_service", "version": "1"}]}
                        """));

        // then: 403 반환 + 에러 코드 JSON
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUIRED_POLICY_REVOKE_DENIED.name()));
    }
}
