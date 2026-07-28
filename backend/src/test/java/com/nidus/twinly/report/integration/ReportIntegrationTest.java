package com.nidus.twinly.report.integration;

import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.entity.AiUtteranceReport;
import com.nidus.twinly.report.entity.Report;
import com.nidus.twinly.report.repository.AiUtteranceReportRepository;
import com.nidus.twinly.report.repository.ReportRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    BlockRepository blockRepository;

    @Autowired
    AiUtteranceReportRepository aiUtteranceReportRepository;

    @Autowired
    SceneRepository sceneRepository;

    @Test
    @DisplayName("유저 신고 성공: 실제 유저·JWT 인증·MockMvc·DB까지 관통하여 신고 행과 자동 차단 행이 생성된다")
    void reportUser_success_end_to_end() throws Exception {
        // given: 실제 유저 2명(신고자/피신고자) 저장 (reports FK 때문에 둘 다 필요)
        User me = saveUser();
        User target = saveUser();

        // when: 신고자의 실제 액세스 토큰으로 유저 신고 API 호출
        mockMvc.perform(post("/api/v1/reports/users")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetUserId":"%s","reason":"HARASSMENT","detail":"지속적으로 괴롭힙니다"}
                                """.formatted(target.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoBlock").value(true));

        // then: DB에 PENDING 신고 행이 생성되고, 자동 차단 행도 함께 생성됨
        List<Report> reports = reportRepository.findAllByReportedUserIdAndStatus(target.getId(), ReportStatus.PENDING);
        assertThat(reports).hasSize(1);
        assertThat(reports.getFirst().getUserId()).isEqualTo(me.getId());
        assertThat(reports.getFirst().getReason()).isEqualTo(ReportReason.HARASSMENT);
        assertThat(reports.getFirst().getDetail()).isEqualTo("지속적으로 괴롭힙니다");
        assertThat(blockRepository.existsByUserIdAndBlockedUserId(me.getId(), target.getId())).isTrue();
    }

    @Test
    @DisplayName("자기 자신 신고: CANNOT_REPORT_SELF로 422를 반환하고 신고 행이 생성되지 않는다")
    void reportUser_self_returns_422() throws Exception {
        // given: 실제 유저 1명 저장
        User me = saveUser();

        // when: 자기 자신을 대상으로 유저 신고 API 호출
        mockMvc.perform(post("/api/v1/reports/users")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetUserId":"%s","reason":"SPAM","detail":"광고 도배"}
                                """.formatted(me.getId())))
                .andExpect(status().isUnprocessableContent());

        // then: DB에 신고 행이 생성되지 않음
        assertThat(reportRepository.findAllByReportedUserIdAndStatus(me.getId(), ReportStatus.PENDING)).isEmpty();
    }

    @Test
    @DisplayName("AI 발화 신고 성공: 실제 씬 소유자 검증을 통과하여 ai_utterance_reports 행이 생성된다")
    void aiUtterance_success_end_to_end() throws Exception {
        // given: 실제 유저 2명과 피신고자가 소유한 실제 씬 저장 (씬 소유자 검증을 통과해야 함)
        User me = saveUser();
        User target = saveUser();
        Scene scene = saveScene(target.getId());

        // when: 신고자의 실제 액세스 토큰으로 AI 발화 신고 API 호출 (id는 문자열로 전달)
        mockMvc.perform(post("/api/v1/reports/ai-utterances")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetUserId":"%d","sceneId":"%d","utteranceText":"부적절한 발언","reason":"HARASSMENT"}
                                """.formatted(target.getId(), scene.getId())))
                .andExpect(status().isOk());

        // then: DB에 PENDING 상태의 AI 발화 신고 행이 생성됨
        List<AiUtteranceReport> reports = aiUtteranceReportRepository.findAll();
        assertThat(reports).hasSize(1);
        AiUtteranceReport saved = reports.getFirst();
        assertThat(saved.getUserId()).isEqualTo(me.getId());
        assertThat(saved.getReportedUserId()).isEqualTo(target.getId());
        assertThat(saved.getSceneId()).isEqualTo(scene.getId());
        assertThat(saved.getUtteranceText()).isEqualTo("부적절한 발언");
        assertThat(saved.getReason()).isEqualTo("HARASSMENT");
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    /** 해당 유저가 소유한 실제 Scene을 DB에 저장한다. (팩토리/세터가 없어 리플렉션으로 필드를 채운다) */
    private Scene saveScene(Long userId) {
        Scene scene = BeanUtils.instantiateClass(Scene.class);
        ReflectionTestUtils.setField(scene, "userId", userId);
        ReflectionTestUtils.setField(scene, "date", LocalDate.of(2026, 7, 26));
        ReflectionTestUtils.setField(scene, "version", "v1");
        ReflectionTestUtils.setField(scene, "place", "카페");
        ReflectionTestUtils.setField(scene, "startsAt", LocalDateTime.of(2026, 7, 26, 10, 0));
        ReflectionTestUtils.setField(scene, "endsAt", LocalDateTime.of(2026, 7, 26, 11, 0));
        ReflectionTestUtils.setField(scene, "type", SceneType.DIALOGUE);
        ReflectionTestUtils.setField(scene, "createdAt", Instant.now());
        return sceneRepository.save(scene);
    }
}
