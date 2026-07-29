package com.nidus.twinly.onboarding.integration;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionAgreement;
import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import com.nidus.twinly.anon.repository.AnonSessionAgreementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.onboarding.entity.SurveyAnswer;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AnonSessionRepository anonSessionRepository;

    @Autowired
    AnonSessionAgreementRepository anonSessionAgreementRepository;

    @Autowired
    AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;

    @Autowired
    SurveyAnswerRepository surveyAnswerRepository;

    @Autowired
    AiChatRepository aiChatRepository;

    @Autowired
    AnonSessionPhotoRepository anonSessionPhotoRepository;

    // CloudFront 서명 URL 생성은 실제 키가 필요하므로 목으로 대체한다.
    @MockitoBean
    CloudFrontService cloudFrontService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("기본 정보 입력: 실제 익명 세션 토큰 인증·MockMvc·DB까지 관통하여 세션의 개인정보가 갱신된다")
    void basicInfo_end_to_end() throws Exception {
        // given: 실제 익명 세션을 DB에 저장 (인증 리졸버가 이 토큰으로 세션을 찾는다)
        AnonSession session = saveAnonSession();

        // when: 익명 세션 토큰으로 기본 정보 입력 API 호출
        mockMvc.perform(put("/api/v1/onboarding/basic-info")
                        .header("Authorization", anonBearer(session))
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
                                """))
                .andExpect(status().isOk());

        // then: DB에서 다시 읽은 세션에 암호화 컬럼까지 실제로 반영됨
        flushAndClear();
        AnonSession reloaded = anonSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getFamilyName()).isEqualTo("홍");
        assertThat(reloaded.getGivenName()).isEqualTo("길동");
        assertThat(reloaded.getGender()).isEqualTo(Gender.MALE);
        assertThat(reloaded.getAffiliation()).isEqualTo("니두스대학교");
        assertThat(reloaded.getAffiliationNumber()).isEqualTo("2024001");
        assertThat(reloaded.getBirthDate()).isEqualTo("2000-01-01");
    }

    @Test
    @DisplayName("설문 답변: 실제 설문 로더의 문항에 답하면 survey_answers 행이 생성된다")
    // [의도된 실패 - BUG-ONB-01] SurveyAnswerRepository.findByAnonSessionIdAndQId 가
    // 엔티티 필드 qId를 'QId'로 해석해 UnknownPathException → 이 엔드포인트는 항상 500이다.
    // 운영 코드 버그이므로 초록불을 위해 테스트를 비틀지 않고 그대로 둔다. (docs/test-findings/onboarding.md)
    void surveyAnswer_end_to_end() throws Exception {
        // given: 실제 익명 세션 저장 (survey_answers가 anon_sessions를 FK로 참조)
        AnonSession session = saveAnonSession();

        // when: 실제 설문 파일에 존재하는 문항(qId=8)에 A로 답변
        mockMvc.perform(post("/api/v1/onboarding/survey-answers")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answer": {"qId": 8, "optionName": "A"}}
                                """))
                .andExpect(status().isOk());

        // then: DB에 답변이 저장되고, 마지막 문항이 아니므로 페르소나 요소는 아직 생성되지 않음
        flushAndClear();
        SurveyAnswer saved = surveyAnswerRepository.findByAnonSessionIdAndQId(session.getId(), 8).orElseThrow();
        assertThat(saved.getOptionName()).isEqualTo(SurveyOptionName.A);
        assertThat(anonSessionPersonaElementRepository.findAllByAnonSessionId(session.getId())).isEmpty();
    }

    @Test
    @DisplayName("AI 채팅 시작: Bedrock 응답을 스텁하면 첫 질문이 응답되고 0번 턴 AI 메시지가 저장된다")
    void aiChatStart_end_to_end() throws Exception {
        // given: 실제 익명 세션 + 외부 모델 호출은 목으로 차단
        AnonSession session = saveAnonSession();
        given(bedrockService.converse(anyString())).willReturn("요즘 제일 자주 가는 곳은 어디야?");

        // when: 익명 세션 토큰으로 AI 채팅 시작 API 호출
        mockMvc.perform(post("/api/v1/onboarding/ai-chat/start")
                        .header("Authorization", anonBearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("요즘 제일 자주 가는 곳은 어디야?"))
                .andExpect(jsonPath("$.turnIndex").value(0))
                .andExpect(jsonPath("$.isEnd").value(false));

        // then: DB에 0번 턴 AI 메시지가 저장됨
        flushAndClear();
        AiChat saved = aiChatRepository
                .findByAnonSessionIdAndTurnIndexAndSender(session.getId(), 0, AiChatSender.AI)
                .orElseThrow();
        assertThat(saved.getMessage()).isEqualTo("요즘 제일 자주 가는 곳은 어디야?");
    }

    @Test
    @DisplayName("동의 등록: policyId+version으로 실제 정책을 조회해 anon_session_agreements 행이 생성된다")
    void grantConsents_end_to_end() throws Exception {
        // given: 실제 익명 세션과 정책(terms_of_service v1) 픽스처를 DB에 저장
        AnonSession session = saveAnonSession();
        Long policyId = savePolicy("terms_of_service", "서비스 이용약관", 1, true);

        // when: 익명 세션 토큰으로 동의 등록 API 호출 (version은 문자열로 전달)
        mockMvc.perform(post("/api/v1/onboarding/consents")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grants": [{"policyId": "terms_of_service", "version": "1"}]}
                                """))
                .andExpect(status().isOk());

        // then: 해당 정책에 대한 동의 이력이 DB에 생성됨
        flushAndClear();
        List<AnonSessionAgreement> agreements =
                anonSessionAgreementRepository.findAllByAnonSessionIdAndRevokedAtIsNull(session.getId());
        assertThat(agreements).hasSize(1);
        assertThat(agreements.get(0).getPolicyId()).isEqualTo(policyId);
        assertThat(agreements.get(0).getAgreedAt()).isNotNull();
    }

    @Test
    @DisplayName("설문 문항 목록 조회: 인증 없이 실제 설문 파일이 로드되어 문항이 응답된다")
    void surveyQuestions_end_to_end() throws Exception {
        // when: 인증 없이 설문 문항 목록 API 호출 (public 엔드포인트)
        var result = mockMvc.perform(get("/api/v1/onboarding/survey-questions"));

        // then: 실제 설문 리소스가 로드되어 1개 이상의 문항이 id·dimension·options와 함께 응답됨
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].dimension").isNotEmpty())
                .andExpect(jsonPath("$[0].options").isNotEmpty());
    }

    @Test
    @DisplayName("관심사 등록: 전달한 관심사 수만큼 INTERESTS 페르소나 요소 행이 생성된다")
    void interests_end_to_end() throws Exception {
        // given: 실제 익명 세션 저장
        AnonSession session = saveAnonSession();

        // when: 익명 세션 토큰으로 관심사 등록 API 호출
        mockMvc.perform(post("/api/v1/onboarding/interests")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interests": ["등산", "재즈"]}
                                """))
                .andExpect(status().isOk());

        // then: DB에 INTERESTS 차원의 페르소나 요소가 2건 생성됨
        flushAndClear();
        assertThat(anonSessionPersonaElementRepository.findAllByAnonSessionId(session.getId()))
                .extracting(AnonSessionPersonaElement::getExplanation)
                .containsExactlyInAnyOrder("등산", "재즈");
    }

    @Test
    @DisplayName("닉네임 중복 확인: 이미 익명 세션이 쓰는 닉네임이면 isAvailable=false로 응답한다")
    void profileNicknameCheck_end_to_end() throws Exception {
        // given: 다른 익명 세션이 'taken-nick'을 이미 사용 중
        AnonSession session = saveAnonSession();
        AnonSession other = saveAnonSession();
        other.changeNickname("taken-nick");
        flushAndClear();

        // when: 사용 중인 닉네임과 비어 있는 닉네임을 각각 확인
        mockMvc.perform(post("/api/v1/onboarding/profile/nickname/check")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "taken-nick"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));

        // then: 아무도 쓰지 않는 닉네임은 isAvailable=true
        mockMvc.perform(post("/api/v1/onboarding/profile/nickname/check")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "free-nick"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    @DisplayName("닉네임 등록: 사용 가능한 닉네임이면 익명 세션의 nickname이 DB에 반영된다")
    void profileNickname_end_to_end() throws Exception {
        // given: 실제 익명 세션 저장
        AnonSession session = saveAnonSession();

        // when: 익명 세션 토큰으로 닉네임 등록 API 호출
        mockMvc.perform(put("/api/v1/onboarding/profile/nickname")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "new-nick"}
                                """))
                .andExpect(status().isOk());

        // then: DB에서 다시 읽어도 닉네임이 반영되어 있음
        flushAndClear();
        assertThat(anonSessionRepository.findById(session.getId()).orElseThrow().getNickname())
                .isEqualTo("new-nick");
    }

    @Test
    @DisplayName("닉네임 등록 실패: 이미 사용 중인 닉네임이면 409와 NICKNAME_ALREADY_USED 코드를 반환한다")
    void profileNickname_when_duplicated_returns_409() throws Exception {
        // given: 다른 익명 세션이 이미 같은 닉네임을 사용 중
        AnonSession session = saveAnonSession();
        AnonSession other = saveAnonSession();
        other.changeNickname("dup-nick");
        flushAndClear();

        // when: 같은 닉네임으로 등록 API 호출
        var result = mockMvc.perform(put("/api/v1/onboarding/profile/nickname")
                .header("Authorization", anonBearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "dup-nick"}
                        """));

        // then: 도메인 예외가 409 + NICKNAME_ALREADY_USED로 매핑됨
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NICKNAME_ALREADY_USED.name()));
    }

    @Test
    @DisplayName("프로필 사진 presign: 허용 content-type이면 익명 세션 id 기반 key와 업로드 URL이 응답된다")
    void profilePhotoPresign_end_to_end() throws Exception {
        // given: 실제 익명 세션 + S3 presign은 목으로 차단
        AnonSession session = saveAnonSession();
        given(s3Service.presignPut(anyString(), anyString(), any())).willReturn("https://s3.example.com/upload");

        // when: 익명 세션 토큰으로 presign API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/profile/photo/presign")
                .header("Authorization", anonBearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contentType": "image/jpeg"}
                        """));

        // then: key가 profile/{anonSessionId}/ 접두사로 만들어지고 업로드 메타가 함께 응답됨
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.example.com/upload"))
                .andExpect(jsonPath("$.key").value(startsWith("profile/" + session.getId() + "/")))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.requiredHeaders.contentType").value("image/jpeg"));
    }

    @Test
    @DisplayName("프로필 사진 presign 실패: 지원하지 않는 content-type이면 415와 UNSUPPORTED_IMAGE_TYPE 코드를 반환한다")
    void profilePhotoPresign_with_unsupported_type_returns_415() throws Exception {
        // given: 실제 익명 세션 저장
        AnonSession session = saveAnonSession();

        // when: 허용되지 않는 content-type으로 presign API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/profile/photo/presign")
                .header("Authorization", anonBearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contentType": "image/gif"}
                        """));

        // then: 도메인 예외가 415(UNSUPPORTED_MEDIA_TYPE) + UNSUPPORTED_IMAGE_TYPE으로 매핑됨
        result.andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_IMAGE_TYPE.name()));
    }

    @Test
    @DisplayName("프로필 사진 commit: 업로드가 끝난 key면 anon_session_photos 행이 생성된다")
    void profilePhotoCommit_end_to_end() throws Exception {
        // given: 실제 익명 세션 + S3 업로드 완료·CloudFront 서명 URL을 목으로 대체
        AnonSession session = saveAnonSession();
        String key = "profile/%d/photo-1".formatted(session.getId());
        given(s3Service.exists(key)).willReturn(true);
        given(cloudFrontService.getSignedUrl(key)).willReturn("https://cdn.example.com/" + key);

        // when: 익명 세션 토큰으로 commit API 호출
        mockMvc.perform(post("/api/v1/onboarding/profile/photo/commit")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "%s", "position": {"startPos": {"x": 1, "y": 2}, "width": 300, "height": 400}}
                                """.formatted(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://cdn.example.com/" + key))
                .andExpect(jsonPath("$.position.width").value(300));

        // then: DB에 프로필 사진 행이 key·좌표와 함께 생성됨
        flushAndClear();
        AnonSessionPhoto photo = anonSessionPhotoRepository
                .findByAnonSessionIdAndType(session.getId(), PhotoType.PROFILE).orElseThrow();
        assertThat(photo.getKey()).isEqualTo(key);
        assertThat(photo.getWidth()).isEqualTo(300);
        assertThat(photo.getHeight()).isEqualTo(400);
    }

    @Test
    @DisplayName("프로필 사진 commit 실패: 남의 key면 403과 NOT_KEY_OWNER 코드를 반환한다")
    void profilePhotoCommit_with_others_key_returns_403() throws Exception {
        // given: 다른 소유자의 key
        AnonSession session = saveAnonSession();

        // when: 소유자 접두사가 다른 key로 commit API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/profile/photo/commit")
                .header("Authorization", anonBearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "profile/999999/photo-1", "position": {"startPos": {"x": 0, "y": 0}, "width": 100, "height": 100}}
                        """));

        // then: 도메인 예외가 403 + NOT_KEY_OWNER로 매핑됨
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_KEY_OWNER.name()));
    }

    @Test
    @DisplayName("AI 채팅 답변: 이전 턴 AI 질문이 있으면 사용자 답변·다음 질문이 저장되고 DETAIL 요소가 생성된다")
    void aiChatMessage_end_to_end() throws Exception {
        // given: 0번 턴 AI 질문이 이미 저장된 상태 + 다음 질문은 Bedrock 목으로 스텁
        AnonSession session = saveAnonSession();
        aiChatRepository.save(AiChat.create(session.getId(), AiChatSender.AI, "요즘 뭐에 빠져 있어?", 0));
        given(bedrockService.converse(anyString())).willReturn("그거 언제부터 좋아했어?");
        flushAndClear();

        // when: 익명 세션 토큰으로 0번 턴 답변 API 호출
        mockMvc.perform(post("/api/v1/onboarding/ai-chat/messages")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "요즘 등산에 빠졌어", "turnIndex": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("그거 언제부터 좋아했어?"))
                .andExpect(jsonPath("$.turnIndex").value(1))
                .andExpect(jsonPath("$.isEnd").value(false));

        // then: 사용자 답변(0턴)과 다음 AI 질문(1턴)이 DB에 저장되고, DETAIL 페르소나 요소가 생성됨
        flushAndClear();
        assertThat(aiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(session.getId(), 0, AiChatSender.USER)
                .orElseThrow().getMessage()).isEqualTo("요즘 등산에 빠졌어");
        assertThat(aiChatRepository.findByAnonSessionIdAndTurnIndexAndSender(session.getId(), 1, AiChatSender.AI)
                .orElseThrow().getMessage()).isEqualTo("그거 언제부터 좋아했어?");
        assertThat(anonSessionPersonaElementRepository.findAllByAnonSessionId(session.getId()))
                .extracting(AnonSessionPersonaElement::getExplanation)
                .contains("요즘 뭐에 빠져 있어?: 요즘 등산에 빠졌어");
    }

    @Test
    @DisplayName("AI 채팅 답변 실패: 해당 턴의 AI 질문이 없으면 404와 AI_QUESTION_NOT_FOUND 코드를 반환한다")
    void aiChatMessage_when_question_missing_returns_404() throws Exception {
        // given: AI 질문이 하나도 없는 익명 세션
        AnonSession session = saveAnonSession();

        // when: 존재하지 않는 턴에 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/onboarding/ai-chat/messages")
                .header("Authorization", anonBearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"message": "안녕", "turnIndex": 0}
                        """));

        // then: 도메인 예외가 404 + AI_QUESTION_NOT_FOUND로 매핑됨
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.AI_QUESTION_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("동의 철회: 선택 정책이면 동의 이력의 revokedAt이 채워진다")
    void revokeConsents_end_to_end() throws Exception {
        // given: 선택(is_required=false) 정책에 이미 동의한 익명 세션
        AnonSession session = saveAnonSession();
        Long policyId = savePolicy("marketing", "마케팅 수신 동의", 1, false);
        anonSessionAgreementRepository.save(AnonSessionAgreement.create(session.getId(), policyId, Instant.now()));
        flushAndClear();

        // when: 익명 세션 토큰으로 동의 철회 API 호출
        mockMvc.perform(delete("/api/v1/onboarding/consents")
                        .header("Authorization", anonBearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grants": [{"policyId": "marketing", "version": "1"}]}
                                """))
                .andExpect(status().isOk());

        // then: 유효한(미철회) 동의 이력이 사라짐
        flushAndClear();
        assertThat(anonSessionAgreementRepository.findAllByAnonSessionIdAndRevokedAtIsNull(session.getId())).isEmpty();
    }

    @Test
    @DisplayName("동의 철회 실패: 필수 정책이면 403과 REQUIRED_POLICY_REVOKE_DENIED 코드를 반환하고 이력이 남는다")
    void revokeConsents_when_required_policy_returns_403() throws Exception {
        // given: 필수(is_required=true) 정책에 이미 동의한 익명 세션
        AnonSession session = saveAnonSession();
        Long policyId = savePolicy("terms_of_service", "서비스 이용약관", 1, true);
        anonSessionAgreementRepository.save(AnonSessionAgreement.create(session.getId(), policyId, Instant.now()));
        flushAndClear();

        // when: 필수 정책에 대해 철회 API 호출
        var result = mockMvc.perform(delete("/api/v1/onboarding/consents")
                .header("Authorization", anonBearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants": [{"policyId": "terms_of_service", "version": "1"}]}
                        """));

        // then: 403 + REQUIRED_POLICY_REVOKE_DENIED로 매핑되고 동의 이력은 그대로 유지됨
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUIRED_POLICY_REVOKE_DENIED.name()));
        flushAndClear();
        assertThat(anonSessionAgreementRepository.findAllByAnonSessionIdAndRevokedAtIsNull(session.getId())).hasSize(1);
    }

    /** 실제 익명 세션을 DB에 저장한다. (인증 리졸버가 토큰으로 조회한다) */
    private AnonSession saveAnonSession() {
        return anonSessionRepository.save(AnonSession.create(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(1))));
    }

    /** 익명 세션 토큰(UUID)으로 Authorization 헤더 값을 만든다. */
    private String anonBearer(AnonSession session) {
        return "Bearer " + session.getToken();
    }

    /** 정책명 + 정책 버전을 직접 insert하고 policies.id를 반환한다. (엔티티에 생성 팩토리가 없어 SQL로 픽스처 구성) */
    private Long savePolicy(String identifier, String name, Integer version, boolean isRequired) {
        jdbcTemplate.update("INSERT INTO policy_names (name, identifier, is_deprecated) VALUES (?, ?, ?)",
                name, identifier, false);
        Long policyNameId = jdbcTemplate.queryForObject(
                "SELECT id FROM policy_names WHERE identifier = ?", Long.class, identifier);
        jdbcTemplate.update("""
                        INSERT INTO policies (policy_name_id, version, content, url, is_required, effective_at)
                        VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
                        """,
                policyNameId, version, "약관 본문", "https://example.com/" + identifier, isRequired);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM policies WHERE policy_name_id = ? AND version = ?", Long.class, policyNameId, version);
    }

    /** 영속성 컨텍스트를 비워 실제 DB 상태를 다시 읽도록 한다. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
