package com.nidus.twinly.me.integration;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.feedback.FeedbackOption;
import com.nidus.twinly.common.feedback.FeedbackOptionLoader;
import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationChannel;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.entity.NotificationSetting;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.notification.repository.NotificationSettingRepository;
import com.nidus.twinly.people.entity.Encounter;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.simulation.client.SimulationPreloadClient;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.UserFeedback;
import com.nidus.twinly.user.entity.UserFeedbackOption;
import com.nidus.twinly.user.entity.UserTendencyAnswer;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserFeedbackOptionRepository;
import com.nidus.twinly.user.repository.UserFeedbackRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import com.nidus.twinly.user.repository.UserTendencyAnswerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.tendency.TendencyLoader;
import com.nidus.twinly.common.tendency.TendencyOption;
import com.nidus.twinly.common.tendency.TendencyQuestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeIntegrationTest extends AbstractIntegrationTest {

    /** MeService가 오늘 날짜를 판정할 때 쓰는 타임존과 동일해야 한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    PolicyNameRepository policyNameRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    PhotoRepository photoRepository;

    @Autowired
    PersonaElementRepository personaElementRepository;

    @Autowired
    UserSurveyAnswerRepository userSurveyAnswerRepository;

    @Autowired
    AiChatRepository aiChatRepository;

    @Autowired
    SurveyLoader surveyLoader;

    @Autowired
    UserTendencyAnswerRepository userTendencyAnswerRepository;

    @Autowired
    UserFeedbackRepository userFeedbackRepository;

    @Autowired
    UserFeedbackOptionRepository userFeedbackOptionRepository;

    @Autowired
    FeedbackOptionLoader feedbackOptionLoader;

    @Autowired
    TendencyLoader tendencyLoader;

    @Autowired
    EncounterRepository encounterRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    BlindIndexHasher blindIndexHasher;

    @Autowired
    EntityManager entityManager;

    @Autowired
    AnonSessionRepository anonSessionRepository;

    // CloudFront 서명 URL 생성은 실제 키가 필요하므로 목으로 대체한다.
    @MockitoBean
    CloudFrontService cloudFrontService;

    @MockitoBean
    SimulationPreloadClient simulationPreloadClient;

    @Autowired
    PolicyRepository policyRepository;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @Autowired
    UserEntitlementRepository userEntitlementRepository;

    @Autowired
    NotificationSettingRepository notificationSettingRepository;

    @Autowired
    AppNotificationFeedRepository appNotificationFeedRepository;

    /**
     * V2 마이그레이션이 넣는 운영 정책명 6건을 비운다.
     * consents 배열의 첫 항목을 단언하는 테스트는 시드 행이 id가 더 작아 앞자리를 뺏긴다.
     * 이 클래스에는 @Transactional 을 끄는 테스트가 있어 @BeforeEach 로 두면 삭제가 커밋되므로,
     * 필요한 테스트에서만 호출한다.
     */
    private void clearSeededPolicyNames() {
        agreementRepository.deleteAllInBatch();
        policyRepository.deleteAllInBatch();
        policyNameRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("탈퇴 신청: 실제 유저·JWT 인증·MockMvc·DB까지 관통하여 탈퇴 신청 시각과 예정 시각이 기록된다")
    void withdraw_end_to_end() throws Exception {
        // given: 실제 유저 저장
        User me = saveUser();

        // when: 실제 액세스 토큰으로 탈퇴 API 호출
        mockMvc.perform(delete("/api/v1/me")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoverableUntil").exists());

        // then: DB의 유저에 탈퇴 신청 시각과 15일 뒤 예정 시각이 기록됨
        User saved = userRepository.findById(me.getId()).orElseThrow();
        assertThat(saved.getWithdrawalRequestedAt()).isNotNull();
        assertThat(Duration.between(saved.getWithdrawalRequestedAt(), saved.getWithdrawalScheduledAt()))
                .isEqualTo(Duration.ofDays(15));
    }

    @Test
    @DisplayName("약관 동의: 동의 등록 후 목록 조회까지 관통하여 agreement 행이 생성되고 isGranted가 true로 내려온다")
    void grantConsents_and_list_end_to_end() throws Exception {
        // given: 실제 유저 + 발효된 필수 약관 1건 저장
        clearSeededPolicyNames();
        User me = saveUser();
        PolicyName policyName = policyNameRepository.save(policyName("서비스 이용약관", "terms_of_service"));
        Policy policy = policyRepository.save(policy(policyName.getId(), "1", true));

        // when: 약관 동의 API 호출
        mockMvc.perform(post("/api/v1/me/consents")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grants":[{"policyId":"terms_of_service","version":"1"}]}
                                """))
                .andExpect(status().isOk());

        // then: DB에 동의 행이 생성되고, 목록 조회 시 동의 상태로 내려옴
        List<Agreement> agreements = agreementRepository.findAllByUserIdAndRevokedAtIsNull(me.getId());
        assertThat(agreements).hasSize(1);
        assertThat(agreements.get(0).getPolicyId()).isEqualTo(policy.getId());

        mockMvc.perform(get("/api/v1/me/consents")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[0].policyId").value("terms_of_service"))
                .andExpect(jsonPath("$.consents[0].title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.consents[0].version").value("1"))
                .andExpect(jsonPath("$.consents[0].isRequired").value(true))
                .andExpect(jsonPath("$.consents[0].isGranted").value(true));
    }

    @Test
    @DisplayName("약관 동의: 결제 후 평행우주 입장 필수 약관에 늦게 동의하면 그때 현재 시즌 참가 행이 생긴다")
    void grantConsents_after_purchase_participates_in_current_season_end_to_end() throws Exception {
        // given: 진행 중인 시즌, 구독 중이지만 아직 필수 약관에 동의하지 않아 참가 행이 없는 유저
        Instant now = Instant.now();
        Season season = seasonRepository.save(Season.create(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30))));
        User me = saveUser();
        userEntitlementRepository.save(UserEntitlement.create(
                me.getId(), EntitlementReader.SIMULATION_ACCESS, now.plus(Duration.ofDays(30)), now));
        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(me.getId(), season.getId())).isEmpty();

        // when: 평행우주 입장 약관 목록의 최신 버전에 동의
        mockMvc.perform(post("/api/v1/me/consents")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grants":[{"policyId":"thirdPartyRealIdentityDisclosure","version":"1.1"}]}
                                """))
                .andExpect(status().isOk());

        // then: 동의 시점에 현재 시즌 참가 행이 생겨 앱에 참여 중으로 보인다
        assertThat(seasonParticipationRepository.findByUserIdAndSeasonId(me.getId(), season.getId())).isPresent();
    }

    @Test
    @DisplayName("약관 동의: 시즌 첫 참가라도 트랜잭션이 커밋되지 않으면 선생성을 요청하지 않는다")
    void grantConsents_first_participation_without_commit_does_not_request_preload() throws Exception {
        // given: 진행 중인 시즌, 권한만 있고 참가 행이 없는 유저 (테스트 트랜잭션은 끝나면 롤백된다)
        Instant now = Instant.now();
        seasonRepository.save(Season.create(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30))));
        User me = saveUser();
        userEntitlementRepository.save(UserEntitlement.create(
                me.getId(), EntitlementReader.SIMULATION_ACCESS, now.plus(Duration.ofDays(30)), now));

        // when: 필수 약관에 동의해 처음으로 시즌에 참가
        mockMvc.perform(post("/api/v1/me/consents")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grants":[{"policyId":"thirdPartyRealIdentityDisclosure","version":"1.1"}]}
                                """))
                .andExpect(status().isOk());

        // then: 동의가 아직 커밋되지 않았으므로 AI 서버가 403을 받을 요청을 미리 보내지 않는다
        then(simulationPreloadClient).should(after(1000).never()).preload(any(), any(), anyList());
    }

    @Test
    @DisplayName("약관 동의: 시즌에 처음 참가하면 커밋 뒤에 선생성을 요청해, AI 서버가 곧바로 페르소나를 조회해도 403이 나지 않는다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void grantConsents_first_participation_requests_preload_after_commit() throws Exception {
        // given: 진행 중인 시즌, 선착순 부여로 가입 직후 권한만 받고 아직 약관에 동의하지 않은 유저
        Instant now = Instant.now();
        Season season = seasonRepository.save(Season.create(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30))));
        User me = saveUser();
        userEntitlementRepository.save(UserEntitlement.create(
                me.getId(), EntitlementReader.SIMULATION_ACCESS, now.plus(Duration.ofDays(30)), now));

        // given: AI 서버처럼 선생성 요청을 받자마자 페르소나를 조회한다
        CompletableFuture<Integer> personaStatus = new CompletableFuture<>();
        willAnswer(invocation -> {
            personaStatus.complete(mockMvc.perform(get("/internal/v1/users/{userId}/persona", me.getId())
                            .param("date", KstTimes.today().toString()))
                    .andReturn().getResponse().getStatus());
            return null;
        }).given(simulationPreloadClient).preload(eq(me.getId()), any(), anyList());

        try {
            // when: 평행우주 입장 필수 약관에 동의해 처음으로 시즌에 참가
            mockMvc.perform(post("/api/v1/me/consents")
                            .header("Authorization", bearer(me.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"grants":[{"policyId":"thirdPartyRealIdentityDisclosure","version":"1.1"}]}
                                    """))
                    .andExpect(status().isOk());

            // then: 약관 동의가 커밋된 뒤라 선생성 직후 페르소나 조회가 성공한다
            assertThat(personaStatus.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            seasonParticipationRepository.findByUserIdAndSeasonId(me.getId(), season.getId())
                    .ifPresent(seasonParticipationRepository::delete);
            agreementRepository.deleteAll(agreementRepository.findAllByUserIdAndRevokedAtIsNull(me.getId()));
            userEntitlementRepository.deleteAll(userEntitlementRepository.findAllByUserId(me.getId()));
            seasonRepository.deleteById(season.getId());
            userRepository.deleteById(me.getId());
        }
    }

    @Test
    @DisplayName("푸시 알림 설정: 변경 후 조회까지 관통하여 DB 설정과 응답이 함께 바뀐다")
    void changePushNotifications_and_get_end_to_end() throws Exception {
        // given: 실제 유저 저장 (푸시 설정 행은 아직 없음)
        User me = saveUser();

        // when: CHAT 푸시 알림을 off로 변경
        mockMvc.perform(patch("/api/v1/me/push-notifications/{type}", "CHAT")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isEnabled":false}
                                """))
                .andExpect(status().isOk());

        // then: DB에 PUSH/CHAT 설정이 off로 생성되고, 조회 응답도 chat만 false
        NotificationSetting setting = notificationSettingRepository
                .findByUserIdAndChannelAndType(me.getId(), NotificationChannel.PUSH, NotificationType.CHAT)
                .orElseThrow();
        assertThat(setting.getEnabled()).isFalse();

        mockMvc.perform(get("/api/v1/me/push-notifications")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushNotificationSettings.chat").value(false))
                .andExpect(jsonPath("$.pushNotificationSettings.event").value(true))
                .andExpect(jsonPath("$.pushNotificationSettings.marketing").value(true));
    }

    @Test
    @DisplayName("앱 알림: 피드 조회 후 읽음 처리까지 관통하여 미읽음 개수가 실제로 줄어든다")
    void appNotificationsFeeds_and_read_end_to_end() throws Exception {
        // given: 실제 유저 2명(수신자/타깃) + 미읽음 알림 2건 저장 (target_user_id FK 때문에 타깃 유저 필요)
        User me = saveUser();
        User target = saveUser();
        Instant now = Instant.now();
        AppNotificationFeed newer = appNotificationFeedRepository.save(
                feed(me.getId(), target.getId(), "새 친구", "친구 요청이 도착했어요", now));
        appNotificationFeedRepository.save(
                feed(me.getId(), target.getId(), "지난 친구", "예전 친구 요청", now.minus(Duration.ofHours(1))));

        // when: 앱 알림 피드를 조회
        mockMvc.perform(get("/api/v1/me/app-notifications/feeds")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.appNotificationFeeds[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$.appNotificationFeeds[0].type").value("friend"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].isRead").value(false))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.kind").value("profile"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.userId").value(target.getId().toString()));

        // when: 최신 알림 1건을 읽음 처리
        mockMvc.perform(post("/api/v1/me/app-notifications/{appNotificationId}/read", newer.getId().toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: DB 기준 미읽음 개수가 1로 줄고, 미읽음 개수 API도 1을 반환
        assertThat(appNotificationFeedRepository.countByUserIdAndReadAtIsNull(me.getId())).isEqualTo(1);
        mockMvc.perform(get("/api/v1/me/app-notifications/unread-count")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("앱 알림: 일회성 피드가 실제 DB 에 저장되고 oneTime 타입으로 조회·필터링된다")
    void appNotificationsFeeds_one_time_type() throws Exception {
        // given: 실제 유저 + 일회성 피드 1건, 친구 피드 1건 (type ENUM 에 ONE_TIME 이 없으면 저장에서 실패한다)
        User me = saveUser();
        User target = saveUser();
        AppNotificationFeed oneTime = appNotificationFeedRepository.save(AppNotificationFeed.createProfileTarget(
                me.getId(), AppNotificationFeedType.ONE_TIME, "일회성 제목", "일회성 본문", target.getId(), null));
        appNotificationFeedRepository.save(
                feed(me.getId(), target.getId(), "새 친구", "친구 요청이 도착했어요", Instant.now().minus(Duration.ofHours(1))));

        // when & then: type=oneTime 으로 필터링하면 일회성 피드만 내려온다
        mockMvc.perform(get("/api/v1/me/app-notifications/feeds")
                        .param("type", "oneTime")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.appNotificationFeeds.length()").value(1))
                .andExpect(jsonPath("$.appNotificationFeeds[0].id").value(oneTime.getId().toString()))
                .andExpect(jsonPath("$.appNotificationFeeds[0].type").value("oneTime"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.kind").value("profile"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.userId").value(target.getId().toString()));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 실제 컨텍스트에서도 401을 반환한다")
    void without_auth_returns_401() throws Exception {
        // when & then: 인증 헤더 없이 내 상태 조회 시 401
        mockMvc.perform(get("/api/v1/me/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("성격 유형 조회: DB의 설문 답 문장을 실제 설문·유형 파일로 풀어 이름·축 단어·형용사/명사 문구·그림 URL을 내려준다")
    void personalityType_end_to_end() throws Exception {
        // given: 외향성 10·11번과 신경성 15·16·17번만 B, 나머지는 A로 답한 페르소나 + 설문 밖 관심사 1개
        User me = saveUser();
        Map<Integer, SurveyOptionName> answers = Map.of(
                10, SurveyOptionName.B,
                11, SurveyOptionName.B,
                15, SurveyOptionName.B,
                16, SurveyOptionName.B,
                17, SurveyOptionName.B);
        personaElementRepository.saveAll(surveyLoader.getAllQuestions().stream()
                .map(question -> PersonaElement.create(
                        me.getId(),
                        question.dimension(),
                        question.traitFor(answers.getOrDefault(question.id(), SurveyOptionName.A)),
                        Instant.now()))
                .toList());
        personaElementRepository.save(PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "등산", Instant.now()));
        flushAndClear();
        given(cloudFrontService.getPublicUrl(anyString()))
                .willAnswer(invocation -> "https://test.cloudfront.net/" + invocation.getArgument(0));

        // when: 실제 액세스 토큰으로 성격 유형 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/personality-type")
                .header("Authorization", bearer(me.getId())));

        // then: 차분·탐험·계획·다정·안정(01110)이라 탐구적인 수호자 + 설계 문서 2장의 탐구적인·수호자 문구 + 코드로 지은 그림 URL, 유형 코드는 내려가지 않음
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("탐구적인 수호자"))
                .andExpect(jsonPath("$.keywords.length()").value(5))
                .andExpect(jsonPath("$.keywords[0]").value("차분"))
                .andExpect(jsonPath("$.keywords[1]").value("탐험"))
                .andExpect(jsonPath("$.keywords[2]").value("계획"))
                .andExpect(jsonPath("$.keywords[3]").value("다정"))
                .andExpect(jsonPath("$.keywords[4]").value("안정"))
                .andExpect(jsonPath("$.adjective.tagline").value("혼자 깊이 파고들며 넓혀 가는"))
                .andExpect(jsonPath("$.adjective.description").value(
                        "사람이 많은 곳보다 조용한 시간에 힘을 얻어요. 궁금한 게 있으면 이것저것 찾아보고, 오래 곱씹으며 생각을 키워요."))
                .andExpect(jsonPath("$.noun.tagline").value("한결같이 곁을 지키는 사람"))
                .andExpect(jsonPath("$.noun.description").value(
                        "한번 맺은 관계와 약속은 끝까지 책임져요. 웬만한 일에는 흔들리지 않고, 변함없는 모습으로 주변에 든든한 버팀목이 되어 줘요."))
                .andExpect(jsonPath("$.imageUrl").value("https://test.cloudfront.net/personality-types/v1/110.webp"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("성격 유형 조회: 페르소나가 없는 유저는 422 PERSONA_NOT_FOUND를 받는다")
    void personalityType_without_persona_returns_422() throws Exception {
        // given: 설문을 하지 않아 페르소나가 없는 실제 유저
        User me = saveUser();

        // when: 실제 액세스 토큰으로 성격 유형 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/personality-type")
                .header("Authorization", bearer(me.getId())));

        // then: 422 PERSONA_NOT_FOUND
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.PERSONA_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("성격 유형 조회: 인증 헤더가 없으면 실제 컨텍스트에서도 401을 반환한다")
    void personalityType_without_auth_returns_401() throws Exception {
        // when & then: 인증 헤더 없이 성격 유형 조회 시 401
        mockMvc.perform(get("/api/v1/me/personality-type"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴 철회: 탈퇴 신청 상태에서 복구하면 DB의 withdrawalRequestedAt이 비워진다")
    void restore_end_to_end() throws Exception {
        // given: 탈퇴를 신청한 실제 유저
        User me = saveUser();
        me.requestWithdrawal(Duration.ofDays(30));
        userRepository.save(me);
        flushAndClear();

        // when: 실제 액세스 토큰으로 복구 API 호출
        mockMvc.perform(post("/api/v1/me/restore")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: DB에서 다시 읽어도 탈퇴 신청이 취소되어 있고, 상태 조회의 isDeleted도 false로 내려옴
        flushAndClear();
        User restored = userRepository.findById(me.getId()).orElseThrow();
        assertThat(restored.getWithdrawalRequestedAt()).isNull();
        assertThat(restored.getWithdrawalScheduledAt()).isNull();
        mockMvc.perform(get("/api/v1/me/status")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal.isDeleted").value(false))
                .andExpect(jsonPath("$.withdrawal.recoverableUntil").doesNotExist());
    }

    @Test
    @DisplayName("탈퇴 철회 멱등: 탈퇴 신청 이력이 없어도 200으로 응답한다")
    void restore_when_not_requested_is_idempotent() throws Exception {
        // given: 탈퇴 신청한 적 없는 실제 유저
        User me = saveUser();

        // when: 복구 API 호출
        var result = mockMvc.perform(post("/api/v1/me/restore")
                .header("Authorization", bearer(me.getId())));

        // then: 예외 없이 200 (멱등)
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("프로필 수정 화면 조회: 실제 유저의 개인정보(복호화 포함)가 응답된다")
    void profileEditView_end_to_end() throws Exception {
        // given: 실제 유저 저장 (암호화 컬럼이 조회 시 복호화되는지 함께 확인)
        User me = saveUser();

        // when: 실제 액세스 토큰으로 프로필 수정 화면 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/profile-edit-view")
                .header("Authorization", bearer(me.getId())));

        // then: 저장한 값이 그대로 복호화되어 응답되고, 사진이 없으면 profilePhoto는 null
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(me.getId().toString()))
                .andExpect(jsonPath("$.nickname").value(me.getNickname()))
                .andExpect(jsonPath("$.familyName").value(me.getFamilyName()))
                .andExpect(jsonPath("$.givenName").value(me.getGivenName()))
                .andExpect(jsonPath("$.affiliation").value(me.getAffiliation()))
                .andExpect(jsonPath("$.birthDate").value(me.getBirthDate()))
                .andExpect(jsonPath("$.profilePhoto").doesNotExist())
                .andExpect(jsonPath("$.interests").isEmpty());
    }

    @Test
    @DisplayName("프로필 수정: 소속을 바꾸면 DB의 평문·블라인드 인덱스가 함께 갱신되고 관심사는 요청한 목록으로 교체된다")
    void profile_patch_end_to_end() throws Exception {
        // given: 실제 유저 저장 + 기존 관심사 2개
        User me = saveUser();
        personaElementRepository.saveAll(List.of(
                PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "등산", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "영화", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.OPENNESS, "새로운 걸 좋아한다", Instant.now())));
        flushAndClear();

        // when: 실제 액세스 토큰으로 소속·관심사 변경 API 호출
        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"affiliation": "트윈리대학교", "interests": ["독서", "요리", "여행"]}
                                """))
                .andExpect(status().isOk());

        // then: 소속·해시가 갱신되고, INTEREST 차원만 새 목록으로 교체되며 다른 차원은 그대로 남음
        flushAndClear();
        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getAffiliation()).isEqualTo("트윈리대학교");
        assertThat(reloaded.getAffiliationHash()).isEqualTo(blindIndexHasher.hash("트윈리대학교"));
        assertThat(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(me.getId(), PersonaDimension.INTEREST))
                .extracting(PersonaElement::getExplanation)
                .containsExactly("독서", "요리", "여행");
        assertThat(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(me.getId(), PersonaDimension.OPENNESS))
                .extracting(PersonaElement::getExplanation)
                .containsExactly("새로운 걸 좋아한다");
    }

    @Test
    @DisplayName("약관 동의 철회: 선택 정책이면 동의 이력이 철회되어 조회에서 isGranted가 false가 된다")
    void revokeConsents_end_to_end() throws Exception {
        // given: 선택 정책에 이미 동의한 실제 유저
        clearSeededPolicyNames();
        User me = saveUser();
        PolicyName name = policyNameRepository.save(policyName("마케팅 수신 동의", "marketing"));
        Policy policy = policyRepository.save(policy(name.getId(), "1", false));
        agreementRepository.save(Agreement.create(me.getId(), policy.getId(), Instant.now()));
        flushAndClear();

        // when: 실제 액세스 토큰으로 동의 철회 API 호출
        mockMvc.perform(post("/api/v1/me/consents/revoke")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grants": [{"policyId": "marketing", "version": "1"}]}
                                """))
                .andExpect(status().isOk());

        // then: 유효한 동의 이력이 사라지고, 조회 응답의 isGranted도 false
        flushAndClear();
        assertThat(agreementRepository.findAllByUserIdAndRevokedAtIsNull(me.getId())).isEmpty();
        mockMvc.perform(get("/api/v1/me/consents")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[0].isGranted").value(false));
    }

    @Test
    @DisplayName("약관 동의 철회 실패: 필수 정책이면 403과 REQUIRED_POLICY_REVOKE_DENIED 코드를 반환한다")
    void revokeConsents_when_required_returns_403() throws Exception {
        // given: 필수 정책에 이미 동의한 실제 유저
        User me = saveUser();
        PolicyName name = policyNameRepository.save(policyName("서비스 이용약관", "terms_of_service"));
        Policy policy = policyRepository.save(policy(name.getId(), "1", true));
        agreementRepository.save(Agreement.create(me.getId(), policy.getId(), Instant.now()));
        flushAndClear();

        // when: 필수 정책에 대해 철회 API 호출
        var result = mockMvc.perform(post("/api/v1/me/consents/revoke")
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants": [{"policyId": "terms_of_service", "version": "1"}]}
                        """));

        // then: 403 + REQUIRED_POLICY_REVOKE_DENIED로 매핑되고 동의 이력은 유지됨
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUIRED_POLICY_REVOKE_DENIED.name()));
        flushAndClear();
        assertThat(agreementRepository.findAllByUserIdAndRevokedAtIsNull(me.getId())).hasSize(1);
    }

    @Test
    @DisplayName("경로 변수 enum은 @JsonProperty에도 상수명에도 없는 표기를 거부한다")
    void profileVisibility_rejects_undefined_path_variable() throws Exception {
        // given: 컨버터는 @JsonProperty만 비교하지만, 스프링의 TypeConverterDelegate가
        //        변환 실패 시 Enum.valueOf로 한 번 더 시도해 상수명(AFFILIATION_NUMBER)은 통과한다.
        //        둘 다 아닌 표기가 실제로 막히는지 고정한다.
        User me = saveUser();

        // when: 계약에 없는 상수명 표기로 공개 설정 변경 API 호출
        var result = mockMvc.perform(patch("/api/v1/me/profile/visibility-settings/{type}", "affiliation_number")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isVisible": true}
                                """));

        // then: 400 INVALID_REQUEST로 거부된다
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
    }

    @Test
    @DisplayName("경로 변수 enum은 API 문서에 노출되는 camelCase 표기(affiliationNumber)로 매핑된다")
    void profileVisibility_accepts_documented_camel_case_path_variable() throws Exception {
        // given: 경로 변수는 Jackson이 아니라 CaseInsensitiveEnumConverterFactory가 변환하므로
        //        @JsonProperty를 읽지 않으면 문서대로 보낸 값이 400이 된다
        User me = saveUser();

        // when: springdoc이 문서화하는 값 그대로 공개 설정 변경 API 호출
        mockMvc.perform(patch("/api/v1/me/profile/visibility-settings/{type}", "affiliationNumber")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isVisible": true}
                                """))
                .andExpect(status().isOk());

        // then: AFFILIATION_NUMBER로 매핑되어 실제로 반영된다
        flushAndClear();
        mockMvc.perform(get("/api/v1/me/profile/visibility-settings")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliationNumberVisible").value(true));
    }

    @Test
    @DisplayName("프로필 공개 설정: 변경 후 조회까지 관통하여 DB 공개 동의 행과 응답이 함께 바뀐다")
    void profileVisibility_end_to_end() throws Exception {
        // given: 아무것도 공개하지 않은 실제 유저 — 초기 조회는 모두 false
        User me = saveUser();
        mockMvc.perform(get("/api/v1/me/profile/visibility-settings")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliationVisible").value(false));

        // when: 소속을 공개로 변경
        mockMvc.perform(patch("/api/v1/me/profile/visibility-settings/{type}", DisclosureField.AFFILIATION.name())
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isVisible": true}
                                """))
                .andExpect(status().isOk());

        // then: 조회 응답이 true로 바뀌고, 다시 false로 되돌리면 응답도 되돌아감
        flushAndClear();
        mockMvc.perform(get("/api/v1/me/profile/visibility-settings")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliationVisible").value(true))
                .andExpect(jsonPath("$.affiliationNumberVisible").value(false));

        mockMvc.perform(patch("/api/v1/me/profile/visibility-settings/{type}", DisclosureField.AFFILIATION.name())
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isVisible": false}
                                """))
                .andExpect(status().isOk());

        flushAndClear();
        mockMvc.perform(get("/api/v1/me/profile/visibility-settings")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliationVisible").value(false));
    }

    @Test
    @DisplayName("앱 알림 전체 읽음: lastAppNotificationId 이하의 알림이 모두 읽음 처리된다")
    void appNotificationsReadAll_end_to_end() throws Exception {
        // given: 미읽음 알림 2건을 실제 DB에 저장
        User me = saveUser();
        User target = saveUser();
        AppNotificationFeed first = appNotificationFeedRepository.save(
                feed(me.getId(), target.getId(), "첫 알림", "본문1", Instant.now().minus(Duration.ofMinutes(10))));
        AppNotificationFeed second = appNotificationFeedRepository.save(
                feed(me.getId(), target.getId(), "둘째 알림", "본문2", Instant.now()));
        flushAndClear();

        // when: 두 번째 알림 id까지 전체 읽음 처리 API 호출
        mockMvc.perform(post("/api/v1/me/app-notifications/read-all")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastAppNotificationId": "%d"}
                                """.formatted(second.getId())))
                .andExpect(status().isOk());

        // then: 두 건 모두 읽음 처리되어 미읽음 개수가 0이 됨
        flushAndClear();
        assertThat(appNotificationFeedRepository.findById(first.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(appNotificationFeedRepository.findById(second.getId()).orElseThrow().getReadAt()).isNotNull();
        mockMvc.perform(get("/api/v1/me/app-notifications/unread-count")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("망설임 목록 조회: 오늘자 미답변 질문만 필터링되어 id 목록으로 응답된다")
    void hesitations_end_to_end() throws Exception {
        // given: 오늘자 PERSONA 질문 2건 — 하나는 이미 답변, 하나는 미답변
        User me = saveUser();
        Question unanswered = questionRepository.save(
                question(me.getId(), LocalDate.now(KST), "오늘 기분은?", List.of("좋아", "그냥")));
        Question answered = questionRepository.save(
                question(me.getId(), LocalDate.now(KST), "점심 먹었어?", List.of("응", "아니")));
        answered.answer("응");
        flushAndClear();

        // when: 오늘·미답변 조건으로 망설임 목록 API 호출
        var result = mockMvc.perform(get("/api/v1/me/hesitations")
                .param("duration", "TODAY")
                .param("status", "UNANSWERED")
                .header("Authorization", bearer(me.getId())));

        // then: 미답변 질문 id만 문자열로 응답되고 date는 오늘
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(LocalDate.now(KST).toString()))
                .andExpect(jsonPath("$.hesitationIds.length()").value(1))
                .andExpect(jsonPath("$.hesitationIds[0]").value(unanswered.getId().toString()));
    }

    @Test
    @DisplayName("망설임 답변: 선택지에 있는 답을 보내면 DB에 choice와 answeredAt이 기록된다")
    void hesitationsAnswer_end_to_end() throws Exception {
        // given: 오늘자 미답변 PERSONA 질문
        User me = saveUser();
        Question question = questionRepository.save(
                question(me.getId(), LocalDate.now(KST), "오늘 기분은?", List.of("좋아", "그냥")));
        flushAndClear();

        // when: 선택지 중 하나로 답변 API 호출
        mockMvc.perform(post("/api/v1/me/hesitations/{hesitationId}/answer", question.getId().toString())
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answer": "좋아", "skipped": false}
                                """))
                .andExpect(status().isOk());

        // then: DB에 답변 내용과 답변 시각이 기록됨
        flushAndClear();
        Question reloaded = questionRepository.findById(question.getId()).orElseThrow();
        assertThat(reloaded.getChoice()).isEqualTo("좋아");
        assertThat(reloaded.getAnsweredAt()).isNotNull();
    }

    @Test
    @DisplayName("망설임 답변 실패: 남의 질문이면 403과 NOT_HESITATION_OWNER 코드를 반환한다")
    void hesitationsAnswer_when_not_owner_returns_403() throws Exception {
        // given: 다른 유저 소유의 질문
        User me = saveUser();
        User other = saveUser();
        Question question = questionRepository.save(
                question(other.getId(), LocalDate.now(KST), "오늘 기분은?", List.of("좋아", "그냥")));
        flushAndClear();

        // when: 내 토큰으로 남의 질문에 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/me/hesitations/{hesitationId}/answer", question.getId().toString())
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answer": "좋아", "skipped": false}
                        """));

        // then: 403 + NOT_HESITATION_OWNER로 매핑됨
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_HESITATION_OWNER.name()));
    }

    @Test
    @DisplayName("망설임 답변 실패: 선택지에 없는 답이면 422와 HESITATION_ANSWER_NOT_IN_OPTIONS 코드를 반환한다")
    void hesitationsAnswer_when_answer_not_in_options_returns_422() throws Exception {
        // given: 선택지가 정해진 오늘자 질문
        User me = saveUser();
        Question question = questionRepository.save(
                question(me.getId(), LocalDate.now(KST), "오늘 기분은?", List.of("좋아", "그냥")));
        flushAndClear();

        // when: 선택지에 없는 답으로 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/me/hesitations/{hesitationId}/answer", question.getId().toString())
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answer": "몰라", "skipped": false}
                        """));

        // then: 422 + HESITATION_ANSWER_NOT_IN_OPTIONS로 매핑됨
        result.andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value(ErrorCode.HESITATION_ANSWER_NOT_IN_OPTIONS.name()));
    }

    @Test
    @DisplayName("프로필 사진 presign: 허용 content-type이면 유저 id 기반 key와 업로드 URL이 응답된다")
    void profilePhotoPresign_end_to_end() throws Exception {
        // given: 실제 유저 + S3 presign은 목으로 차단
        User me = saveUser();
        given(s3Service.presignPut(anyString(), anyString(), any())).willReturn("https://s3.example.com/upload");

        // when: 실제 액세스 토큰으로 presign API 호출
        var result = mockMvc.perform(post("/api/v1/me/profile/photo/presign")
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contentType": "image/png"}
                        """));

        // then: key가 profile/{userId}/ 접두사로 만들어지고 업로드 메타가 함께 응답됨
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.example.com/upload"))
                .andExpect(jsonPath("$.key").value(startsWith("profile/" + me.getId() + "/")))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.requiredHeaders.contentType").value("image/png"));
    }

    @Test
    @DisplayName("프로필 사진 commit: 업로드가 끝난 key면 photos 행이 생성된다")
    void profilePhotoCommit_end_to_end() throws Exception {
        // given: 실제 유저 + S3 업로드 완료·CloudFront 서명 URL을 목으로 대체
        User me = saveUser();
        String key = "profile/%d/photo-1".formatted(me.getId());
        given(s3Service.contentLength(key)).willReturn(Optional.of(1024L));
        given(cloudFrontService.getSignedUrl(key)).willReturn("https://cdn.example.com/" + key);

        // when: 실제 액세스 토큰으로 commit API 호출
        mockMvc.perform(post("/api/v1/me/profile/photo/commit")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "%s", "position": {"startPos": {"x": 1, "y": 2}, "width": 300, "height": 400}}
                                """.formatted(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://cdn.example.com/" + key));

        // then: DB에 프로필 사진 행이 key·좌표와 함께 생성됨
        flushAndClear();
        Photo photo = photoRepository.findByUserIdAndType(me.getId(), PhotoType.PROFILE).orElseThrow();
        assertThat(photo.getKey()).isEqualTo(key);
        assertThat(photo.getWidth()).isEqualTo(300);
    }

    /**
     * 베이스 클래스의 @Transactional을 끈다.
     * 켜둔 채로 두면 서비스가 테스트의 트랜잭션에 편승해 더티 체킹이 성공해버려서,
     * 운영에서 실제로 발생하는 "주변 트랜잭션 없음" 상황이 재현되지 않는다.
     * 롤백도 함께 사라지므로 생성한 행은 직접 정리한다.
     */
    @Test
    @DisplayName("프로필 사진 commit: 이미 사진이 있으면 새 key·좌표로 기존 행이 갱신된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void profilePhotoCommit_replaces_existing_photo() throws Exception {
        // given: 이미 프로필 사진이 등록된 유저
        User me = saveUser();
        String oldKey = "profile/%d/photo-old".formatted(me.getId());
        String newKey = "profile/%d/photo-new".formatted(me.getId());
        Photo existing = photoRepository.save(
                Photo.create(me.getId(), PhotoType.PROFILE, oldKey, 1, 2, 300, 400, Instant.now()));
        given(s3Service.contentLength(newKey)).willReturn(Optional.of(1024L));
        given(cloudFrontService.getSignedUrl(newKey)).willReturn("https://cdn.example.com/" + newKey);

        try {
            // when: 새 key로 다시 commit API 호출
            mockMvc.perform(post("/api/v1/me/profile/photo/commit")
                            .header("Authorization", bearer(me.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"key": "%s", "position": {"startPos": {"x": 10, "y": 20}, "width": 500, "height": 600}}
                                    """.formatted(newKey)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.photoUrl").value("https://cdn.example.com/" + newKey));

            // then: 새 행이 생기지 않고 기존 행의 key·좌표가 실제로 DB에 반영됨
            Photo photo = photoRepository.findByUserIdAndType(me.getId(), PhotoType.PROFILE).orElseThrow();
            assertThat(photo.getId()).isEqualTo(existing.getId());
            assertThat(photo.getKey()).isEqualTo(newKey);
            assertThat(photo.getXPos()).isEqualTo(10);
            assertThat(photo.getWidth()).isEqualTo(500);
        } finally {
            photoRepository.deleteAll(photoRepository.findAllByUserIdInAndType(List.of(me.getId()), PhotoType.PROFILE));
            userRepository.deleteById(me.getId());
        }
    }

    // ---------------------------------------------------------------- 픽스처

    /** questions는 생성 팩토리가 없어 리플렉션으로 픽스처를 만든다. (PERSONA 타입 = 망설임) */
    private Question question(Long userId, LocalDate date, String text, List<String> options) {
        Question question = BeanUtils.instantiateClass(Question.class);
        ReflectionTestUtils.setField(question, "userId", userId);
        ReflectionTestUtils.setField(question, "date", date);
        ReflectionTestUtils.setField(question, "version", "v1");
        ReflectionTestUtils.setField(question, "time", date.atTime(21, 0));
        ReflectionTestUtils.setField(question, "type", QuestionType.PERSONA);
        ReflectionTestUtils.setField(question, "text", text);
        ReflectionTestUtils.setField(question, "options", options);
        ReflectionTestUtils.setField(question, "isSkipped", false);
        ReflectionTestUtils.setField(question, "createdAt", Instant.now());
        return question;
    }

    /** 영속성 컨텍스트를 비워 실제 DB 상태를 다시 읽도록 한다. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }


    private PolicyName policyName(String name, String identifier) {
        PolicyName policyName = BeanUtils.instantiateClass(PolicyName.class);
        ReflectionTestUtils.setField(policyName, "kind", PolicyKind.ONBOARDING);
        ReflectionTestUtils.setField(policyName, "name", name);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "requiresAgreement", true);
        ReflectionTestUtils.setField(policyName, "isDeprecated", false);
        return policyName;
    }

    private Policy policy(Long policyNameId, String version, Boolean isRequired) {
        Policy policy = BeanUtils.instantiateClass(Policy.class);
        ReflectionTestUtils.setField(policy, "policyNameId", policyNameId);
        ReflectionTestUtils.setField(policy, "version", version);
        ReflectionTestUtils.setField(policy, "isRequired", isRequired);
        ReflectionTestUtils.setField(policy, "effectiveAt", Instant.now().minus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(policy, "createdAt", Instant.now());
        return policy;
    }

    private AppNotificationFeed feed(Long userId, Long targetUserId, String title, String body, Instant createdAt) {
        AppNotificationFeed feed = BeanUtils.instantiateClass(AppNotificationFeed.class);
        ReflectionTestUtils.setField(feed, "userId", userId);
        ReflectionTestUtils.setField(feed, "title", title);
        ReflectionTestUtils.setField(feed, "body", body);
        ReflectionTestUtils.setField(feed, "type", AppNotificationFeedType.FRIEND);
        ReflectionTestUtils.setField(feed, "targetKind", AppNotificationFeedTargetType.PROFILE);
        ReflectionTestUtils.setField(feed, "targetUserId", targetUserId);
        ReflectionTestUtils.setField(feed, "createdAt", createdAt);
        return feed;
    }

    @Test
    @DisplayName("약관 동의 철회: 존재하지 않는 정책 버전이면 404 POLICY_NOT_FOUND를 반환한다 (등록 API와 대칭)")
    void revokeConsents_unknown_policy_returns_404() throws Exception {
        // given: 실제 유저
        User me = saveUser();

        // when & then: 카탈로그에 없는 (policyId, version)이므로 조용히 200이 아니라 404
        mockMvc.perform(post("/api/v1/me/consents/revoke")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grants":[{"policyId":"없는정책","version":"99"}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    @DisplayName("내 프로필 조회: 실제 유저·사진·페르소나·만난 사람까지 관통해 성+이름과 요약·카운트가 내려온다")
    void myProfile_success_end_to_end() throws Exception {
        // given: 실제 유저 본인과 만난 상대 2명(FK 때문에 상대도 실제 저장)
        User me = saveUser();
        User friend = saveUser();
        User acquaintance = saveUser();

        // given: 프로필 사진 + CloudFront 서명 URL 목
        String key = "profile/%d/photo-1".formatted(me.getId());
        photoRepository.save(Photo.create(me.getId(), PhotoType.PROFILE, key, 10, 20, 100, 200, Instant.now()));
        given(cloudFrontService.getSignedUrl(key)).willReturn("https://cdn.example.com/" + key);

        // given: 요약에 쓰이는 차원 4개(앞 3개만 요약됨) + 관심사 2개
        personaElementRepository.saveAll(List.of(
                PersonaElement.create(me.getId(), PersonaDimension.OPENNESS, "새로운 걸 좋아한다", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.CONSCIENTIOUSNESS, "약속은 꼭 지킨다", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.EXTRAVERSION, "먼저 말을 건다", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.AGREEABLENESS, "잘 맞춰준다", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "등산", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "영화", Instant.now())));

        // given: 만난 기록 2건 — 하나는 내가 userA, 하나는 내가 userB로 정렬되도록 양방향 저장
        encounterRepository.save(Encounter.create(me.getId(), friend.getId()));
        encounterRepository.save(Encounter.create(acquaintance.getId(), me.getId()));

        // given: friend는 어제 지인이었다가 오늘 친구로 올라섰고, acquaintance는 지인에 머문다
        LocalDate today = LocalDate.now(KST);
        relationshipRepository.saveAll(List.of(
                relationship(me.getId(), friend.getId(), today.minusDays(1), 10),
                relationship(me.getId(), friend.getId(), today, 75),
                relationship(me.getId(), acquaintance.getId(), today, 10)));

        // when: 본인의 실제 액세스 토큰으로 내 프로필 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/profile")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + 본인 화면이므로 성+이름, 페르소나는 앞 3개 요약, 최신 친밀도 기준으로 지인은 친구 수에서 빠진다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(me.getId().toString()))
                .andExpect(jsonPath("$.userName").value(me.getFamilyName() + me.getGivenName()))
                .andExpect(jsonPath("$.profilePhoto.key").value(key))
                .andExpect(jsonPath("$.profilePhoto.photoUrl").value("https://cdn.example.com/" + key))
                .andExpect(jsonPath("$.profilePhoto.position.startPos.x").value(10))
                .andExpect(jsonPath("$.profilePhoto.position.width").value(100))
                .andExpect(jsonPath("$.persona").value("새로운 걸 좋아한다, 약속은 꼭 지킨다, 먼저 말을 건다..."))
                .andExpect(jsonPath("$.interests[0]").value("등산"))
                .andExpect(jsonPath("$.interests[1]").value("영화"))
                .andExpect(jsonPath("$.encounteredPeopleCount").value(2))
                .andExpect(jsonPath("$.encounteredFriendCount").value(1));
    }

    @Test
    @DisplayName("내 프로필 조회: 갱신 시각이 아직 오지 않은 관계 기록은 친구 수 계산에 쓰지 않는다")
    void myProfile_friend_count_ignores_records_after_now() throws Exception {
        // given: 만난 상대 1명, 어제는 지인(10)이고 두 시간 뒤 친구(80)로 올라설 예정
        User me = saveUser();
        User partner = saveUser();
        encounterRepository.save(Encounter.create(me.getId(), partner.getId()));
        LocalDateTime now = KstTimes.now();
        relationshipRepository.saveAll(List.of(
                Relationship.create(me.getId(), now.minusDays(1).toLocalDate(), "v1", partner.getId(), 10, "model", now.minusDays(1), null),
                Relationship.create(me.getId(), now.plusHours(2).toLocalDate(), "v1", partner.getId(), 80, "model", now.plusHours(2), null)));

        // when: 내 프로필 조회
        var result = mockMvc.perform(get("/api/v1/me/profile")
                .header("Authorization", bearer(me.getId())));

        // then: 아직 오지 않은 기록은 무시되어 친구 수는 0이다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.encounteredPeopleCount").value(1))
                .andExpect(jsonPath("$.encounteredFriendCount").value(0));
    }

    @Test
    @DisplayName("내 프로필 조회: 인증 헤더가 없으면 401을 반환한다")
    void myProfile_without_auth_returns_401() throws Exception {
        // when & then: 인증 헤더 없이 호출하면 필터 단계에서 막힌다
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 프로필 조회 v2: SUMMARY 요소가 있어도 persona는 내려가지 않고 나머지는 v1과 같다")
    void myProfileV2_success_end_to_end() throws Exception {
        // given: AI 채팅 요약(SUMMARY)과 관심사 2개를 가진 유저
        User me = saveUser();
        personaElementRepository.saveAll(List.of(
                PersonaElement.create(me.getId(), PersonaDimension.SUMMARY, "주말마다 북한산에 오르는 사람", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "등산", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "영화", Instant.now())));

        // when: 본인의 실제 액세스 토큰으로 내 프로필 조회 v2 API 호출
        var result = mockMvc.perform(get("/api/v2/me/profile")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + persona 필드는 없고 이름·관심사·카운트는 그대로
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(me.getId().toString()))
                .andExpect(jsonPath("$.userName").value(me.getFamilyName() + me.getGivenName()))
                .andExpect(jsonPath("$.profilePhoto").isEmpty())
                .andExpect(jsonPath("$.persona").doesNotExist())
                .andExpect(jsonPath("$.interests[0]").value("등산"))
                .andExpect(jsonPath("$.interests[1]").value("영화"))
                .andExpect(jsonPath("$.encounteredPeopleCount").value(0))
                .andExpect(jsonPath("$.encounteredFriendCount").value(0));
    }

    @Test
    @DisplayName("내 프로필 조회 v2: 인증 헤더가 없으면 401을 반환한다")
    void myProfileV2_without_auth_returns_401() throws Exception {
        // when & then: v2 경로도 인증 헤더 없이 호출하면 필터 단계에서 막힌다
        mockMvc.perform(get("/api/v2/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    private Relationship relationship(Long userId, Long partnerUserId, LocalDate date, int intimacy) {
        return Relationship.create(userId, date, "v1", partnerUserId, intimacy, "model", date.atStartOfDay(), null);
    }

    /**
     * 422로 실패한 요청이 롤백되어 마지막 문항 답도 남지 않는지 확인하려면
     * 요청이 테스트 트랜잭션에 합류하지 않고 스스로 커밋·롤백해야 하므로 베이스 클래스의 @Transactional을 끈다.
     */
    @Test
    @DisplayName("설문 답변: 응답하지 않은 문항이 있는 채로 마지막 문항에 답하면 422이고 롤백되어 마지막 문항 답도 저장되지 않는다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void surveyAnswer_last_question_with_missing_answers_rolls_back() throws Exception {
        // given: 아무 문항에도 답하지 않은 실제 유저
        User me = saveUser();
        Integer lastQuestionId = surveyLoader.getAllQuestions().getLast().id();

        try {
            // when: 마지막 문항에만 답변
            mockMvc.perform(post("/api/v1/me/survey-answers")
                            .header("Authorization", bearer(me.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"answer": {"qId": %d, "optionName": "A"}}
                                    """.formatted(lastQuestionId)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SURVEY_ANSWERS_INCOMPLETE.name()));

            // then: 마지막 문항 답과 페르소나 요소 모두 남지 않음
            assertThat(userSurveyAnswerRepository.findAllByUserId(me.getId())).isEmpty();
            assertThat(personaElementRepository.findAllByUserIdOrderByIdAsc(me.getId())).isEmpty();
        } finally {
            userSurveyAnswerRepository.deleteAll(userSurveyAnswerRepository.findAllByUserId(me.getId()));
            userRepository.deleteById(me.getId());
        }
    }

    @Test
    @DisplayName("설문 답변: 같은 문항에 다시 답하면 행을 새로 만들지 않고 기존 행의 선택지만 바뀐다")
    void surveyAnswer_resubmit_updates_existing_row() throws Exception {
        // given: 실제 유저 + 실제 설문 파일에 존재하는 문항(qId=8)에 A로 답한 상태
        User me = saveUser();
        mockMvc.perform(post("/api/v1/me/survey-answers")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answer": {"qId": 8, "optionName": "A"}}
                                """))
                .andExpect(status().isOk());

        // when: 같은 문항에 B로 다시 답변
        mockMvc.perform(post("/api/v1/me/survey-answers")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answer": {"qId": 8, "optionName": "B"}}
                                """))
                .andExpect(status().isOk());

        // then: 유니크 제약 위반 없이 행은 1개로 유지되고 선택지만 B로 바뀌며, 마지막 문항이 아니므로 페르소나 요소는 없음
        entityManager.flush();
        entityManager.clear();
        assertThat(userSurveyAnswerRepository.findAllByUserId(me.getId()))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getQuestionId()).isEqualTo(8);
                    assertThat(saved.getOptionName()).isEqualTo(SurveyOptionName.B);
                });
        assertThat(personaElementRepository.findAllByUserIdOrderByIdAsc(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("관심사 선택: 실제 유저·JWT 인증·DB까지 관통하여 INTEREST 차원만 새 목록으로 교체되고 다른 차원은 그대로 남는다")
    void interests_end_to_end() throws Exception {
        // given: 실제 유저 저장 + 기존 관심사 1개와 설문 차원 요소 1개
        User me = saveUser();
        personaElementRepository.saveAll(List.of(
                PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "등산", Instant.now()),
                PersonaElement.create(me.getId(), PersonaDimension.OPENNESS, "새로운 걸 좋아한다", Instant.now())));
        flushAndClear();

        // when: 실제 액세스 토큰으로 관심사 선택 API 호출
        mockMvc.perform(post("/api/v1/me/interests")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interests": ["독서", "요리"]}
                                """))
                .andExpect(status().isOk());

        // then: INTEREST 차원만 요청 순서대로 교체되고 OPENNESS 차원은 유지됨
        flushAndClear();
        assertThat(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(me.getId(), PersonaDimension.INTEREST))
                .extracting(PersonaElement::getExplanation)
                .containsExactly("독서", "요리");
        assertThat(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(me.getId(), PersonaDimension.OPENNESS))
                .extracting(PersonaElement::getExplanation)
                .containsExactly("새로운 걸 좋아한다");
    }

    @Test
    @DisplayName("AI 대화: 시작 후 0번 턴에 답하면 대화 3건·DETAIL 요소가 저장되고 다음 질문이 응답된다")
    void aiChat_start_and_message_end_to_end() throws Exception {
        // given: 실제 유저 + Bedrock이 첫 질문·다음 질문을 차례로 반환
        User me = saveUser();
        given(bedrockService.converse(anyString())).willReturn("등산은 어디로 자주 가?", "북한산 어느 코스로 올라가?");

        // when: AI 대화 시작 후 0번 턴에 답변
        mockMvc.perform(post("/api/v1/me/ai-chat/start")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("등산은 어디로 자주 가?"))
                .andExpect(jsonPath("$.turnIndex").value(0));
        mockMvc.perform(post("/api/v1/me/ai-chat/messages")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "북한산", "turnIndex": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("북한산 어느 코스로 올라가?"))
                .andExpect(jsonPath("$.turnIndex").value(1))
                .andExpect(jsonPath("$.isEnd").value(false));

        // then: AI(0)·USER(0)·AI(1) 대화 3건과 질문+답변 DETAIL 요소 1건이 저장됨
        flushAndClear();
        assertThat(aiChatRepository.findByUserIdOrderByTurnIndexAscSenderDesc(me.getId()))
                .extracting(AiChat::getSender, AiChat::getTurnIndex, AiChat::getMessage)
                .containsExactly(
                        tuple(AiChatSender.AI, 0, "등산은 어디로 자주 가?"),
                        tuple(AiChatSender.USER, 0, "북한산"),
                        tuple(AiChatSender.AI, 1, "북한산 어느 코스로 올라가?"));
        assertThat(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(me.getId(), PersonaDimension.DETAIL))
                .extracting(PersonaElement::getExplanation)
                .containsExactly("등산은 어디로 자주 가?: 북한산");
    }

    @Test
    @DisplayName("AI 대화 답변 멱등: 같은 턴에 두 번 답해도 이미 생성된 다음 질문을 그대로 돌려주고 모델은 한 번만 불린다")
    void aiChatMessage_is_idempotent() throws Exception {
        // given: 실제 유저 + 0번 턴 AI 질문이 저장된 상태
        User me = saveUser();
        aiChatRepository.save(AiChat.create(me.getId(), AiChatSender.AI, "요즘 뭐에 빠져 있어?", 0, Instant.now()));
        given(bedrockService.converse(anyString())).willReturn("그거 언제부터 좋아했어?");
        flushAndClear();

        // when: 같은 turnIndex로 답변 API를 두 번 호출 (네트워크 재시도 상황)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/me/ai-chat/messages")
                            .header("Authorization", bearer(me.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "요즘 등산에 빠졌어", "turnIndex": 0}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("그거 언제부터 좋아했어?"))
                    .andExpect(jsonPath("$.turnIndex").value(1));
        }

        // then: 대화 3건·DETAIL 요소 1건만 남고 모델도 한 번만 불림
        flushAndClear();
        assertThat(aiChatRepository.findByUserIdOrderByTurnIndexAscSenderDesc(me.getId())).hasSize(3);
        assertThat(personaElementRepository.findAllByUserIdAndDimensionOrderByIdAsc(me.getId(), PersonaDimension.DETAIL)).hasSize(1);
        then(bedrockService).should(times(1)).converse(anyString());
    }

    @Test
    @DisplayName("AI 대화 종료: 처음 호출하면 종료 시각이 기록되고, 다시 호출해도 처음 기록한 시각이 유지된다")
    void aiChatComplete_end_to_end_is_idempotent() throws Exception {
        // given: 실제 유저 + AI 대화를 한 번 종료한 상태
        User me = saveUser();
        mockMvc.perform(post("/api/v1/me/ai-chat/complete")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());
        flushAndClear();
        Instant firstCompletedAt = userRepository.findById(me.getId()).orElseThrow().getAiChatCompletedAt();

        // when: 같은 유저가 다시 종료 API 호출
        mockMvc.perform(post("/api/v1/me/ai-chat/complete")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: 첫 호출에서 종료 시각이 기록됐고, 재호출 후에도 그 시각이 그대로 유지됨
        flushAndClear();
        assertThat(firstCompletedAt).isNotNull();
        assertThat(userRepository.findById(me.getId()).orElseThrow().getAiChatCompletedAt()).isEqualTo(firstCompletedAt);
    }

    @Test
    @DisplayName("내 상태 조회: 아무것도 입력하지 않은 유저는 페르소나 입력 상태가 모두 false다")
    void status_persona_nothing_completed_end_to_end() throws Exception {
        // given: 설문·관심사·AI 대화를 하지 않은 실제 유저
        User me = saveUser();

        // when & then: 페르소나 입력 상태가 모두 false
        mockMvc.perform(get("/api/v1/me/status")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona.isSurveyCompleted").value(false))
                .andExpect(jsonPath("$.persona.isInterestsCompleted").value(false))
                .andExpect(jsonPath("$.persona.isAiChatCompleted").value(false));
    }

    @Test
    @DisplayName("내 상태 조회: 설문 답변이 한 문항 모자라면 isSurveyCompleted는 false다")
    void status_survey_incomplete_end_to_end() throws Exception {
        // given: 실제 설문 파일의 마지막 문항을 뺀 나머지에만 답한 실제 유저
        User me = saveUser();
        List<SurveyQuestion> questions = surveyLoader.getAllQuestions();
        questions.subList(0, questions.size() - 1).forEach(question ->
                userSurveyAnswerRepository.upsert(me.getId(), question.id(), SurveyOptionName.A.name()));
        flushAndClear();

        // when & then: 설문 미완료
        mockMvc.perform(get("/api/v1/me/status")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona.isSurveyCompleted").value(false));
    }

    @Test
    @DisplayName("내 상태 조회: 설문 전 문항에 답했고 관심사 요소가 있으며 AI 대화 종료 API를 호출했으면 페르소나 입력 상태가 모두 true다")
    void status_persona_all_completed_end_to_end() throws Exception {
        // given: 실제 설문 파일의 전 문항에 답했고 관심사 요소가 있는 실제 유저가 AI 대화 종료 API까지 호출
        User me = saveUser();
        surveyLoader.getAllQuestions().forEach(question ->
                userSurveyAnswerRepository.upsert(me.getId(), question.id(), SurveyOptionName.A.name()));
        personaElementRepository.save(PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "등산", Instant.now()));
        mockMvc.perform(post("/api/v1/me/ai-chat/complete")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());
        flushAndClear();

        // when & then: 페르소나 입력 상태가 모두 true
        mockMvc.perform(get("/api/v1/me/status")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona.isSurveyCompleted").value(true))
                .andExpect(jsonPath("$.persona.isInterestsCompleted").value(true))
                .andExpect(jsonPath("$.persona.isAiChatCompleted").value(true));
    }

    @Test
    @DisplayName("내 정보 조회: 저장된 성별을 소문자 문자열로 내려준다")
    void info_end_to_end() throws Exception {
        // given: 성별이 저장된 실제 유저
        User me = saveUser();

        // when & then: 저장된 성별이 소문자 문자열로 내려감
        mockMvc.perform(get("/api/v1/me/info")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("male"));
    }

    @Test
    @DisplayName("설문 문항 목록: 로그인 유저에게 실제 설문 파일의 전 문항을 순서대로 내려준다")
    void surveyQuestions_end_to_end() throws Exception {
        // given: 실제 유저 + 실제 설문 파일의 문항들
        User me = saveUser();
        List<SurveyQuestion> questions = surveyLoader.getAllQuestions();

        // when & then: 문항 수·첫 문항 id가 설문 파일과 일치
        mockMvc.perform(get("/api/v1/me/survey-questions")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(questions.size()))
                .andExpect(jsonPath("$[0].id").value(questions.getFirst().id()))
                .andExpect(jsonPath("$[0].options.A").isNotEmpty());
    }

    @Test
    @DisplayName("성향 문항 목록: 로그인 유저에게 실제 성향 문항 파일의 전 문항을 순서대로, id는 문자열로 내려준다")
    void tendencyQuestions_end_to_end() throws Exception {
        // given: 실제 유저 + 실제 성향 문항 파일의 문항들
        User me = saveUser();
        List<TendencyQuestion> questions = tendencyLoader.getAllQuestions();
        TendencyQuestion firstQuestion = questions.getFirst();
        TendencyOption firstOption = firstQuestion.options().getFirst();

        // when & then: 문항 수·첫 문항·첫 선택지가 파일과 일치하고 id는 문자열
        mockMvc.perform(get("/api/v1/me/tendency-questions")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(questions.size()))
                .andExpect(jsonPath("$.questions[0].id").value(firstQuestion.id().toString()))
                .andExpect(jsonPath("$.questions[0].text").value(firstQuestion.text()))
                .andExpect(jsonPath("$.questions[0].options.length()").value(firstQuestion.options().size()))
                .andExpect(jsonPath("$.questions[0].options[0].id").value(firstOption.id().toString()))
                .andExpect(jsonPath("$.questions[0].options[0].label").value(firstOption.label()));
    }

    @Test
    @DisplayName("성향 문항 목록: 인증 헤더가 없으면 401")
    void tendencyQuestions_without_auth_returns_401() throws Exception {
        // when & then: 인증 헤더 없이 호출하면 401
        mockMvc.perform(get("/api/v1/me/tendency-questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("성향 응답 제출: 실제 유저·JWT 인증·DB까지 관통하여 user_tendency_answers 행이 생성된다")
    void submitTendencyAnswer_end_to_end() throws Exception {
        // given: 실제 유저 + 실제 파일의 첫 문항과 그 마지막 선택지
        User me = saveUser();
        TendencyQuestion question = tendencyLoader.getAllQuestions().getFirst();
        Long optionId = question.options().getLast().id();

        // when: 문항 id는 경로에, 선택지 id는 문자열로 본문에 담아 제출
        mockMvc.perform(put("/api/v1/me/tendency-answers/{questionId}", question.id().toString())
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionId": "%d"}
                                """.formatted(optionId)))
                .andExpect(status().isOk());

        // then: DB에 유저·문항·선택지 id로 답변 1행이 저장됨
        flushAndClear();
        assertThat(tendencyAnswersOf(me))
                .extracting(UserTendencyAnswer::getQuestionId, UserTendencyAnswer::getOptionId)
                .containsExactly(tuple(question.id(), optionId));
    }

    @Test
    @DisplayName("성향 응답 제출: 같은 문항에 다시 답하면 행을 새로 만들지 않고 선택지만 바뀌며, 다른 유저의 답은 그대로다")
    void submitTendencyAnswer_resubmit_updates_existing_row() throws Exception {
        // given: 같은 문항에 나는 첫 선택지로, 다른 유저는 마지막 선택지로 답한 상태
        User me = saveUser();
        User other = saveUser();
        TendencyQuestion question = tendencyLoader.getAllQuestions().getFirst();
        Long firstOptionId = question.options().getFirst().id();
        Long lastOptionId = question.options().getLast().id();
        submitTendencyAnswer(me, question.id(), firstOptionId);
        submitTendencyAnswer(other, question.id(), lastOptionId);

        // when: 내가 같은 문항에 마지막 선택지로 다시 답변
        submitTendencyAnswer(me, question.id(), lastOptionId);

        // then: 유니크 제약 위반 없이 내 행은 1개로 유지되고 선택지만 바뀌며, 다른 유저의 행은 영향받지 않음
        flushAndClear();
        assertThat(tendencyAnswersOf(me))
                .extracting(UserTendencyAnswer::getQuestionId, UserTendencyAnswer::getOptionId)
                .containsExactly(tuple(question.id(), lastOptionId));
        assertThat(tendencyAnswersOf(other))
                .extracting(UserTendencyAnswer::getQuestionId, UserTendencyAnswer::getOptionId)
                .containsExactly(tuple(question.id(), lastOptionId));
    }

    @Test
    @DisplayName("성향 응답 제출: 파일에 없는 문항이면 404 TENDENCY_QUESTION_NOT_FOUND이고 저장되지 않는다")
    void submitTendencyAnswer_with_unknown_question_returns_404() throws Exception {
        // given: 실제 유저 + 파일의 어떤 문항 id보다 큰 id
        User me = saveUser();
        long unknownQuestionId = tendencyLoader.getAllQuestions().stream()
                .mapToLong(TendencyQuestion::id)
                .max()
                .orElseThrow() + 1;

        // when & then: 없는 문항에 답하면 404
        mockMvc.perform(put("/api/v1/me/tendency-answers/{questionId}", String.valueOf(unknownQuestionId))
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionId": "1"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENDENCY_QUESTION_NOT_FOUND.name()));

        // then: 답변이 저장되지 않음
        assertThat(tendencyAnswersOf(me)).isEmpty();
    }

    @Test
    @DisplayName("성향 응답 제출: 그 문항에 없는 선택지면 422 TENDENCY_OPTION_NOT_IN_QUESTION이고 저장되지 않는다")
    void submitTendencyAnswer_with_option_not_in_question_returns_422() throws Exception {
        // given: 실제 유저 + 실제 파일의 첫 문항과, 그 문항의 어떤 선택지 id보다 큰 id
        User me = saveUser();
        TendencyQuestion question = tendencyLoader.getAllQuestions().getFirst();
        long unknownOptionId = question.options().stream()
                .mapToLong(TendencyOption::id)
                .max()
                .orElseThrow() + 1;

        // when & then: 문항에 없는 선택지로 답하면 422
        mockMvc.perform(put("/api/v1/me/tendency-answers/{questionId}", question.id().toString())
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionId": "%d"}
                                """.formatted(unknownOptionId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENDENCY_OPTION_NOT_IN_QUESTION.name()));

        // then: 답변이 저장되지 않음
        assertThat(tendencyAnswersOf(me)).isEmpty();
    }

    @Test
    @DisplayName("성향 응답 제출: 인증 헤더가 없으면 401")
    void submitTendencyAnswer_without_auth_returns_401() throws Exception {
        // given: 실제 파일의 첫 문항
        TendencyQuestion question = tendencyLoader.getAllQuestions().getFirst();

        // when & then: 인증 헤더 없이 호출하면 401
        mockMvc.perform(put("/api/v1/me/tendency-answers/{questionId}", question.id().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionId": "%d"}
                                """.formatted(question.options().getFirst().id())))
                .andExpect(status().isUnauthorized());
    }

    private void submitTendencyAnswer(User user, Long questionId, Long optionId) throws Exception {
        mockMvc.perform(put("/api/v1/me/tendency-answers/{questionId}", questionId.toString())
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionId": "%d"}
                                """.formatted(optionId)))
                .andExpect(status().isOk());
    }

    private List<UserTendencyAnswer> tendencyAnswersOf(User user) {
        return userTendencyAnswerRepository.findAll().stream()
                .filter(answer -> answer.getUserId().equals(user.getId()))
                .toList();
    }

    @Test
    @DisplayName("피드백 선택지 조회: 실제 선택지 파일의 해당 type 목록을 순서 그대로 문자열 id로 내려준다")
    void feedbackOptions_returns_options_from_file() throws Exception {
        // given: 실제 유저 + 실제 파일의 탈퇴 사유 선택지
        User me = saveUser();
        List<FeedbackOption> options = feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL);

        // when: 실제 액세스 토큰으로 탈퇴 사유 선택지 조회
        var result = mockMvc.perform(get("/api/v1/me/feedback/options")
                .param("type", "withdrawal")
                .header("Authorization", bearer(me.getId())));

        // then: 200 + 파일 순서 그대로 문자열 id·문구
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(options.size()))
                .andExpect(jsonPath("$.options[0].id").value(options.getFirst().id().toString()))
                .andExpect(jsonPath("$.options[0].label").value(options.getFirst().label()));
    }

    @Test
    @DisplayName("피드백 선택지 조회: 탈퇴 사유와 건의하기 선택지 id는 서로 겹치지 않는다")
    void feedbackOptions_ids_are_unique_across_types() {
        // given: 실제 파일의 두 type 선택지 id
        List<Long> withdrawalIds = feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL).stream().map(FeedbackOption::id).toList();
        List<Long> suggestionIds = feedbackOptionLoader.getOptions(FeedbackType.SUGGESTION).stream().map(FeedbackOption::id).toList();

        // then: 겹치는 id가 없음
        assertThat(withdrawalIds).doesNotContainAnyElementsOf(suggestionIds);
    }

    @Test
    @DisplayName("피드백 선택지 조회: 인증 헤더가 없으면 401")
    void feedbackOptions_without_auth_returns_401() throws Exception {
        // when & then: 인증 헤더 없이 호출하면 401
        mockMvc.perform(get("/api/v1/me/feedback/options")
                        .param("type", "withdrawal"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("피드백 보내기: 목록에 있는 선택지만 한 번씩, 공백을 지운 내용과 앱 헤더가 실제 DB에 저장된다")
    void sendFeedback_saves_feedback_end_to_end() throws Exception {
        // given: 실제 유저 + 실제 파일의 건의하기 첫 선택지와 탈퇴 사유 첫 선택지
        User me = saveUser();
        Long suggestionOptionId = feedbackOptionLoader.getOptions(FeedbackType.SUGGESTION).getFirst().id();
        Long withdrawalOptionId = feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL).getFirst().id();

        // when: 중복 id, 다른 type id, 없는 id를 섞어 건의하기 전송
        mockMvc.perform(post("/api/v1/me/feedback")
                        .header("Authorization", bearer(me.getId()))
                        .header("X-App-Platform", "android")
                        .header("X-App-Version", "1.4.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"suggestion","optionIds":["%d","%d","%d","999999"],"detail":"  알림이 늦게 와요  "}
                                """.formatted(suggestionOptionId, suggestionOptionId, withdrawalOptionId)))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // then: 공백을 지운 내용과 플랫폼·버전으로 피드백 1건이 저장됨
        List<UserFeedback> feedbacks = feedbacksOf(me);
        assertThat(feedbacks).hasSize(1);
        UserFeedback saved = feedbacks.getFirst();
        assertThat(saved.getType()).isEqualTo(FeedbackType.SUGGESTION);
        assertThat(saved.getDetail()).isEqualTo("알림이 늦게 와요");
        assertThat(saved.getAppPlatform()).isEqualTo(AppPlatform.ANDROID);
        assertThat(saved.getAppVersion()).isEqualTo("1.4.0");
        assertThat(saved.getCreatedAt()).isNotNull();

        // then: 선택지 행은 건의하기 선택지 하나만 그 피드백 id로 저장됨
        assertThat(optionIdsOf(saved)).containsExactly(suggestionOptionId);
    }

    @Test
    @DisplayName("피드백 보내기: 앱 흐름대로 탈퇴 사유를 보낸 뒤 탈퇴하면 둘 다 성공하고, 탈퇴 대기 중에 다시 보내도 저장된다")
    void sendFeedback_then_withdraw_end_to_end() throws Exception {
        // given: 실제 유저 + 실제 파일의 탈퇴 사유 첫 선택지
        User me = saveUser();
        Long withdrawalOptionId = feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL).getFirst().id();
        String body = """
                {"type":"withdrawal","optionIds":["%d"],"detail":null}
                """.formatted(withdrawalOptionId);

        // when: 탈퇴 사유 전송 → 탈퇴 신청 → 탈퇴 대기 상태에서 다시 탈퇴 사유 전송
        mockMvc.perform(post("/api/v1/me/feedback")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/me")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/feedback")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // then: 탈퇴 사유 2건이 내용 없이 저장되고, 앱 헤더가 없으니 플랫폼·버전은 비어 있음
        List<UserFeedback> feedbacks = feedbacksOf(me);
        assertThat(feedbacks)
                .extracting(UserFeedback::getType, UserFeedback::getDetail, UserFeedback::getAppPlatform, UserFeedback::getAppVersion)
                .containsExactly(
                        tuple(FeedbackType.WITHDRAWAL, null, null, null),
                        tuple(FeedbackType.WITHDRAWAL, null, null, null));

        // then: 두 피드백 모두 각자의 id로 같은 선택지 행이 저장됨
        assertThat(feedbacks).allSatisfy(feedback -> assertThat(optionIdsOf(feedback)).containsExactly(withdrawalOptionId));
    }

    @Test
    @DisplayName("피드백 보내기: 건의하기에 내용이 없으면 400 INVALID_REQUEST이고 저장되지 않는다")
    void sendFeedback_suggestion_without_detail_returns_400() throws Exception {
        // given: 실제 유저 + 실제 파일의 건의하기 첫 선택지
        User me = saveUser();
        Long suggestionOptionId = feedbackOptionLoader.getOptions(FeedbackType.SUGGESTION).getFirst().id();

        // when & then: 선택지만 고르고 내용 없이 건의하기 전송하면 400
        mockMvc.perform(post("/api/v1/me/feedback")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"suggestion","optionIds":["%d"],"detail":null}
                                """.formatted(suggestionOptionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));

        // then: 아무것도 저장되지 않음
        assertThat(feedbacksOf(me)).isEmpty();
    }

    @Test
    @DisplayName("피드백 보내기: 탈퇴 사유에 선택지도 내용도 없어도 200이고 빈 피드백 1건이 저장된다")
    void sendFeedback_withdrawal_without_options_and_detail_saves_empty() throws Exception {
        // given: 실제 유저
        User me = saveUser();

        // when & then: 빈 선택지 + 내용 없이 탈퇴 사유 전송하면 200
        mockMvc.perform(post("/api/v1/me/feedback")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"withdrawal","optionIds":[],"detail":null}
                                """))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // then: 내용 없는 탈퇴 사유 1건이 저장되고 선택지 행은 없음
        List<UserFeedback> feedbacks = feedbacksOf(me);
        assertThat(feedbacks)
                .extracting(UserFeedback::getType, UserFeedback::getDetail)
                .containsExactly(tuple(FeedbackType.WITHDRAWAL, null));
        assertThat(optionIdsOf(feedbacks.getFirst())).isEmpty();
    }

    @Test
    @DisplayName("닉네임 수정: 바꾼 닉네임이 DB에 저장되고 프로필 수정 화면 조회에 그대로 나온다")
    void changeProfileNickname_end_to_end() throws Exception {
        // given: 실제 유저 저장
        User me = saveUser();
        flushAndClear();

        // when: 실제 액세스 토큰으로 닉네임 수정 API 호출
        mockMvc.perform(put("/api/v1/me/profile/nickname")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "트윈리새닉"}
                                """))
                .andExpect(status().isOk());

        // then: DB에 닉네임이 저장되고, 프로필 수정 화면에도 바뀐 값이 나온다
        flushAndClear();
        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("트윈리새닉");
        mockMvc.perform(get("/api/v1/me/profile-edit-view")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("트윈리새닉"));
    }

    @Test
    @DisplayName("닉네임 수정: 대소문자를 구분하지 않는 DB에서도 자기 닉네임의 대소문자만 바꾸면 성공한다")
    void changeProfileNickname_case_only_change_end_to_end() throws Exception {
        // given: 현재 닉네임이 Twinly인 실제 유저
        User me = saveUser();
        me.changeNickname("Twinly");
        flushAndClear();

        // when: 대소문자만 다른 닉네임으로 수정 API 호출
        var result = mockMvc.perform(put("/api/v1/me/profile/nickname")
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "twinly"}
                        """));

        // then: 본인 행에 막히지 않고 200 + DB에 바뀐 대소문자가 저장됨
        result.andExpect(status().isOk());
        flushAndClear();
        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("twinly");
    }

    @Test
    @DisplayName("닉네임 수정: 다른 유저가 대소문자만 다른 같은 닉네임을 쓰고 있으면 409를 반환하고 닉네임은 그대로다")
    void changeProfileNickname_taken_by_other_user_ignoring_case_returns_409() throws Exception {
        // given: 다른 유저가 Twinly를 쓰는 중
        User me = saveUser();
        User other = saveUser();
        other.changeNickname("Twinly");
        flushAndClear();

        // when: 대문자로만 바꾼 TWINLY로 수정 API 호출
        var result = mockMvc.perform(put("/api/v1/me/profile/nickname")
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "TWINLY"}
                        """));

        // then: 409 NICKNAME_ALREADY_USED + 내 닉네임은 그대로
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NICKNAME_ALREADY_USED.name()));
        entityManager.clear();
        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo(me.getNickname());
    }

    @Test
    @DisplayName("닉네임 수정: 온보딩 중인 익명 세션이 잡아 둔 닉네임이면 409를 반환한다")
    void changeProfileNickname_taken_by_onboarding_session_returns_409() throws Exception {
        // given: 실제 유저 + 같은 닉네임을 잡아 둔 온보딩 중인 익명 세션
        User me = saveUser();
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(1)));
        anonSession.changeNickname("트윈리온보딩");
        anonSessionRepository.save(anonSession);
        flushAndClear();

        // when: 익명 세션이 잡아 둔 닉네임으로 수정 API 호출
        var result = mockMvc.perform(put("/api/v1/me/profile/nickname")
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "트윈리온보딩"}
                        """));

        // then: 409 NICKNAME_ALREADY_USED + 내 닉네임은 그대로
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NICKNAME_ALREADY_USED.name()));
        entityManager.clear();
        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo(me.getNickname());
    }

    @Test
    @DisplayName("닉네임 수정: 인증 헤더가 없으면 401을 반환한다")
    void changeProfileNickname_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 닉네임 수정 API 호출
        var result = mockMvc.perform(put("/api/v1/me/profile/nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "트윈리새닉"}
                        """));

        // then: 401 반환
        result.andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("닉네임 중복 확인: 내 현재 닉네임은 대소문자만 달라도 사용 가능으로 응답한다")
    void checkProfileNickname_own_nickname_is_available_end_to_end() throws Exception {
        // given: 현재 닉네임이 Twinly인 실제 유저
        User me = saveUser();
        me.changeNickname("Twinly");
        flushAndClear();

        // when: 대소문자만 다른 내 닉네임으로 중복 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/me/profile/nickname/check")
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "twinly"}
                        """));

        // then: 본인 행에 막히지 않고 사용 가능
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    @DisplayName("닉네임 중복 확인: 다른 유저가 대소문자만 다른 같은 닉네임을 쓰고 있으면 사용 불가로 응답한다")
    void checkProfileNickname_taken_by_other_user_ignoring_case_end_to_end() throws Exception {
        // given: 다른 유저가 Twinly를 쓰는 중
        User me = saveUser();
        User other = saveUser();
        other.changeNickname("Twinly");
        flushAndClear();

        // when: 대문자로만 바꾼 TWINLY로 중복 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/me/profile/nickname/check")
                .header("Authorization", bearer(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "TWINLY"}
                        """));

        // then: 사용 불가
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));
    }

    @Test
    @DisplayName("닉네임 중복 확인: 인증 헤더가 없으면 401을 반환한다")
    void checkProfileNickname_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 중복 확인 API 호출
        var result = mockMvc.perform(post("/api/v1/me/profile/nickname/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "트윈리새닉"}
                        """));

        // then: 401 반환
        result.andExpect(status().isUnauthorized());
    }

    private List<Long> optionIdsOf(UserFeedback feedback) {
        return userFeedbackOptionRepository.findAll().stream()
                .filter(option -> option.getFeedbackId().equals(feedback.getId()))
                .sorted(Comparator.comparing(UserFeedbackOption::getId))
                .map(UserFeedbackOption::getOptionId)
                .toList();
    }

    private List<UserFeedback> feedbacksOf(User user) {
        return userFeedbackRepository.findAll().stream()
                .filter(feedback -> feedback.getUserId().equals(user.getId()))
                .sorted(Comparator.comparing(UserFeedback::getId))
                .toList();
    }
}
