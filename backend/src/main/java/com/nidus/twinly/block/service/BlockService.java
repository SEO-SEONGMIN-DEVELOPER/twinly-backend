package com.nidus.twinly.block.service;

import com.nidus.twinly.block.dto.result.BlockListItemResult;
import com.nidus.twinly.block.dto.result.BlockListResult;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    @Transactional
    public void block(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        if (!userRepository.existsById(blockedUserId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (blockRepository.existsByUserIdAndBlockedUserId(userId, blockedUserId)) {
            return;
        }

        blockRepository.save(Block.create(userId, blockedUserId));
    }

    @Transactional
    public void unblock(Long userId, Long blockedUserId) {
        blockRepository.deleteByUserIdAndBlockedUserId(userId, blockedUserId);
    }

    public BlockListResult blockList(Long userId) {
        List<Block> blocks = blockRepository.findAllByUserId(userId);

        if (blocks.isEmpty()) {
            return new BlockListResult(List.of());
        }

        List<Long> blockedUserIds = blocks.stream()
                .map(Block::getBlockedUserId)
                .toList();

        Map<Long, User> usersById = userRepository.findAllById(blockedUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<BlockListItemResult> items = blocks.stream()
                .map(block -> {
                    User user = usersById.get(block.getBlockedUserId());

                    String name = user.displayName();

                    return new BlockListItemResult(block.getBlockedUserId(), name);
                })
                .toList();

        return new BlockListResult(items);
    }
}
