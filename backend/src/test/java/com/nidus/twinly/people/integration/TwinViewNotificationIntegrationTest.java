package com.nidus.twinly.people.integration;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.people.domain.TwinViewKind;
import com.nidus.twinly.people.entity.TwinView;
import com.nidus.twinly.people.repository.TwinViewRepository;
import com.nidus.twinly.people.repository.TwinViewRepository.ViewerCountProjection;
import com.nidus.twinly.people.service.TwinViewNotificationService;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TwinViewNotificationIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate SENDING_DAY = LocalDate.of(2026, 8, 25);

    @Autowired
    TwinViewRepository twinViewRepository;

    @Autowired
    TwinViewNotificationService twinViewNotificationService;

    @Test
    @DisplayName("집계 쿼리: 프로필과 이벤트 열람을 합쳐 세고 같은 사람은 1명으로 센다")
    void counts_distinct_viewers_across_both_kinds() {
        // given: viewer1 은 프로필·이벤트를 모두, viewer2 는 이벤트만 열람
        User target = saveUser();
        User viewer1 = saveUser();
        User viewer2 = saveUser();

        Instant to = kstEightPm(SENDING_DAY);
        Instant from = kstEightPm(SENDING_DAY.minusDays(5));

        saveView(target, viewer1, TwinViewKind.PROFILE, from);
        saveView(target, viewer1, TwinViewKind.EVENT, from.plusSeconds(60));
        saveView(target, viewer1, TwinViewKind.EVENT, from.plusSeconds(120));
        saveView(target, viewer2, TwinViewKind.EVENT, from.plusSeconds(180));

        // 구간 밖: 시작 직전과 종료 시각 정각
        saveView(target, viewer2, TwinViewKind.PROFILE, from.minusSeconds(1));
        saveView(target, viewer2, TwinViewKind.PROFILE, to);

        // when: 집계
        List<ViewerCountProjection> counts = twinViewRepository.countDistinctViewersByViewedAtRange(from, to);

        // then: 종류를 합쳐 중복 제거해 2명, 구간 경계는 시작 포함·종료 제외
        assertThat(counts).hasSize(1);
        assertThat(counts.getFirst().getTargetUserId()).isEqualTo(target.getId());
        assertThat(counts.getFirst().getViewerCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("집계 쿼리: 대상이 여러 명이면 각자의 열람자 수로 나뉘어 나온다")
    void groups_counts_by_target_user() {
        // given: 대상 2명이 각각 다른 수의 열람자를 가진다
        User target1 = saveUser();
        User target2 = saveUser();
        User viewer1 = saveUser();
        User viewer2 = saveUser();

        Instant to = kstEightPm(SENDING_DAY);
        Instant from = kstEightPm(SENDING_DAY.minusDays(5));
        Instant inside = from.plusSeconds(3600);

        saveView(target1, viewer1, TwinViewKind.PROFILE, inside);
        saveView(target2, viewer1, TwinViewKind.EVENT, inside);
        saveView(target2, viewer2, TwinViewKind.PROFILE, inside);

        // when: 집계
        List<ViewerCountProjection> counts = twinViewRepository.countDistinctViewersByViewedAtRange(from, to);

        // then: 대상별로 각자의 열람자 수
        assertThat(counts)
                .extracting(ViewerCountProjection::getTargetUserId, ViewerCountProjection::getViewerCount)
                .containsExactlyInAnyOrder(tuple(target1.getId(), 1L), tuple(target2.getId(), 2L));
    }

    @Test
    @DisplayName("알림 생성: 열람자 수 알림이 앱 알림 목록 API에 본인 프로필 대상으로 나타난다")
    void notification_appears_in_app_notification_feeds() throws Exception {
        // given: 프로필·이벤트를 합쳐 열람자 2명
        User target = saveUser();
        User viewer1 = saveUser();
        User viewer2 = saveUser();

        Instant inside = kstEightPm(SENDING_DAY.minusDays(5)).plusSeconds(3600);
        saveView(target, viewer1, TwinViewKind.PROFILE, inside);
        saveView(target, viewer1, TwinViewKind.EVENT, inside.plusSeconds(60));
        saveView(target, viewer2, TwinViewKind.EVENT, inside);

        // when: 발송일에 알림 작업 실행 (ENUM·CHECK 제약을 실제 INSERT 로 통과해야 한다)
        twinViewNotificationService.notifyViewerCounts(SENDING_DAY);

        // then: 목록 API 에 합산·중복 제거된 2명으로 노출되고 대상은 본인 프로필
        mockMvc.perform(get("/api/v1/me/app-notifications/feeds")
                        .header("Authorization", bearer(target.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appNotificationFeeds[0].type").value("twinView"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].title").value("2명이 회원님에게 관심을 보였어요."))
                .andExpect(jsonPath("$.appNotificationFeeds[0].body")
                        .value("지난 5일간 2명이 회원님의 프로필 또는 대화 기록을 열람했어요."))
                .andExpect(jsonPath("$.appNotificationFeeds[0].isRead").value(false))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.kind").value("profile"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.userId").value(target.getId().toString()))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("알림 생성: type=twinView 로 필터링해도 조회된다")
    void notification_is_filterable_by_type() throws Exception {
        // given: 열람자 1명
        User target = saveUser();
        User viewer = saveUser();
        saveView(target, viewer, TwinViewKind.PROFILE, kstEightPm(SENDING_DAY.minusDays(5)).plusSeconds(3600));

        // when: 알림 작업 실행
        twinViewNotificationService.notifyViewerCounts(SENDING_DAY);

        // then: JSON 이름으로 필터링이 동작한다
        mockMvc.perform(get("/api/v1/me/app-notifications/feeds")
                        .param("type", "twinView")
                        .header("Authorization", bearer(target.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appNotificationFeeds.length()").value(1))
                .andExpect(jsonPath("$.appNotificationFeeds[0].title").value("1명이 회원님에게 관심을 보였어요."));
    }

    private Instant kstEightPm(LocalDate date) {
        return date.atTime(LocalTime.of(20, 0)).atZone(KstTimes.ZONE).toInstant();
    }

    private void saveView(User target, User viewer, TwinViewKind kind, Instant viewedAt) {
        TwinView view = TwinView.create(target.getId(), viewer.getId(), kind);
        ReflectionTestUtils.setField(view, "viewedAt", viewedAt);
        twinViewRepository.save(view);
    }
}
