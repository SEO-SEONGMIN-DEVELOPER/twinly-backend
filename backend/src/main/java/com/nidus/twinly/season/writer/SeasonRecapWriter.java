package com.nidus.twinly.season.writer;

import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.season.domain.SeasonRecapData;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonRecap;
import com.nidus.twinly.season.repository.SeasonRecapRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonRecapWriter {

    private final SeasonRepository seasonRepository;
    private final SeasonRecapRepository seasonRecapRepository;
    private final AppNotificationFeedWriter appNotificationFeedWriter;
    private final ObjectMapper objectMapper;

    @Transactional
    public void create(Long seasonId, Map<Long, SeasonRecapData> dataByUserId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalStateException("리캡을 만들 시즌이 존재하지 않습니다."));
        if (season.getRecapCreatedAt() != null) {
            return;
        }

        Instant createdAt = Instant.now();

        seasonRecapRepository.saveAll(dataByUserId.entrySet().stream()
                .map(entry -> SeasonRecap.create(entry.getKey(), seasonId, objectMapper.writeValueAsString(entry.getValue()), createdAt))
                .toList());

        season.markRecapCreated(createdAt);

        InfoLog.log(log, "시즌 리캡을 만들었습니다", field("seasonId", seasonId), field("population", dataByUserId.size()));
    }

    @Transactional
    public void notifyAll(Long seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalStateException("리캡을 알릴 시즌이 존재하지 않습니다."));
        if (season.getRecapCreatedAt() == null || season.getRecapNotifiedAt() != null) {
            return;
        }

        List<Long> userIds = seasonRecapRepository.findAllNotifiableUserIdsBySeasonId(seasonId);
        int month = KstTimes.toLocalDateTime(season.getStartedAt()).getMonthValue();

        appNotificationFeedWriter.writeSeasonRecap(userIds, seasonId, month);

        season.markRecapNotified(Instant.now());

        InfoLog.log(log, "시즌 리캡 알림을 만들었습니다", field("seasonId", seasonId), field("recipients", userIds.size()));
    }

    @Transactional
    public void deleteExpired(Long servingSeasonId) {
        int deleted = seasonRecapRepository.deleteAllBySeasonIdLessThan(servingSeasonId);

        if (deleted > 0) {
            InfoLog.log(log, "제공이 끝난 시즌 리캡을 지웠습니다", field("servingSeasonId", servingSeasonId), field("deleted", deleted));
        }
    }
}
