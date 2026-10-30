package com.nidus.twinly.season.service;

import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.result.SeasonReservationResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.season.writer.SeasonRowLinker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonReservationService {

    private final SeasonRepository seasonRepository;
    private final CurrentSeasonReader currentSeasonReader;
    private final SeasonRowLinker seasonRowLinker;
    private final SeasonService seasonService;

    @Transactional
    public SeasonReservationResult reserve(SeasonChangeCommand command, Instant now) {
        if (!command.startedAt().isBefore(command.endedAt())) {
            throw new BusinessException(ErrorCode.INVALID_SEASON_PERIOD);
        }

        Season current = currentSeasonReader.read();

        if (!command.startedAt().isAfter(now)
                || !command.startedAt().equals(KstTimes.toInstant(current.nextStartDate().atStartOfDay()))
                || !KstTimes.toLocalDateTime(command.endedAt()).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            throw new BusinessException(ErrorCode.INVALID_SEASON_RESERVATION);
        }
        if (seasonRepository.existsByIsActiveFalseAndActivatedAtIsNull()) {
            throw new BusinessException(ErrorCode.SEASON_RESERVATION_ALREADY_EXISTS);
        }

        Season reserved = seasonRepository.save(Season.reserve(command.startedAt(), command.endedAt()));

        seasonRowLinker.link(current);
        seasonRowLinker.link(reserved);

        InfoLog.log(log, "시즌 전환을 예약했습니다.", field("seasonId", reserved.getId()), field("startedAt", reserved.getStartedAt()));

        return new SeasonReservationResult(reserved.getId(), reserved.getStartedAt(), reserved.getEndedAt());
    }

    @Transactional
    public void cancel() {
        Season reserved = seasonRepository.findFirstByIsActiveFalseAndActivatedAtIsNullOrderByStartedAtAsc()
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_RESERVATION_NOT_FOUND));

        seasonRowLinker.unlink(reserved);
        seasonRepository.delete(reserved);

        InfoLog.log(log, "시즌 전환 예약을 취소했습니다.", field("seasonId", reserved.getId()));
    }

    @Transactional
    public void activateDue(Instant now) {
        seasonRepository.findFirstByIsActiveFalseAndActivatedAtIsNullAndStartedAtLessThanEqualOrderByStartedAtAsc(now)
                .ifPresent(reserved -> {
                    seasonService.activateReserved(reserved, now);

                    InfoLog.log(log, "예약한 시즌으로 전환했습니다.", field("seasonId", reserved.getId()));
                });
    }
}
