package com.nidus.twinly.block.service;

import com.nidus.twinly.block.dto.result.BlockListResult;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BlockServiceUnitTest {

    @Mock
    BlockRepository blockRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    BlockService blockService;

    @Test
    @DisplayName("자기 자신을 차단하면 CANNOT_BLOCK_SELF 예외가 발생하고 저장하지 않는다")
    void block_self_throws() {
        // when & then: 자기 자신 차단 시 CANNOT_BLOCK_SELF 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> blockService.block(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_BLOCK_SELF);

        then(blockRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 차단한 유저면 다시 저장하지 않는다 (멱등)")
    void block_already_blocked_is_noop() {
        // given: 대상 유저가 실재하고 이미 차단된 상태
        given(userRepository.existsById(2L)).willReturn(true);
        given(blockRepository.existsByUserIdAndBlockedUserId(1L, 2L)).willReturn(true);

        // when: 같은 대상을 다시 차단
        blockService.block(1L, 2L);

        // then: 저장하지 않음 (멱등)
        then(blockRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("차단 이력이 없으면 userId/blockedUserId로 새 Block을 저장한다")
    void block_new_saves() {
        // given: 대상 유저가 실재하고 차단 이력은 없음
        given(userRepository.existsById(2L)).willReturn(true);
        given(blockRepository.existsByUserIdAndBlockedUserId(1L, 2L)).willReturn(false);

        // when: 신규 차단
        blockService.block(1L, 2L);

        // then: userId/blockedUserId로 새 Block 저장
        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        then(blockRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getBlockedUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("차단 해제는 userId/blockedUserId로 삭제를 위임한다")
    void unblock_delegates_delete() {
        // when: 차단 해제
        blockService.unblock(1L, 2L);

        // then: 리포지토리에 삭제 위임
        then(blockRepository).should().deleteByUserIdAndBlockedUserId(1L, 2L);
    }

    @Test
    @DisplayName("차단 목록은 이름만 반환하고, 탈퇴한 유저는 '탈퇴한 사용자'로 표기한다")
    void blockList_maps_names_and_withdrawn_user() {
        // given: 차단 대상 2명 (정상 유저 10, 탈퇴 유저 20)
        given(blockRepository.findAllByUserId(1L))
                .willReturn(List.of(Block.create(1L, 10L), Block.create(1L, 20L)));

        User active = user("홍", "길동", null);
        ReflectionTestUtils.setField(active, "id", 10L);
        User withdrawn = user("김", "철수", Instant.now());
        ReflectionTestUtils.setField(withdrawn, "id", 20L);
        given(userRepository.findAllById(anyList())).willReturn(List.of(active, withdrawn));

        // when: 차단 목록 조회
        BlockListResult result = blockService.blockList(1L);

        // then: 정상 유저는 이름만, 탈퇴 유저는 '탈퇴한 사용자'
        assertThat(result.blocks())
                .extracting(item -> item.blockedUserId() + ":" + item.blockedUserName())
                .containsExactly("10:길동", "20:탈퇴한 사용자");
    }

    private User user(String familyName, String givenName, Instant deletedAt) {
        User user = User.create(
                "nick", familyName, "familyHash", givenName, "givenHash",
                Gender.MALE, "organization", "organizationHash", "aff", "affHash", "affNo", "affNoHash",
                "2000-01-01", "birthHash", "phone", "phoneHash", "email", "emailHash", null, null);
        if (deletedAt != null) {
            ReflectionTestUtils.setField(user, "deletedAt", deletedAt);
        }
        return user;
    }

    @Test
    @DisplayName("존재하지 않는 유저를 차단하면 USER_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void block_unknown_user_throws() {
        // given: 대상 유저가 마스터 데이터에 없음
        given(userRepository.existsById(2L)).willReturn(false);

        // when & then: 조용히 저장하지 않고 404로 거절
        assertThatThrownBy(() -> blockService.block(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(blockRepository).should(never()).save(any());
    }
}
