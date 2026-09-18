package com.nidus.twinly.user.service;

import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.user.config.WithdrawnUserDeletionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnUserDeletionService {

    private final WithdrawnUserChunkDeletionService chunkDeletionService;
    private final WithdrawnUserDeletionProperties withdrawnUserDeletionProperties;

    public void deleteAll() {
        Instant now = Instant.now();
        int chunkSize = withdrawnUserDeletionProperties.chunkSize();
        int maxChunks = withdrawnUserDeletionProperties.maxChunks();

        int totalDeleted = 0;
        for (int chunk = 0; chunk < maxChunks; chunk++) {
            int deleted = chunkDeletionService.deleteChunk(now, chunkSize);
            totalDeleted += deleted;

            if (deleted < chunkSize) {
                InfoLog.log(log, "탈퇴 유저 파기를 완료했습니다.", field("deletedCount", totalDeleted));
                return;
            }
        }

        WarnLog.log(log, "탈퇴 유저 파기가 최대 청크 수를 초과해 중단됐습니다.", field("maxChunks", maxChunks), field("deletedCount", totalDeleted));
    }
}
