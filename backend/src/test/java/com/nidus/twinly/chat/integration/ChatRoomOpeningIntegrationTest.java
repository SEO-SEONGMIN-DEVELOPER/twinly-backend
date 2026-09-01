package com.nidus.twinly.chat.integration;

import com.nidus.twinly.chat.entity.ChatRoomOpening;
import com.nidus.twinly.chat.repository.ChatRoomOpeningRepository;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** scheduled_at 이 KST 벽시계를 UTC 인스턴트로 바꿔 저장되는지, 그 값으로 조회되는지 고정한다. */
class ChatRoomOpeningIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDateTime KST_UPDATE_TIME = LocalDateTime.of(2026, 9, 14, 20, 10);
    private static final String EXPECTED_UTC = "2026-09-14 11:10:00";

    @Autowired
    ChatRoomOpeningRepository chatRoomOpeningRepository;

    @Autowired
    EntityManager entityManager;

    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() {
        User first = saveUser();
        User second = saveUser();
        userAId = Math.min(first.getId(), second.getId());
        userBId = Math.max(first.getId(), second.getId());
    }

    private String rawScheduledAt() {
        return entityManager
                .createNativeQuery("SELECT CAST(scheduled_at AS CHAR) FROM chat_room_openings WHERE user_a_id = :a")
                .setParameter("a", userAId)
                .getSingleResult()
                .toString();
    }

    @Test
    @DisplayName("KST 20:10 로 예약하면 DB에는 UTC 11:10 으로 저장된다")
    void upsert_stores_utc() {
        // given: KST 벽시계 20:10 을 인스턴트로 변환
        Instant scheduledAt = KstTimes.toInstant(KST_UPDATE_TIME);

        // when: 예약 저장
        chatRoomOpeningRepository.upsert(userAId, userBId, scheduledAt);

        // then: 컬럼에는 9시간 당겨진 UTC 벽시계가 들어간다
        assertThat(rawScheduledAt()).startsWith(EXPECTED_UTC);

        // then: 다시 읽으면 원래 인스턴트로 복원된다
        List<ChatRoomOpening> all = chatRoomOpeningRepository.findAll();
        assertThat(all).singleElement()
                .satisfies(opening -> assertThat(opening.getScheduledAt()).isEqualTo(scheduledAt));
    }

    @Test
    @DisplayName("도래 여부는 인스턴트로 비교되어 KST 시각을 그대로 반영한다")
    void due_query_compares_by_instant() {
        // given: 도래한 예약과 아직 남은 예약
        chatRoomOpeningRepository.upsert(userAId, userBId, Instant.now().minusSeconds(60));

        // when: 현재 시각으로 도래분 조회
        List<ChatRoomOpening> due = chatRoomOpeningRepository
                .findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(Instant.now());

        // then: 지난 예약이 잡힌다
        assertThat(due).hasSize(1);

        // when: 도래 이전 시점으로 조회
        List<ChatRoomOpening> notYet = chatRoomOpeningRepository
                .findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(Instant.now().minusSeconds(600));

        // then: 아직 안 잡힌다
        assertThat(notYet).isEmpty();
    }

    @Test
    @DisplayName("같은 쌍을 다시 예약해도 행은 하나이고 더 이른 시각이 남는다")
    void upsert_keeps_earliest() {
        // given: 늦은 시각으로 먼저 예약
        Instant later = KstTimes.toInstant(KST_UPDATE_TIME.plusDays(1));
        Instant earlier = KstTimes.toInstant(KST_UPDATE_TIME);
        chatRoomOpeningRepository.upsert(userAId, userBId, later);

        // when: 더 이른 시각으로 다시 예약하고, 또 더 늦은 시각으로 예약
        chatRoomOpeningRepository.upsert(userAId, userBId, earlier);
        chatRoomOpeningRepository.upsert(userAId, userBId, later.plusSeconds(3600));

        // then: 행은 하나, 가장 이른 시각이 남는다
        assertThat(chatRoomOpeningRepository.findAll()).singleElement()
                .satisfies(opening -> assertThat(opening.getScheduledAt()).isEqualTo(earlier));
    }

    @Test
    @DisplayName("개설 표시가 된 예약은 다시 조회되지 않는다")
    void opened_is_excluded() {
        // given: 도래한 예약을 열고 표시한 상태
        chatRoomOpeningRepository.upsert(userAId, userBId, Instant.now().minusSeconds(60));
        ChatRoomOpening opening = chatRoomOpeningRepository.findAll().getFirst();
        opening.markOpened(Instant.now());
        chatRoomOpeningRepository.saveAndFlush(opening);

        // when: 도래분 조회
        List<ChatRoomOpening> due = chatRoomOpeningRepository
                .findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(Instant.now());

        // then: 이미 연 건은 빠진다 (행 자체는 남아 재예약을 흡수한다)
        assertThat(due).isEmpty();
        assertThat(chatRoomOpeningRepository.findAll()).hasSize(1);
    }
}
