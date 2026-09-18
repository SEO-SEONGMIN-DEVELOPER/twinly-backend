package com.nidus.twinly.report.service;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.dto.command.ReportAiUtteranceCommand;
import com.nidus.twinly.report.dto.command.ReportUserCommand;
import com.nidus.twinly.report.dto.result.ReportUserResult;
import com.nidus.twinly.report.entity.AiUtteranceReport;
import com.nidus.twinly.report.entity.Report;
import com.nidus.twinly.report.event.AiUtteranceReportedEvent;
import com.nidus.twinly.report.event.UserReportedEvent;
import com.nidus.twinly.report.repository.AiUtteranceReportRepository;
import com.nidus.twinly.report.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReportServiceUnitTest {

    @Mock
    ReportRepository reportRepository;

    @Mock
    BlockRepository blockRepository;

    @Mock
    AiUtteranceReportRepository aiUtteranceReportRepository;

    @Mock
    SceneRepository sceneRepository;

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ReportService reportService;

    @Test
    @DisplayName("자기 자신을 신고하면 CANNOT_REPORT_SELF 예외가 발생하고 신고·차단을 저장하지 않는다")
    void reportUser_self_throws() {
        // when & then: 자기 자신 신고 시 CANNOT_REPORT_SELF 예외 발생 + 아무것도 저장하지 않음
        assertThatThrownBy(() -> reportService.reportUser(1L, new ReportUserCommand(1L, ReportReason.SPAM, "광고 도배")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REPORT_SELF);

        then(reportRepository).should(never()).save(any());
        then(blockRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("차단 이력이 없는 유저를 신고하면 PENDING 신고를 저장하고 자동으로 차단한다")
    void reportUser_new_saves_report_and_block() {
        // given: 신고자(1)가 대상(2)을 아직 차단하지 않은 상태
        given(blockRepository.existsByUserIdAndBlockedUserId(1L, 2L)).willReturn(false);
        given(reportRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when: 대상 유저를 신고
        ReportUserResult result = reportService.reportUser(1L, new ReportUserCommand(2L, ReportReason.HARASSMENT, "괴롭힙니다"));

        // then: 신고자·대상·사유·상세가 담긴 PENDING 신고 저장 + 자동 차단 저장 + autoBlock=true 반환
        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        then(reportRepository).should().save(reportCaptor.capture());
        Report saved = reportCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getReportedUserId()).isEqualTo(2L);
        assertThat(saved.getReason()).isEqualTo(ReportReason.HARASSMENT);
        assertThat(saved.getDetail()).isEqualTo("괴롭힙니다");
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);

        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        then(blockRepository).should().save(blockCaptor.capture());
        assertThat(blockCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(blockCaptor.getValue().getBlockedUserId()).isEqualTo(2L);

        assertThat(result.autoBlock()).isTrue();

        ArgumentCaptor<UserReportedEvent> eventCaptor = ArgumentCaptor.forClass(UserReportedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        UserReportedEvent event = eventCaptor.getValue();
        assertThat(event.reporterUserId()).isEqualTo(1L);
        assertThat(event.reportedUserId()).isEqualTo(2L);
        assertThat(event.reason()).isEqualTo(ReportReason.HARASSMENT);
        assertThat(event.detail()).isEqualTo("괴롭힙니다");
    }

    @Test
    @DisplayName("같은 사유·상세로 이미 신고한 유저를 다시 신고하면 신고를 저장하지 않고 알림 이벤트도 발행하지 않는다")
    void reportUser_duplicate_skips_save_and_event() {
        // given: 신고자(1)가 대상(2)을 같은 사유·상세로 이미 신고했고 차단도 되어 있는 상태
        given(reportRepository.findAllByUserIdAndReportedUserId(1L, 2L))
                .willReturn(List.of(Report.create(1L, 2L, ReportReason.SPAM, "광고 도배")));
        given(blockRepository.existsByUserIdAndBlockedUserId(1L, 2L)).willReturn(true);

        // when: 같은 내용으로 다시 신고
        reportService.reportUser(1L, new ReportUserCommand(2L, ReportReason.SPAM, "광고 도배"));

        // then: 신고가 중복 저장되지 않고, 운영 채널에 같은 알림이 반복되지 않도록 이벤트도 발행하지 않는다
        then(reportRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 차단한 유저를 신고하면 신고만 저장하고 차단은 중복 저장하지 않는다 (멱등)")
    void reportUser_already_blocked_skips_block_save() {
        // given: 신고자(1)가 대상(2)을 이미 차단한 상태
        given(blockRepository.existsByUserIdAndBlockedUserId(1L, 2L)).willReturn(true);
        given(reportRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when: 이미 차단한 대상을 신고
        ReportUserResult result = reportService.reportUser(1L, new ReportUserCommand(2L, ReportReason.SPAM, "광고 도배"));

        // then: 신고는 저장되지만 차단은 중복 저장되지 않고, autoBlock은 여전히 true
        then(reportRepository).should().save(any(Report.class));
        then(blockRepository).should(never()).save(any());
        assertThat(result.autoBlock()).isTrue();
    }

    @Test
    @DisplayName("AI 발화 신고 시 씬이 존재하지 않으면 SCENE_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void aiUtterance_scene_not_found_throws() {
        // given: 해당 씬이 존재하지 않음
        given(sceneRepository.findById(77L)).willReturn(Optional.empty());

        // when & then: SCENE_NOT_FOUND 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> reportService.reportAiUtterance(
                1L, new ReportAiUtteranceCommand(2L, 77L, "부적절한 발언", "HARASSMENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCENE_NOT_FOUND);

        then(aiUtteranceReportRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("AI 발화 신고 시 남의 씬이면 NOT_SCENE_OWNER 예외가 발생하고 저장하지 않는다")
    void aiUtterance_not_scene_owner_throws() {
        // given: 씬의 주인이 신고자(1)가 아닌 다른 유저(99). 씬은 조회 API에서 항상 조회자 소유로만 내려간다
        Scene scene = mock(Scene.class);
        given(scene.getUserId()).willReturn(99L);
        given(sceneRepository.findById(77L)).willReturn(Optional.of(scene));

        // when & then: 남의 씬 id를 찍어 신고할 수 없다
        assertThatThrownBy(() -> reportService.reportAiUtterance(
                1L, new ReportAiUtteranceCommand(2L, 77L, "부적절한 발언", "HARASSMENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_SCENE_OWNER);

        then(aiUtteranceReportRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("AI 발화 신고 시 신고 대상이 그 씬의 참여자가 아니면 SCENE_TARGET_MISMATCH 예외가 발생하고 저장하지 않는다")
    void aiUtterance_scene_target_mismatch_throws() {
        // given: 내 씬이지만 신고 대상(2)이 그 씬에 등장하지 않음
        Scene scene = mock(Scene.class);
        given(scene.getUserId()).willReturn(1L);
        given(sceneRepository.findById(77L)).willReturn(Optional.of(scene));
        given(scenePartnerRepository.existsBySceneIdAndUserId(77L, 2L)).willReturn(false);

        // when & then: SCENE_TARGET_MISMATCH 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> reportService.reportAiUtterance(
                1L, new ReportAiUtteranceCommand(2L, 77L, "부적절한 발언", "HARASSMENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCENE_TARGET_MISMATCH);

        then(aiUtteranceReportRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("AI 발화 신고 시 내 씬에 등장한 상대를 신고하면 PENDING 상태로 신고를 저장한다")
    void aiUtterance_success_saves_report() {
        // given: 씬(77)은 내 것이고 신고 대상(2)이 그 씬의 참여자다
        Scene scene = mock(Scene.class);
        given(scene.getUserId()).willReturn(1L);
        given(sceneRepository.findById(77L)).willReturn(Optional.of(scene));
        given(scenePartnerRepository.existsBySceneIdAndUserId(77L, 2L)).willReturn(true);
        given(aiUtteranceReportRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when: AI 발화 신고
        reportService.reportAiUtterance(1L, new ReportAiUtteranceCommand(2L, 77L, "부적절한 발언", "HARASSMENT"));

        // then: 신고자·대상·씬·발화·사유가 담긴 PENDING 신고 저장
        ArgumentCaptor<AiUtteranceReport> captor = ArgumentCaptor.forClass(AiUtteranceReport.class);
        then(aiUtteranceReportRepository).should().save(captor.capture());
        AiUtteranceReport saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getReportedUserId()).isEqualTo(2L);
        assertThat(saved.getSceneId()).isEqualTo(77L);
        assertThat(saved.getUtteranceText()).isEqualTo("부적절한 발언");
        assertThat(saved.getReason()).isEqualTo("HARASSMENT");
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);

        ArgumentCaptor<AiUtteranceReportedEvent> eventCaptor = ArgumentCaptor.forClass(AiUtteranceReportedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        AiUtteranceReportedEvent event = eventCaptor.getValue();
        assertThat(event.reporterUserId()).isEqualTo(1L);
        assertThat(event.reportedUserId()).isEqualTo(2L);
        assertThat(event.sceneId()).isEqualTo(77L);
        assertThat(event.utteranceText()).isEqualTo("부적절한 발언");
        assertThat(event.reason()).isEqualTo("HARASSMENT");
    }

    @Test
    @DisplayName("AI 발화 신고 시 같은 씬의 같은 발화·사유로 이미 신고했으면 저장하지 않고 알림 이벤트도 발행하지 않는다")
    void aiUtterance_duplicate_skips_save_and_event() {
        // given: 씬(77)은 내 것이고 대상(2)이 참여자이며, 같은 발화·사유로 이미 신고한 상태
        Scene scene = mock(Scene.class);
        given(scene.getUserId()).willReturn(1L);
        given(sceneRepository.findById(77L)).willReturn(Optional.of(scene));
        given(scenePartnerRepository.existsBySceneIdAndUserId(77L, 2L)).willReturn(true);
        given(aiUtteranceReportRepository.findAllByUserIdAndSceneId(1L, 77L))
                .willReturn(List.of(AiUtteranceReport.create(1L, 2L, 77L, "부적절한 발언", "HARASSMENT")));

        // when: 같은 내용으로 다시 신고
        reportService.reportAiUtterance(1L, new ReportAiUtteranceCommand(2L, 77L, "부적절한 발언", "HARASSMENT"));

        // then: 저장도 이벤트 발행도 하지 않는다
        then(aiUtteranceReportRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }
}
