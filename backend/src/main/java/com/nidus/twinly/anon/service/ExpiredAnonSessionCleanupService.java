package com.nidus.twinly.anon.service;

import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiredAnonSessionCleanupService {

    private static final int CHUNK_SIZE = 200;
    private static final int MAX_CHUNKS = 100;

    private final AnonSessionRepository anonSessionRepository;
    private final ExpiredAnonSessionDeletionService expiredAnonSessionDeletionService;

    public void deleteExpired() {
        Instant now = Instant.now();
        long afterId = 0L;
        int deleted = 0;
        int failed = 0;

        for (int chunk = 0; chunk < MAX_CHUNKS; chunk++) {
            List<Long> anonSessionIds = anonSessionRepository.findExpiredIds(now, afterId, PageRequest.of(0, CHUNK_SIZE));

            for (Long anonSessionId : anonSessionIds) {
                if (deleteQuietly(anonSessionId)) {
                    deleted++;
                } else {
                    failed++;
                }
            }

            if (anonSessionIds.size() < CHUNK_SIZE) {
                InfoLog.log(log, "만료된 익명 세션을 정리했습니다.", field("deletedCount", deleted), field("failedCount", failed));
                return;
            }

            afterId = anonSessionIds.getLast();
        }

        WarnLog.log(log, "만료된 익명 세션 정리가 최대 청크 수를 초과해 중단됐습니다.",
                field("maxChunks", MAX_CHUNKS), field("deletedCount", deleted), field("failedCount", failed));
    }

    private boolean deleteQuietly(Long anonSessionId) {
        try {
            expiredAnonSessionDeletionService.deleteFiles(anonSessionId);
            expiredAnonSessionDeletionService.deleteRows(anonSessionId);
            return true;
        } catch (RuntimeException e) {
            ErrorLog.warn(log, ErrorCode.ANON_SESSION_CLEANUP_FAILED.name(), String.valueOf(anonSessionId), e)
                    .log("만료된 익명 세션 정리 실패. 다음 주기에 다시 시도합니다.");
            return false;
        }
    }
}
