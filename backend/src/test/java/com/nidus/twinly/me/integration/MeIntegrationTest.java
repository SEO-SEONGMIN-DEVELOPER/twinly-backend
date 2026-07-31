package com.nidus.twinly.me.integration;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.AgreementRepository;
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
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import jakarta.persistence.EntityManager;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    BlindIndexHasher blindIndexHasher;

    @Autowired
    EntityManager entityManager;

    // CloudFront 서명 URL 생성은 실제 키가 필요하므로 목으로 대체한다.
    @MockitoBean
    CloudFrontService cloudFrontService;

    @Autowired
    PolicyRepository policyRepository;

    @Autowired
    AgreementRepository agreementRepository;

    @Autowired
    NotificationSettingRepository notificationSettingRepository;

    @Autowired
    AppNotificationFeedRepository appNotificationFeedRepository;

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
        User me = saveUser();
        PolicyName policyName = policyNameRepository.save(policyName("서비스 이용약관", "terms_of_service"));
        Policy policy = policyRepository.save(policy(policyName.getId(), 1, "https://policy/tos/1", true));

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
    @DisplayName("인증 헤더가 없으면 실제 컨텍스트에서도 401을 반환한다")
    void without_auth_returns_401() throws Exception {
        // when & then: 인증 헤더 없이 내 상태 조회 시 401
        mockMvc.perform(get("/api/v1/me/status"))
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
                .andExpect(jsonPath("$.familyName").value(me.getFamilyName()))
                .andExpect(jsonPath("$.givenName").value(me.getGivenName()))
                .andExpect(jsonPath("$.affiliation").value(me.getAffiliation()))
                .andExpect(jsonPath("$.birthDate").value(me.getBirthDate()))
                .andExpect(jsonPath("$.profilePhoto").doesNotExist());
    }

    @Test
    @DisplayName("프로필 수정: 소속을 바꾸면 DB의 평문·블라인드 인덱스가 함께 갱신된다")
    void profile_patch_end_to_end() throws Exception {
        // given: 실제 유저 저장
        User me = saveUser();

        // when: 실제 액세스 토큰으로 소속 변경 API 호출
        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"affiliation": "니두스대학교"}
                                """))
                .andExpect(status().isOk());

        // then: DB에서 다시 읽어도 소속이 갱신되고, 해시도 새 값 기준으로 저장됨
        flushAndClear();
        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getAffiliation()).isEqualTo("니두스대학교");
        assertThat(reloaded.getAffiliationHash()).isEqualTo(blindIndexHasher.hash("니두스대학교"));
    }

    @Test
    @DisplayName("약관 동의 철회: 선택 정책이면 동의 이력이 철회되어 조회에서 isGranted가 false가 된다")
    void revokeConsents_end_to_end() throws Exception {
        // given: 선택 정책에 이미 동의한 실제 유저
        User me = saveUser();
        PolicyName name = policyNameRepository.save(policyName("마케팅 수신 동의", "marketing"));
        Policy policy = policyRepository.save(policy(name.getId(), 1, "https://example.com/marketing", false));
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
        Policy policy = policyRepository.save(policy(name.getId(), 1, "https://example.com/terms", true));
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
        given(s3Service.exists(key)).willReturn(true);
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
        given(s3Service.exists(newKey)).willReturn(true);
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
        ReflectionTestUtils.setField(question, "time", LocalTime.of(21, 0));
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
        ReflectionTestUtils.setField(policyName, "name", name);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "isDeprecated", false);
        return policyName;
    }

    private Policy policy(Long policyNameId, Integer version, String url, Boolean isRequired) {
        Policy policy = BeanUtils.instantiateClass(Policy.class);
        ReflectionTestUtils.setField(policy, "policyNameId", policyNameId);
        ReflectionTestUtils.setField(policy, "version", version);
        ReflectionTestUtils.setField(policy, "content", "약관 본문");
        ReflectionTestUtils.setField(policy, "url", url);
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
}
