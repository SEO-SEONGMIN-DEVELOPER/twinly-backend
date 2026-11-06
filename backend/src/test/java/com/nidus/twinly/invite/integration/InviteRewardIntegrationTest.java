package com.nidus.twinly.invite.integration;

import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.invite.domain.InvitePolicy;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.entity.InviteRegistration;
import com.nidus.twinly.invite.entity.InviteReward;
import com.nidus.twinly.invite.entity.TwinCreation;
import com.nidus.twinly.invite.repository.InviteRegistrationRepository;
import com.nidus.twinly.invite.repository.InviteRewardRepository;
import com.nidus.twinly.invite.repository.TwinCreationRepository;
import com.nidus.twinly.parallelrelation.entity.ParallelRelationCode;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationCodeRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InviteRewardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ParallelRelationCodeRepository parallelRelationCodeRepository;

    @Autowired
    InviteRegistrationRepository inviteRegistrationRepository;

    @Autowired
    InviteRewardRepository inviteRewardRepository;

    @Autowired
    TwinCreationRepository twinCreationRepository;

    @Autowired
    UserSurveyAnswerRepository userSurveyAnswerRepository;

    @Autowired
    PersonaElementRepository personaElementRepository;

    @Autowired
    SurveyLoader surveyLoader;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("코드 등록 뒤 친구가 트윈 생성을 처음 마치면 트윈 생성 기록이 남고 초대 1건이 성립한다")
    void register_then_first_twin_creation_completes_invite() throws Exception {
        // given: 코드를 가진 초대자와 아직 트윈이 없는 친구
        User inviter = saveUser();
        User friend = saveUser();
        parallelRelationCodeRepository.save(ParallelRelationCode.create(inviter.getId(), "K7M2QX"));

        // when: 친구가 소문자로 코드 등록
        mockMvc.perform(post("/api/v1/me/invite-code")
                        .header("Authorization", bearer(friend.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"k7m2qx"}
                                """))
                .andExpect(status().isOk());

        // then: 등록만 되고 아직 성립 전
        InviteRegistration registered = inviteRegistrationRepository.findByInviteeUserId(friend.getId()).orElseThrow();
        assertThat(registered.getInviterUserId()).isEqualTo(inviter.getId());
        assertThat(registered.isCompleted()).isFalse();

        // given: 친구가 설문 전 문항과 관심사를 마침
        surveyLoader.getAllQuestions().forEach(question ->
                userSurveyAnswerRepository.upsert(friend.getId(), question.id(), SurveyOptionName.A.name()));
        personaElementRepository.save(PersonaElement.create(friend.getId(), PersonaDimension.INTEREST, "등산", Instant.now()));
        flushAndClear();

        // when: AI 대화 종료로 트윈 생성 완료
        mockMvc.perform(post("/api/v1/me/ai-chat/complete")
                        .header("Authorization", bearer(friend.getId())))
                .andExpect(status().isOk());
        flushAndClear();

        // then: 트윈 생성 기록 + 초대 성립
        assertThat(twinCreationRepository.existsByUserId(friend.getId())).isTrue();
        assertThat(inviteRegistrationRepository.findByInviteeUserId(friend.getId()).orElseThrow().isCompleted()).isTrue();

        // then: 초대자 보상 화면에 등록 1·성립 1
        mockMvc.perform(get("/api/v1/me/invite-reward")
                        .header("Authorization", bearer(inviter.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("K7M2QX"))
                .andExpect(jsonPath("$.registeredCount").value(1))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.goal").value(5))
                .andExpect(jsonPath("$.reward").value(nullValue()));
    }

    @Test
    @DisplayName("보상 조회: 코드가 없으면 이때 발급하고, 친구 코드를 등록하기 전에는 canRegisterCode 가 true 다")
    void inviteReward_issues_code_and_reports_registrable() throws Exception {
        // given: 코드가 없는 새 유저
        User me = saveUser();

        // when & then: 코드 발급 + 초대 링크 + 등록 가능
        mockMvc.perform(get("/api/v1/me/invite-reward")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.canRegisterCode").value(true))
                .andExpect(jsonPath("$.promotionalSource").value(nullValue()));

        assertThat(parallelRelationCodeRepository.findByUserId(me.getId())).isPresent();
    }

    @Test
    @DisplayName("코드 등록: 같은 DI 의 예전 계정이 트윈을 만든 적 있으면 409 INVITE_CODE_NOT_ELIGIBLE")
    void register_rejects_previous_twin_of_same_di() throws Exception {
        // given: 같은 DI 의 트윈 생성 기록이 남은 새 계정
        User inviter = saveUser();
        parallelRelationCodeRepository.save(ParallelRelationCode.create(inviter.getId(), "K7M2QX"));
        User me = saveUser();
        ReflectionTestUtils.setField(me, "diHash", "di-hash-rejoined");
        userRepository.save(me);
        User previous = saveUser();
        twinCreationRepository.save(TwinCreation.record(previous.getId(), "di-hash-rejoined", Instant.now()));
        flushAndClear();

        // when & then: 409
        mockMvc.perform(post("/api/v1/me/invite-code")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"K7M2QX"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("애플 오퍼 수락 보고: 대기 중인 보상을 수락으로 바꾸고 거래 ID 를 저장한다")
    void acceptAppleOffer_end_to_end() throws Exception {
        // given: 애플 오퍼 대기 중인 보상
        User me = saveUser();
        InviteReward reward = InviteReward.create(me.getId(), "di-hash-apple", Instant.now());
        ReflectionTestUtils.setField(reward, "status", InviteRewardStatus.APPLE_OFFER_PENDING);
        ReflectionTestUtils.setField(reward, "productId", "twinly_monthly");
        inviteRewardRepository.save(reward);
        flushAndClear();

        // when: 수락 보고
        mockMvc.perform(post("/api/v1/me/invite-reward/apple-offer")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId":"2000000123456789"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reward.status").value("appleOfferAccepted"));
        flushAndClear();

        // then: DB 상태 반영
        InviteReward accepted = inviteRewardRepository.findByUserId(me.getId()).orElseThrow();
        assertThat(accepted.getStatus()).isEqualTo(InviteRewardStatus.APPLE_OFFER_ACCEPTED);
        assertThat(accepted.getAppleTransactionId()).isEqualTo("2000000123456789");
    }

    @Test
    @DisplayName("보상 없이 성립 5건을 채운 초대자만 찾는 쿼리가 실제 MySQL 에서 동작한다")
    void findInviterIdsReachedWithoutReward_on_mysql() {
        // given: 성립 5건 초대자 A, 4건 초대자 B
        User inviterA = saveUser();
        User inviterB = saveUser();
        for (int i = 0; i < InvitePolicy.GOAL; i++) {
            InviteRegistration registration = InviteRegistration.register(inviterA.getId(), saveUser().getId(), Instant.now());
            registration.complete(Instant.now());
            inviteRegistrationRepository.save(registration);
        }
        for (int i = 0; i < InvitePolicy.GOAL - 1; i++) {
            InviteRegistration registration = InviteRegistration.register(inviterB.getId(), saveUser().getId(), Instant.now());
            registration.complete(Instant.now());
            inviteRegistrationRepository.save(registration);
        }
        flushAndClear();

        // when & then: A 만 대상
        assertThat(inviteRegistrationRepository.findInviterIdsReachedWithoutReward(InvitePolicy.GOAL))
                .containsExactly(inviterA.getId());
    }

    @Test
    @DisplayName("탈퇴 후 1년이 지난 계정의 DI 해시만 트윈 생성 기록과 보상에서 지운다")
    void clearDiHashOfWithdrawnBefore_on_mysql() {
        // given: 400일 전 탈퇴·파기된 계정과 활동 중인 계정
        User old = saveUser();
        old.requestWithdrawal(Duration.ZERO);
        ReflectionTestUtils.setField(old, "withdrawalRequestedAt", Instant.now().minus(Duration.ofDays(400)));
        old.delete();
        userRepository.save(old);
        User active = saveUser();
        twinCreationRepository.save(TwinCreation.record(old.getId(), "di-old", Instant.now()));
        twinCreationRepository.save(TwinCreation.record(active.getId(), "di-active", Instant.now()));
        inviteRewardRepository.save(InviteReward.create(old.getId(), "di-old", Instant.now()));
        flushAndClear();

        // when: 1년 기준으로 정리
        Instant cutoff = Instant.now().minus(Duration.ofDays(365));
        twinCreationRepository.clearDiHashOfWithdrawnBefore(cutoff);
        inviteRewardRepository.clearDiHashOfWithdrawnBefore(cutoff);
        flushAndClear();

        // then: 오래된 탈퇴자만 지워짐
        assertThat(twinCreationRepository.existsByDiHash("di-old")).isFalse();
        assertThat(twinCreationRepository.existsByDiHash("di-active")).isTrue();
        assertThat(inviteRewardRepository.existsByDiHash("di-old")).isFalse();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
