package com.nidus.twinly.notification.integration;

import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppNotificationNoticeIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/admin/app-notifications/notices";
    private static final String ADMIN_TOKEN = "test-admin-token";


    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("전체 발행: 활성 가입자에게만 공지가 들어가고 탈퇴 신청·삭제·시드 계정은 빠지며, 다시 호출해도 중복되지 않는다")
    void publishToAll_activeUsersOnly_idempotent() throws Exception {
        // given: 활성 유저 1명 + 탈퇴 신청·삭제·시드 유저 각 1명
        User active = saveUser();
        User withdrawing = saveUser();
        withdrawing.requestWithdrawal(Duration.ofDays(30));
        User deleted = saveUser();
        deleted.delete();
        User seed = saveUser();
        userRepository.markSeed(List.of(seed.getId()));
        flushAndClear();

        // when: 대상 없이 공지 발행
        publish("""
                {"title": "처리방침이 바뀌어요", "body": "본문"}
                """)
                .andExpect(status().isOk());

        // then: 활성 유저만 공지 1건을 받는다
        assertThat(noticeCount(active)).isEqualTo(1);
        assertThat(noticeCount(withdrawing)).isZero();
        assertThat(noticeCount(deleted)).isZero();
        assertThat(noticeCount(seed)).isZero();

        // when & then: 같은 공지를 다시 발행하면 새로 들어가는 건이 없다
        publish("""
                {"title": "처리방침이 바뀌어요", "body": "본문"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(0));
        assertThat(noticeCount(active)).isEqualTo(1);
    }

    @Test
    @DisplayName("대상 지정 발행: 지정한 활성 유저에게만 들어가고, 이후 전체 발행에서는 이미 받은 유저를 건너뛴다")
    void publishToUserIds_thenToAll_skipsAlreadyReceived() throws Exception {
        // given: 테스트 계정 1명, 지정했지만 탈퇴 신청한 1명, 지정하지 않은 1명
        User tester = saveUser();
        User withdrawingTester = saveUser();
        withdrawingTester.requestWithdrawal(Duration.ofDays(30));
        User other = saveUser();
        flushAndClear();

        // when: 테스트 계정 둘을 지정해 발행
        publish("""
                {"title": "테스트 공지", "body": "본문", "userIds": ["%d", "%d"]}
                """.formatted(tester.getId(), withdrawingTester.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(1));

        // then: 활성인 테스트 계정만 받는다
        assertThat(noticeCount(tester)).isEqualTo(1);
        assertThat(noticeCount(withdrawingTester)).isZero();
        assertThat(noticeCount(other)).isZero();

        // when: 같은 공지를 전체 발행
        publish("""
                {"title": "테스트 공지", "body": "본문"}
                """)
                .andExpect(status().isOk());

        // then: 이미 받은 테스트 계정은 그대로 1건, 나머지가 새로 받는다
        assertThat(noticeCount(tester)).isEqualTo(1);
        assertThat(noticeCount(other)).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 토큰이 틀리면 실제 컨텍스트에서도 401을 반환하고 공지가 들어가지 않는다")
    void publish_withWrongToken_unauthorized() throws Exception {
        // given: 공지를 받을 수 있는 활성 유저 저장
        User active = saveUser();
        flushAndClear();

        // when: 틀린 관리자 토큰으로 발행
        var result = mockMvc.perform(post(PATH)
                .header("X-Admin-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "제목", "body": "본문"}
                        """));

        // then: 401 반환 + DB 에 공지가 들어가지 않음
        result.andExpect(status().isUnauthorized());
        assertThat(noticeCount(active)).isZero();
    }

    @Test
    @DisplayName("DB 제약: 공지는 target 을 가질 수 없고, 공지가 아닌 피드는 target 없이 저장할 수 없다")
    void checkConstraint_noticeHasNoTarget_othersRequireTarget() {
        // given: target 으로 가리킬 유저와 피드 주인 유저 저장 (FK 때문에 둘 다 필요)
        User me = saveUser();
        User partner = saveUser();
        flushAndClear();

        // when & then: target 이 붙은 공지는 거부
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO app_notification_feeds (user_id, title, body, type, target_kind, target_user_id)
                VALUES (?, '제목', '본문', 'NOTICE', 'PROFILE', ?)
                """, me.getId(), partner.getId()))
                .hasMessageContaining("ck_notification_feeds_target_kind");

        // when & then: target 없는 친구 피드는 거부
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO app_notification_feeds (user_id, title, body, type)
                VALUES (?, '제목', '본문', 'FRIEND')
                """, me.getId()))
                .hasMessageContaining("ck_notification_feeds_target_kind");
    }

    private ResultActions publish(String body) throws Exception {
        return mockMvc.perform(post(PATH)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long noticeCount(User user) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_notification_feeds WHERE user_id = ? AND type = 'NOTICE'",
                Long.class, user.getId());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
