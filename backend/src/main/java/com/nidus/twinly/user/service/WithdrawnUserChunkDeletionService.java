package com.nidus.twinly.user.service;

import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawnUserChunkDeletionService {

    private final UserRepository userRepository;

    @Transactional
    public int deleteChunk(Instant now, int chunkSize) {
        List<User> users = userRepository
                .findAllByDeletedAtIsNullAndWithdrawalScheduledAtLessThanEqual(now, PageRequest.of(0, chunkSize));

        users.forEach(User::delete);

        return users.size();
    }
}
