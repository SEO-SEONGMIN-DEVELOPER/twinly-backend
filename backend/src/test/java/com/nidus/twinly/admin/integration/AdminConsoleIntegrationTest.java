package com.nidus.twinly.admin.integration;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.feedback.FeedbackStatus;
import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.domain.ViolationKind;
import com.nidus.twinly.report.entity.AiUtteranceReport;
import com.nidus.twinly.report.entity.Report;
import com.nidus.twinly.report.repository.AiUtteranceReportRepository;
import com.nidus.twinly.report.repository.ReportRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.UserFeedback;
import com.nidus.twinly.user.entity.UserFeedbackOption;
import com.nidus.twinly.user.entity.Verification;
import com.nidus.twinly.user.repository.UserFeedbackOptionRepository;
import com.nidus.twinly.user.repository.UserFeedbackRepository;
import com.nidus.twinly.user.repository.VerificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminConsoleIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_TOKEN = "test-admin-token";
    private static final long SEASON_ID = 990_001L;
    private static final long ACTIVE_SEASON_ID = 990_002L;

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper objectMapper;
    @Autowired BlindIndexHasher blindIndexHasher;
    @Autowired UserEntitlementRepository userEntitlementRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired SceneRepository sceneRepository;
    @Autowired ScenePartnerRepository scenePartnerRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatRoomParticipationRepository chatRoomParticipationRepository;
    @Autowired ChatRepository chatRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired AiUtteranceReportRepository aiUtteranceReportRepository;
    @Autowired UserFeedbackRepository userFeedbackRepository;
    @Autowired UserFeedbackOptionRepository userFeedbackOptionRepository;

    private final AtomicInteger memberSeq = new AtomicInteger();
    private long activeSeasonId;

    @BeforeEach
    void insertSeason() {
        jdbcTemplate.update("""
                INSERT IGNORE INTO seasons (id, started_at, ended_at, is_active, created_at)
                VALUES (?, UTC_TIMESTAMP(6) - INTERVAL 30 DAY, UTC_TIMESTAMP(6) + INTERVAL 30 DAY, 0, UTC_TIMESTAMP(6))
                """, SEASON_ID);
        List<Long> active = jdbcTemplate.queryForList("SELECT id FROM seasons WHERE is_active = 1 ORDER BY id DESC LIMIT 1", Long.class);
        if (active.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO seasons (id, started_at, ended_at, is_active, created_at)
                    VALUES (?, UTC_TIMESTAMP(6) - INTERVAL 30 DAY, UTC_TIMESTAMP(6) + INTERVAL 30 DAY, 1, UTC_TIMESTAMP(6))
                    """, ACTIVE_SEASON_ID);
            activeSeasonId = ACTIVE_SEASON_ID;
        } else {
            activeSeasonId = active.getFirst();
        }
    }

    @Test
    @DisplayName("어드민 콘솔 API는 관리자 토큰이 있어야 부를 수 있다")
    void adminApis_requireAdminToken() throws Exception {
        // when & then: 토큰이 있으면 200, 없으면 401
        mockMvc.perform(admin(get("/admin/dashboard"))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("대시보드: 가입자·구독자(현재 시즌 참가자)·구독 종류·탈퇴자·학교를 세고 시드 회원은 모든 집계에서 뺀다")
    void dashboard_countsMembersSubscribersWithdrawnAndSchools() throws Exception {
        // given: 지금까지의 집계를 기억해 두고, 시즌에 참가한 활성 남2·여1, 이용권만 있고 시즌 미참가 1명, 시드 1, 탈퇴 유예 1, 탈퇴 완료 1 추가
        JsonNode before = body(mockMvc.perform(admin(get("/admin/dashboard"))));
        User paid = saveMember("유료남", Gender.MALE, "가나대학교", "paid@ganada.ac.kr");
        User trial = saveMember("체험여", Gender.FEMALE, "가나대학교", "trial@ganada.ac.kr");
        User early = saveMember("선착남", Gender.MALE, "다라대학교", "early@dara.ac.kr");
        User seed = saveMember("시드", Gender.FEMALE, "가나대학교", "seed@ganada.ac.kr");
        userRepository.markSeed(List.of(seed.getId()));
        saveMember("유예", Gender.FEMALE, "가나대학교", "pending@ganada.ac.kr").requestWithdrawal(Duration.ofDays(30));
        saveMember("탈퇴", Gender.MALE, "가나대학교", "gone@ganada.ac.kr").delete();
        entitle(paid, "app_store", "normal", true);
        entitle(trial, "play_store", "trial", true);
        entitle(early, "promotional", "normal", true);
        entitle(seed, null, null, true);
        User entitledOnly = saveMember("미참가", Gender.FEMALE, "다라대학교", "only@dara.ac.kr");
        entitle(entitledOnly, "promotional", "normal", true);
        participate(paid);
        participate(trial);
        participate(early);
        participate(seed);
        flushAndClear();
        long baseMembers = countActiveMembers() - 4;

        // when
        JsonNode dashboard = body(mockMvc.perform(admin(get("/admin/dashboard"))).andExpect(status().isOk()));

        // then
        // then: 구독자는 이용권이 아니라 현재 시즌 참가로 세므로 이용권만 있는 회원은 빠진다
        assertThat(dashboard.at("/members/total").asLong()).isEqualTo(baseMembers + 4);
        assertThat(delta(before, dashboard, "/subscribers/count/total")).isEqualTo(3);
        assertThat(delta(before, dashboard, "/subscribers/count/male")).isEqualTo(2);
        assertThat(delta(before, dashboard, "/subscribers/paid")).isEqualTo(1);
        assertThat(delta(before, dashboard, "/subscribers/trial")).isEqualTo(1);
        assertThat(delta(before, dashboard, "/subscribers/earlyGrant")).isEqualTo(1);
        assertThat(dashboard.at("/withdrawn/completed").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.at("/withdrawn/pending").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(schoolCount(dashboard, "가나대학교")).isEqualTo(2);
        assertThat(schoolCount(dashboard, "다라대학교")).isEqualTo(2);
    }

    @Test
    @DisplayName("대시보드 나이 통계: 생년월일을 복호화해 출생연도별 가입자·구독자를 세고, 시드와 탈퇴 회원은 뺀다")
    void dashboard_countsBirthYears() throws Exception {
        // given: 1971년생 시즌 참가자 1명, 1972년생 이용권만 있는 미참가자 1명, 1971년생 시드·탈퇴 유예 회원 각 1명
        User subscribed = saveMember("칠일", Gender.MALE, "타파대학교", "s71@tapa.ac.kr", "1971-03-01");
        User notParticipating = saveMember("칠이", Gender.FEMALE, "타파대학교", "n72@tapa.ac.kr", "19720701");
        User seed = saveMember("시드칠일", Gender.FEMALE, "타파대학교", "seed71@tapa.ac.kr", "1971-05-05");
        userRepository.markSeed(List.of(seed.getId()));
        saveMember("유예칠일", Gender.MALE, "타파대학교", "w71@tapa.ac.kr", "1971-06-06").requestWithdrawal(Duration.ofDays(30));
        entitle(subscribed, "app_store", "normal", true);
        entitle(notParticipating, "promotional", "normal", true);
        entitle(seed, null, null, true);
        participate(subscribed);
        participate(seed);
        flushAndClear();

        // when
        JsonNode dashboard = body(mockMvc.perform(admin(get("/admin/dashboard"))).andExpect(status().isOk()));

        // then: 형식이 달라도(하이픈 유무) 앞 네 자리를 출생연도로 읽고, 활성 가입자만 센다
        assertThat(birthYear(dashboard, 1971)).containsExactly(1L, 1L);
        assertThat(birthYear(dashboard, 1972)).containsExactly(1L, 0L);
    }

    @Test
    @DisplayName("회원 검색: 닉네임은 일부로, 이메일·학교는 값 전체로 찾고 시드 회원은 나오지 않는다")
    void members_searchByNicknameEmailAndSchool() throws Exception {
        // given
        User target = saveMember("별헤는밤", Gender.MALE, "마바대학교", "Star@maba.ac.kr");
        User seed = saveMember("별시드", Gender.FEMALE, "마바대학교", "seed@maba.ac.kr");
        userRepository.markSeed(List.of(seed.getId()));
        entitle(target, "app_store", "normal", true);
        flushAndClear();

        // when & then: 닉네임 일부
        mockMvc.perform(admin(get("/admin/members").param("key", "NICKNAME").param("query", "별")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(String.valueOf(target.getId())))
                .andExpect(jsonPath("$.items[0].maskedEmail").value("St****@maba.ac.kr"))
                .andExpect(jsonPath("$.items[0].subscription.kind").value("PAID"));

        // when & then: 이메일 전체 일치(대소문자 다르게 입력해도 저장값과 같으면 찾음), 일부는 못 찾음
        mockMvc.perform(admin(get("/admin/members").param("key", "EMAIL").param("query", "Star@maba.ac.kr")))
                .andExpect(jsonPath("$.totalCount").value(1));
        mockMvc.perform(admin(get("/admin/members").param("key", "EMAIL").param("query", "Star@maba")))
                .andExpect(jsonPath("$.totalCount").value(0));

        // when & then: 학교 이름 전체 일치
        mockMvc.perform(admin(get("/admin/members").param("key", "SCHOOL").param("query", "마바대학교")))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName("회원 상세: 인증 시각·구독·장면 날짜·관련 신고를 모으고, 날짜별 장면은 이름을 닉네임으로 채워 준다")
    void memberDetail_andScenes() throws Exception {
        // given
        User me = saveMember("하늘별", Gender.FEMALE, "사아대학교", "sky@saa.ac.kr");
        User partner = saveMember("모카라떼", Gender.FEMALE, "사아대학교", "mocha@saa.ac.kr");
        Instant verifiedAt = Instant.parse("2026-09-29T03:00:00Z");
        verificationRepository.save(Verification.create(me.getId(), VerificationType.IDENTITY, verifiedAt));
        entitle(me, "app_store", "normal", true);
        LocalDate date = LocalDate.of(2026, 10, 19);
        Scene dialogue = sceneRepository.save(Scene.createDialogue(me.getId(), date, "v1", "중앙도서관", null,
                date.atTime(12, 30), date.atTime(13, 0), """
                        [{"t":"narr","text":"{user_%d}이 자리를 찾는다","occursAt":"2026-10-19T12:30:00"},
                         {"t":"bubble","userId":%d,"action":null,"text":"여기 앉아도 돼?","occursAt":"2026-10-19T12:31:00"}]
                        """.formatted(partner.getId(), partner.getId())));
        scenePartnerRepository.save(ScenePartner.create(dialogue.getId(), partner.getId()));
        reportRepository.save(Report.create(partner.getId(), me.getId(), ReportReason.SPAM, "링크를 보내요"));
        flushAndClear();

        // when & then: 상세
        mockMvc.perform(admin(get("/admin/members/{id}", me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.identityVerifiedAt").value("2026-09-29T12:00:00+09:00"))
                .andExpect(jsonPath("$.subscription.kind").value("PAID"))
                .andExpect(jsonPath("$.sceneDates[0]").value("2026-10-19"))
                .andExpect(jsonPath("$.relatedCases[0].category").value("USER_REPORT"))
                .andExpect(jsonPath("$.relatedCases[0].role").value("REPORTED"));

        // when & then: 장면
        mockMvc.perform(admin(get("/admin/members/{id}/scenes", me.getId()).param("date", "2026-10-19")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("DIALOGUE"))
                .andExpect(jsonPath("$[0].partnerNicknames[0]").value("모카라떼"))
                .andExpect(jsonPath("$[0].lines[0].text").value("모카라떼가 자리를 찾는다"))
                .andExpect(jsonPath("$[0].lines[1].speaker").value("모카라떼"));
    }

    @Test
    @DisplayName("채팅방: 입장 상태로 거르고 메시지 수·마지막 시각을 붙이며, 시드가 있는 방은 목록·상세 모두에서 숨긴다")
    void chatRooms_listDetailAndMessages() throws Exception {
        // given: 둘 다 입장해 메시지 3개 / 한 명만 입장 / 시드가 낀 방
        User a = saveMember("가가", Gender.MALE, "자차대학교", "a@jacha.ac.kr");
        User b = saveMember("나나", Gender.FEMALE, "자차대학교", "b@jacha.ac.kr");
        User c = saveMember("다다", Gender.FEMALE, "자차대학교", "c@jacha.ac.kr");
        User seed = saveMember("시드", Gender.FEMALE, "자차대학교", "s@jacha.ac.kr");
        userRepository.markSeed(List.of(seed.getId()));
        Long both = room(a, b, true, true);
        Long one = room(a, c, true, false);
        Long seeded = room(a, seed, true, true);
        for (int i = 1; i <= 3; i++) {
            chatRepository.save(Chat.create("m" + i, both, a.getId(), b.getId(), ChatMessageType.TEXT, "메시지" + i));
        }
        flushAndClear();

        // when & then: 회원 a 의 방 중 시드 방은 빠지고 둘
        mockMvc.perform(admin(get("/admin/chat-rooms").param("query", String.valueOf(a.getId()))))
                .andExpect(jsonPath("$.totalCount").value(2));
        mockMvc.perform(admin(get("/admin/chat-rooms").param("query", String.valueOf(a.getId())).param("entryState", "ONE")))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(String.valueOf(one)));
        mockMvc.perform(admin(get("/admin/chat-rooms").param("query", "나나").param("entryState", "BOTH")))
                .andExpect(jsonPath("$.items[0].id").value(String.valueOf(both)))
                .andExpect(jsonPath("$.items[0].messageCount").value(3));
        mockMvc.perform(admin(get("/admin/chat-rooms/{id}", seeded))).andExpect(status().isNotFound());

        // when & then: 메시지는 최신부터, 커서로 이전 메시지
        JsonNode first = body(mockMvc.perform(admin(get("/admin/chat-rooms/{id}/messages", both).param("size", "2"))));
        assertThat(first.at("/messages/0/text").asString()).isEqualTo("메시지3");
        assertThat(first.at("/hasMore").asBoolean()).isTrue();
        mockMvc.perform(admin(get("/admin/chat-rooms/{id}/messages", both)
                        .param("beforeId", first.at("/messages/1/id").asString()).param("size", "2")))
                .andExpect(jsonPath("$.messages[0].text").value("메시지1"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @DisplayName("구독 목록: 종류로 거르고 종류별 활성 수를 함께 주며, 첫 결제 할인가(intro)는 유료로 센다")
    void subscriptions_filterByKind() throws Exception {
        // given
        User trial = saveMember("체험", Gender.FEMALE, "카타대학교", "t@kata.ac.kr");
        User expired = saveMember("만료", Gender.MALE, "카타대학교", "e@kata.ac.kr");
        User intro = saveMember("할인가", Gender.MALE, "카타대학교", "i@kata.ac.kr");
        entitle(trial, "play_store", "trial", true);
        entitle(expired, "app_store", "trial", false);
        entitle(intro, "app_store", "intro", true);
        flushAndClear();

        // when & then: 활성만이면 만료된 체험은 빠진다
        JsonNode activeTrials = body(mockMvc.perform(admin(get("/admin/subscriptions").param("kind", "TRIAL").param("size", "100"))));
        assertThat(userIdsIn(activeTrials.at("/page/items"), "/subscription/userId")).contains(String.valueOf(trial.getId()))
                .doesNotContain(String.valueOf(expired.getId()));
        assertThat(activeTrials.at("/activeCounts/trial").asLong()).isGreaterThanOrEqualTo(1);

        // then: 첫 결제 할인가(intro)는 돈을 낸 구독이라 무료 체험이 아니라 유료로 센다
        JsonNode paid = body(mockMvc.perform(admin(get("/admin/subscriptions").param("kind", "PAID").param("size", "100"))));
        assertThat(userIdsIn(paid.at("/page/items"), "/subscription/userId")).contains(String.valueOf(intro.getId()));
        assertThat(userIdsIn(activeTrials.at("/page/items"), "/subscription/userId")).doesNotContain(String.valueOf(intro.getId()));

        JsonNode allTrials = body(mockMvc.perform(admin(get("/admin/subscriptions").param("kind", "TRIAL").param("activeOnly", "false").param("size", "100"))));
        assertThat(userIdsIn(allTrials.at("/page/items"), "/subscription/userId")).contains(String.valueOf(expired.getId()));
    }

    @Test
    @DisplayName("신고 처리: 처리 완료로 바꾸면 처리 시각이 남고, 다시 검토 중으로 되돌리면 지워진다")
    void userReport_review() throws Exception {
        // given
        User reporter = saveMember("신고자", Gender.FEMALE, "파하대학교", "r@paha.ac.kr");
        User reported = saveMember("대상", Gender.MALE, "파하대학교", "d@paha.ac.kr");
        Report report = reportRepository.save(Report.create(reporter.getId(), reported.getId(), ReportReason.HARASSMENT, "연락처를 계속 물어봐요"));
        flushAndClear();

        // when: 조치로 처리 완료
        mockMvc.perform(admin(patch("/admin/cases/user-reports/{id}", report.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "RESOLVED", "reviewNote": "채팅 기록 확인", "actionNote": "경고 안내"}
                                """))
                .andExpect(status().isOk());
        flushAndClear();

        // then
        Report resolved = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(resolved.getReviewNote()).isEqualTo("채팅 기록 확인");
        assertThat(resolved.getProcessedAt()).isNotNull();
        mockMvc.perform(admin(get("/admin/cases/user-reports").param("status", "DONE")))
                .andExpect(jsonPath("$.items[?(@.id == '%d')].actionNote".formatted(report.getId())).value("경고 안내"));

        // when & then: 검토 중으로 되돌리면 처리 시각이 지워진다
        mockMvc.perform(admin(patch("/admin/cases/user-reports/{id}", report.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_REVIEW"}
                                """))
                .andExpect(status().isOk());
        flushAndClear();
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("AI 발화 신고 처리: 문제 구분을 저장하고, 없는 신고는 404")
    void aiUtteranceReport_review() throws Exception {
        // given
        User reporter = saveMember("신고자", Gender.FEMALE, "가하대학교", "r@gaha.ac.kr");
        User reported = saveMember("트윈", Gender.MALE, "가하대학교", "t@gaha.ac.kr");
        AiUtteranceReport report = aiUtteranceReportRepository.save(AiUtteranceReport.create(
                reporter.getId(), reported.getId(), 1L, "그 수업 별로던데", "비하 발언"));
        flushAndClear();

        // when
        mockMvc.perform(admin(patch("/admin/cases/ai-utterance-reports/{id}", report.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "RESOLVED", "violationKind": "AI_UTTERANCE", "actionNote": "AI 팀 전달"}
                                """))
                .andExpect(status().isOk());
        flushAndClear();

        // then
        assertThat(aiUtteranceReportRepository.findById(report.getId()).orElseThrow().getViolationKind()).isEqualTo(ViolationKind.AI_UTTERANCE);
        mockMvc.perform(admin(patch("/admin/cases/ai-utterance-reports/{id}", 999_999_999L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_REVIEW"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("건의사항·탈퇴 사유: 건의는 상태와 메모를 저장하고, 탈퇴 사유는 선택지별로 센다")
    void feedbacks_suggestionReviewAndWithdrawalReasons() throws Exception {
        // given: 건의 1건(선택지 4) + 탈퇴 2건(선택지 1, 1·3)
        User author = saveMember("작성자", Gender.FEMALE, "나다대학교", "w@nada.ac.kr");
        UserFeedback suggestion = feedback(author, FeedbackType.SUGGESTION, "알림이 두 번 와요", 4L);
        long before = withdrawalOptionCount(1L);
        feedback(author, FeedbackType.WITHDRAWAL, "비싸요", 1L);
        feedback(author, FeedbackType.WITHDRAWAL, null, 1L, 3L);
        flushAndClear();

        // when: 건의 검토
        mockMvc.perform(admin(patch("/admin/cases/suggestions/{id}", suggestion.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_REVIEW", "memo": "FCM 중복 확인 중"}
                                """))
                .andExpect(status().isOk());
        flushAndClear();

        // then
        UserFeedback reviewed = userFeedbackRepository.findById(suggestion.getId()).orElseThrow();
        assertThat(reviewed.getStatus()).isEqualTo(FeedbackStatus.IN_REVIEW);
        assertThat(reviewed.getMemo()).isEqualTo("FCM 중복 확인 중");
        mockMvc.perform(admin(get("/admin/cases/suggestions").param("status", "IN_REVIEW")))
                .andExpect(jsonPath("$.items[?(@.id == '%d')].optionLabels[0]".formatted(suggestion.getId())).value("기능 제안"));

        JsonNode withdrawals = body(mockMvc.perform(admin(get("/admin/cases/withdrawal-reasons"))));
        assertThat(withdrawals.at("/reasonCounts/0/label").asString()).isEqualTo("인연이 마음에 들지 않아요");
        assertThat(withdrawals.at("/reasonCounts/0/count").asLong()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("탈퇴 사유: 시즌 참가 전이면 가입일부터, 참가 후면 첫 참가일부터 탈퇴까지 날짜 수를 준다")
    void withdrawalReasons_withdrawalTiming() throws Exception {
        // given: 오늘 가입하고 바로 탈퇴한 회원, 10일 전 가입·3일 전 첫 참가 뒤 탈퇴한 회원
        User quick = saveMember("바로탈퇴", Gender.MALE, "시기대학교", "q@sigi.ac.kr");
        User subscribed = saveMember("참가탈퇴", Gender.FEMALE, "시기대학교", "s@sigi.ac.kr");
        jdbcTemplate.update("UPDATE users SET created_at = UTC_TIMESTAMP(6) - INTERVAL 10 DAY WHERE id = ?", subscribed.getId());
        jdbcTemplate.update("""
                INSERT INTO season_participations (user_id, season_id, participated_in_at, created_at)
                VALUES (?, ?, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, UTC_TIMESTAMP(6))
                """, subscribed.getId(), activeSeasonId);
        UserFeedback quickReason = feedback(quick, FeedbackType.WITHDRAWAL, null, 1L);
        UserFeedback subscribedReason = feedback(subscribed, FeedbackType.WITHDRAWAL, null, 1L);
        flushAndClear();

        // when & then
        mockMvc.perform(admin(get("/admin/cases/withdrawal-reasons")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.items[?(@.id == '%d')].withdrawalTiming.stage".formatted(quickReason.getId())).value("BEFORE_SUBSCRIPTION"))
                .andExpect(jsonPath("$.page.items[?(@.id == '%d')].withdrawalTiming.days".formatted(quickReason.getId())).value(0))
                .andExpect(jsonPath("$.page.items[?(@.id == '%d')].withdrawalTiming.stage".formatted(subscribedReason.getId())).value("AFTER_SUBSCRIPTION"))
                .andExpect(jsonPath("$.page.items[?(@.id == '%d')].withdrawalTiming.days".formatted(subscribedReason.getId())).value(3));
        mockMvc.perform(admin(get("/admin/cases/suggestions")))
                .andExpect(jsonPath("$.items[0].withdrawalTiming").doesNotExist());
    }

    @Test
    @DisplayName("기능 이용 현황: 평행우주 관계는 결과를 받은 양쪽 회원을 한 번씩 세고, 분모로 가입자 수를 준다")
    void usage_countsParallelRelationUsers() throws Exception {
        // given: 남성 A 가 두 명과 관계 결과를 받았고, 코드만 발급한 남성 D 는 결과가 없다
        JsonNode before = body(mockMvc.perform(admin(get("/admin/usage"))));
        User a = saveMember("평행남", Gender.MALE, "평행대학교", "p1@pyeong.ac.kr");
        User b = saveMember("평행여", Gender.FEMALE, "평행대학교", "p2@pyeong.ac.kr");
        User c = saveMember("평행여2", Gender.FEMALE, "평행대학교", "p3@pyeong.ac.kr");
        User codeOnly = saveMember("코드만", Gender.MALE, "평행대학교", "p4@pyeong.ac.kr");
        parallelRelation(a, b);
        parallelRelation(c, a);
        jdbcTemplate.update("INSERT INTO parallel_relation_codes (user_id, code) VALUES (?, 'PAR001')", codeOnly.getId());
        flushAndClear();

        // when
        JsonNode after = body(mockMvc.perform(admin(get("/admin/usage"))).andExpect(status().isOk()));

        // then
        assertThat(delta(before, after, "/parallelRelation/users/male")).isEqualTo(1);
        assertThat(delta(before, after, "/parallelRelation/users/female")).isEqualTo(2);
        assertThat(delta(before, after, "/members/total")).isEqualTo(4);
    }

    @Test
    @DisplayName("기능 이용 현황: 시드가 낀 방은 빼고 회원별 열린 건수 대비 입장 건수와 상대 성별을 센다")
    void usage_countsEntriesAndPartnerGenders() throws Exception {
        // given: 현재까지의 집계를 기억해 두고 남-여 방(둘 다 입장), 남-남 방(한 명만 입장) 추가
        JsonNode before = body(mockMvc.perform(admin(get("/admin/usage"))));
        User man = saveMember("남1", Gender.MALE, "마사대학교", "m1@masa.ac.kr");
        User woman = saveMember("여1", Gender.FEMALE, "마사대학교", "w1@masa.ac.kr");
        User man2 = saveMember("남2", Gender.MALE, "마사대학교", "m2@masa.ac.kr");
        room(man, woman, true, true);
        room(man, man2, true, false);
        flushAndClear();

        // when
        JsonNode after = body(mockMvc.perform(admin(get("/admin/usage"))).andExpect(status().isOk()));

        // then
        assertThat(delta(before, after, "/chatRoom/openedRooms")).isEqualTo(2);
        assertThat(delta(before, after, "/chatRoom/bothEnteredRooms")).isEqualTo(1);
        assertThat(delta(before, after, "/chatRoom/openedEntries/male")).isEqualTo(3);
        assertThat(delta(before, after, "/chatRoom/enteredEntries/male")).isEqualTo(2);
        assertThat(delta(before, after, "/chatRoom/enteredEntries/female")).isEqualTo(1);
        assertThat(delta(before, after, "/chatRoom/malePartners/oppositeGender")).isEqualTo(1);
        assertThat(delta(before, after, "/chatRoom/malePartners/sameGender")).isEqualTo(1);
        assertThat(delta(before, after, "/chatRoom/femalePartners/oppositeGender")).isEqualTo(1);
    }

    @Test
    @DisplayName("기능 이용 현황: 망설임 질문은 한 번이라도 답한 회원만 세고 건너뛰기와 약속 질문은 뺀다")
    void usage_countsHesitationAnswerUsers() throws Exception {
        // given: 두 번 답한 남성, 건너뛰기만 한 여성, 약속 질문에만 답한 남성
        JsonNode before = body(mockMvc.perform(admin(get("/admin/usage"))));
        User answered = saveMember("답변남", Gender.MALE, "망대학교", "h1@mang.ac.kr");
        User skipped = saveMember("건너뜀여", Gender.FEMALE, "망대학교", "h2@mang.ac.kr");
        User promise = saveMember("약속남", Gender.MALE, "망대학교", "h3@mang.ac.kr");
        question(answered, "PERSONA", true, false);
        question(answered, "PERSONA", true, false);
        question(skipped, "PERSONA", false, true);
        question(promise, "PROMISE", true, false);
        flushAndClear();

        // when
        JsonNode after = body(mockMvc.perform(admin(get("/admin/usage"))).andExpect(status().isOk()));

        // then
        assertThat(delta(before, after, "/hesitation/users/total")).isEqualTo(1);
        assertThat(delta(before, after, "/hesitation/users/male")).isEqualTo(1);
        assertThat(delta(before, after, "/hesitation/users/female")).isZero();
    }

    @Test
    @DisplayName("공지 수신 대상 수: 지정한 ID 중 활성 가입자만 센다")
    void noticeRecipientCount() throws Exception {
        // given
        User active = saveMember("활성", Gender.MALE, "아자대학교", "a@aja.ac.kr");
        User withdrawing = saveMember("유예", Gender.MALE, "아자대학교", "b@aja.ac.kr");
        withdrawing.requestWithdrawal(Duration.ofDays(30));
        flushAndClear();

        // when & then
        mockMvc.perform(admin(get("/admin/app-notifications/notices/recipient-count")
                        .param("userIds", String.valueOf(active.getId()), String.valueOf(withdrawing.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientCount").value(1));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Admin-Token", ADMIN_TOKEN);
    }

    private JsonNode body(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private User saveMember(String nickname, Gender gender, String organization, String email) {
        return saveMember(nickname, gender, organization, email, "2000-01-01");
    }

    private User saveMember(String nickname, Gender gender, String organization, String email, String birthDate) {
        int n = memberSeq.incrementAndGet();
        String unique = nickname + "-" + n + "-" + System.nanoTime();
        return userRepository.save(User.create(
                nickname,
                "성", "familyHash" + unique,
                "이름", "givenHash" + unique,
                gender,
                organization, blindIndexHasher.hash(organization),
                "학과", "affHash" + unique,
                "학번" + unique, "affNoHash" + unique,
                birthDate, "birthHash" + unique,
                "010" + unique, "phoneHash" + unique,
                email, blindIndexHasher.hash(email), null, null,
                null, null));
    }

    private void parallelRelation(User codeOwner, User submitter) {
        jdbcTemplate.update("""
                INSERT INTO parallel_relations (user_a_id, user_b_id, code_owner_id, similarity, raw_similarity, relation, story_index)
                VALUES (?, ?, ?, 72, 0.5, 'CLOSE', 0)
                """, Math.min(codeOwner.getId(), submitter.getId()), Math.max(codeOwner.getId(), submitter.getId()), codeOwner.getId());
    }

    private void question(User user, String type, boolean answered, boolean skipped) {
        jdbcTemplate.update("""
                INSERT INTO questions (user_id, `date`, version, `time`, type, `text`, options, choice, answered_at, is_skipped)
                VALUES (?, CURRENT_DATE, 'v1', UTC_TIMESTAMP(6), ?, '질문', JSON_ARRAY('A', 'B'), ?, ?, ?)
                """, user.getId(), type, answered ? "A" : null, answered ? Timestamp.from(Instant.now()) : null, skipped);
    }

    private void participate(User user) {
        jdbcTemplate.update("""
                INSERT INTO season_participations (user_id, season_id, participated_in_at, created_at)
                VALUES (?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, user.getId(), activeSeasonId);
    }

    private void entitle(User user, String store, String periodType, boolean active) {
        Instant expiresAt = active ? Instant.now().plus(Duration.ofDays(30)) : Instant.now().minus(Duration.ofDays(1));
        userEntitlementRepository.save(UserEntitlement.create(user.getId(), EntitlementReader.SIMULATION_ACCESS,
                expiresAt, store, periodType, Instant.now()));
    }

    private Long room(User first, User second, boolean firstEntered, boolean secondEntered) {
        long low = Math.min(first.getId(), second.getId());
        long high = Math.max(first.getId(), second.getId());
        jdbcTemplate.update("INSERT INTO matches (user_a_id, user_b_id, season_id, created_at) VALUES (?, ?, ?, UTC_TIMESTAMP(6))",
                low, high, SEASON_ID);
        Long matchId = jdbcTemplate.queryForObject(
                "SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ? AND season_id = ?", Long.class, low, high, SEASON_ID);
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        ChatRoomParticipation firstParticipation = ChatRoomParticipation.create(room.getId(), first.getId());
        ChatRoomParticipation secondParticipation = ChatRoomParticipation.create(room.getId(), second.getId());
        if (firstEntered) {
            firstParticipation.agree();
        }
        if (secondEntered) {
            secondParticipation.agree();
        }
        chatRoomParticipationRepository.save(firstParticipation);
        chatRoomParticipationRepository.save(secondParticipation);
        return room.getId();
    }

    private UserFeedback feedback(User author, FeedbackType type, String detail, Long... optionIds) {
        UserFeedback feedback = userFeedbackRepository.save(UserFeedback.create(author.getId(), type, detail, null, null));
        for (Long optionId : optionIds) {
            userFeedbackOptionRepository.save(UserFeedbackOption.create(feedback.getId(), optionId));
        }
        return feedback;
    }

    private long withdrawalOptionCount(Long optionId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_feedback_options o JOIN user_feedbacks f ON f.id = o.feedback_id
                WHERE f.type = 'WITHDRAWAL' AND o.option_id = ?
                """, Long.class, optionId);
        return count == null ? 0 : count;
    }

    private long countActiveMembers() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM users u
                WHERE u.is_seed = FALSE AND u.deleted_at IS NULL AND u.withdrawal_requested_at IS NULL
                """, Long.class);
        return count == null ? 0 : count;
    }

    private long schoolCount(JsonNode dashboard, String name) {
        for (JsonNode organization : dashboard.get("organizations")) {
            if (name.equals(organization.get("name").asString())) {
                return organization.get("memberCount").asLong();
            }
        }
        return 0;
    }

    private List<Long> birthYear(JsonNode dashboard, int year) {
        for (JsonNode birthYear : dashboard.get("birthYears")) {
            if (birthYear.get("year").asInt() == year) {
                return List.of(birthYear.get("members").asLong(), birthYear.get("subscribers").asLong());
            }
        }
        return List.of(0L, 0L);
    }

    private List<String> userIdsIn(JsonNode items, String pointer) {
        return java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(item -> item.at(pointer).asString())
                .toList();
    }

    private long delta(JsonNode before, JsonNode after, String pointer) {
        return after.at(pointer).asLong() - before.at(pointer).asLong();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
