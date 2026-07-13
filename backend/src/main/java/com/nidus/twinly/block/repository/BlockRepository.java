package com.nidus.twinly.block.repository;

import com.nidus.twinly.block.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    void deleteByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    List<Block> findAllByUserId(Long userId);
}