package com.nidus.twinly.user.service;

import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.logging.WarnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnUserDeletionService {

    private static final int CHUNK_SIZE = 500;
    private static final int MAX_CHUNKS = 100;

    private final WithdrawnUserChunkDeletionService chunkDeletionService;

    public void deleteAll() {
        Instant now = Instant.now();

        int totalDeleted = 0;
        for (int chunk = 0; chunk < MAX_CHUNKS; chunk++) {
            int deleted = chunkDeletionService.deleteChunk(now, CHUNK_SIZE);
            totalDeleted += deleted;

            if (deleted < CHUNK_SIZE) {
                InfoLog.log(log, "탈퇴 유저 파기를 완료했습니다.", field("deletedCount", totalDeleted));
                return;
            }
        }

        WarnLog.log(log, "탈퇴 유저 파기가 최대 청크 수를 초과해 중단됐습니다.", field("maxChunks", MAX_CHUNKS), field("deletedCount", totalDeleted));
    }
}
