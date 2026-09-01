package com.nidus.twinly.chat.service;

import com.nidus.twinly.chat.entity.ChatRoomOpening;
import com.nidus.twinly.chat.opener.ChatRoomOpener;
import com.nidus.twinly.chat.repository.ChatRoomOpeningRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ChatRoomOpeningServiceUnitTest {

    private static final Instant NOW = LocalDateTime.of(2026, 8, 29, 12, 0).toInstant(ZoneOffset.UTC);

    @Mock
    ChatRoomOpeningRepository chatRoomOpeningRepository;

    @Mock
    ChatRoomOpener chatRoomOpener;

    @InjectMocks
    ChatRoomOpeningService chatRoomOpeningService;

    private ChatRoomOpening opening(Long id, Long userId, Long partnerUserId) {
        ChatRoomOpening opening = ChatRoomOpening.create(userId, partnerUserId, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(opening, "id", id);
        return opening;
    }

    @Test
    @DisplayName("예약 시각이 지난 건은 정규화된 쌍 그대로 방을 연다")
    void openDue_opens_normalized_pair() {
        // given: 3↔9 예약 한 건 (누가 걸었든 결과는 같아야 한다)
        given(chatRoomOpeningRepository.findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of(opening(1L, 3L, 9L)));

        // when: 도래분 처리
        int opened = chatRoomOpeningService.openDue(NOW);

        // then: 작은 id 가 앞에 오는 정규화된 순서로 열린다
        assertThat(opened).isEqualTo(1);
        then(chatRoomOpener).should().open(3L, 9L);
        then(chatRoomOpeningRepository).should().save(argThat(o -> o.getOpenedAt().equals(NOW)));
    }

    @Test
    @DisplayName("상대가 먼저 열어 충돌해도 개설 완료로 표시하고 다음 건을 계속 처리한다")
    void openDue_continues_after_conflict() {
        // given: 첫 건이 유니크 제약에 걸리는 상황
        given(chatRoomOpeningRepository.findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of(opening(1L, 3L, 9L), opening(2L, 4L, 8L)));
        willThrow(new DataIntegrityViolationException("duplicate")).given(chatRoomOpener).open(3L, 9L);

        // when: 도래분 처리
        int opened = chatRoomOpeningService.openDue(NOW);

        // then: 충돌 건은 세지 않지만 표시는 남기고, 뒤 건은 정상 처리된다
        assertThat(opened).isEqualTo(1);
        then(chatRoomOpener).should().open(4L, 8L);
        then(chatRoomOpeningRepository).should(times(2)).save(any(ChatRoomOpening.class));
    }

    @Test
    @DisplayName("도래한 예약이 없으면 아무것도 열지 않는다")
    void openDue_does_nothing_when_empty() {
        // given: 도래분 없음
        given(chatRoomOpeningRepository.findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of());

        // when: 도래분 처리
        int opened = chatRoomOpeningService.openDue(NOW);

        // then: 개설도 삭제도 없다
        assertThat(opened).isZero();
        then(chatRoomOpener).should(never()).open(any(), any());
        then(chatRoomOpeningRepository).should(never()).save(any(ChatRoomOpening.class));
    }

    @Test
    @DisplayName("예약을 어느 쪽 순서로 만들었든 같은 쌍으로 열린다")
    void openDue_is_order_independent() {
        // given: 9번이 3번을 상대로 만든 예약 (역순으로 생성)
        given(chatRoomOpeningRepository.findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of(opening(1L, 9L, 3L)));

        // when: 도래분 처리
        chatRoomOpeningService.openDue(NOW);

        // then: 정규화되어 (3, 9) 로 열린다 — 만든 순서가 결과를 바꾸지 않는다
        then(chatRoomOpener).should().open(3L, 9L);
    }

    @Test
    @DisplayName("이미 연 예약은 조회 대상에서 빠지므로 다시 열지 않는다")
    void openDue_ignores_already_opened() {
        // given: 리포지토리가 opened_at IS NULL 조건으로만 조회한다
        given(chatRoomOpeningRepository.findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NOW))
                .willReturn(List.of());

        // when: 도래분 처리
        chatRoomOpeningService.openDue(NOW);

        // then: 다시 여는 시도 자체가 없다
        then(chatRoomOpener).should(never()).open(any(), any());
    }
}
