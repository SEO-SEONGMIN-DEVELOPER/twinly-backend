package com.nidus.twinly.user.service;

import com.nidus.twinly.schoolverification.repository.StudentIdSubmissionRepository;
import com.nidus.twinly.season.repository.SeasonRecapRepository;
import com.nidus.twinly.twinupdate.service.TwinUpdateService;
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
    private final StudentIdSubmissionRepository studentIdSubmissionRepository;
    private final SeasonRecapRepository seasonRecapRepository;
    private final TwinUpdateService twinUpdateService;

    @Transactional
    public int deleteChunk(Instant now, int chunkSize) {
        List<User> users = userRepository
                .findAllByDeletedAtIsNullAndWithdrawalScheduledAtLessThanEqual(now, PageRequest.of(0, chunkSize));

        users.forEach(User::delete);

        if (!users.isEmpty()) {
            List<Long> userIds = users.stream().map(User::getId).toList();

            studentIdSubmissionRepository.cancelPendingByUserIdIn(userIds, Instant.now());
            seasonRecapRepository.deleteAllByUserIdIn(userIds);
        }

        users.forEach(user -> twinUpdateService.discardInProgress(user.getId()));

        return users.size();
    }
}
