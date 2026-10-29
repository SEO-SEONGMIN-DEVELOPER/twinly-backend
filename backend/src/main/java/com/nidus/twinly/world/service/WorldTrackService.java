package com.nidus.twinly.world.service;

import com.nidus.twinly.common.logging.LogField;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.world.dto.command.WorldTrackSaveCommand;
import com.nidus.twinly.world.dto.request.WorldTrackObjectRequest;
import com.nidus.twinly.world.dto.request.WorldTrackPeriodRequest;
import com.nidus.twinly.world.dto.request.WorldTrackRequest;
import com.nidus.twinly.world.entity.WorldTrack;
import com.nidus.twinly.world.reader.WorldTrackBodyReader;
import com.nidus.twinly.world.repository.WorldTrackRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorldTrackService {

    private final WorldTrackSavePermits worldTrackSavePermits;
    private final WorldTrackBodyReader worldTrackBodyReader;
    private final Validator validator;
    private final UserRepository userRepository;
    private final WorldTrackRepository worldTrackRepository;

    public void saveTrack(WorldTrackSaveCommand command) {
        if (!worldTrackSavePermits.tryAcquire()) {
            throw new BusinessException(ErrorCode.WORLD_TRACK_SAVE_BUSY);
        }

        try {
            byte[] body = worldTrackBodyReader.readCompressed(command.contentEncoding(), command.body());
            WorldTrackRequest track = worldTrackBodyReader.parse(body);
            Long viewerUserId = validate(command, track);

            worldTrackRepository.upsert(command.poolNumber(), command.date(), command.sectorCode(), WorldTrack.toViewerUserId(viewerUserId), body);
        } finally {
            worldTrackSavePermits.release();
        }
    }

    private Long validate(WorldTrackSaveCommand command, WorldTrackRequest track) {
        Set<ConstraintViolation<WorldTrackRequest>> violations = validator.validate(track);
        if (!violations.isEmpty()) {
            ConstraintViolation<WorldTrackRequest> violation = violations.iterator().next();
            throw rejected(command, "세계 트랙 형식이 올바르지 않습니다.",
                    field("property", violation.getPropertyPath().toString()), field("violationCount", violations.size()));
        }

        if (!String.valueOf(command.poolNumber()).equals(track.poolNumber())
                || !command.date().toString().equals(track.date())
                || !command.sectorCode().equals(track.sectorCode())) {
            throw rejected(command, "경로와 본문의 풀·회차·구역이 다릅니다.",
                    field("bodyPoolNumber", track.poolNumber()), field("bodyDate", track.date()), field("bodySectorCode", track.sectorCode()));
        }

        validateTimes(command, track);

        if (!userRepository.existsByPoolNumber(command.poolNumber())) {
            throw rejected(command, "사용자가 없는 풀의 세계 트랙입니다.");
        }

        if (track.viewerUserId() == null) {
            return null;
        }

        Long viewerUserId = Long.valueOf(track.viewerUserId());
        if (!userRepository.existsByIdAndDeletedAtIsNull(viewerUserId)) {
            throw rejected(command, "없는 사용자의 개인 세계 트랙입니다.", field("viewerUserId", viewerUserId));
        }

        return viewerUserId;
    }

    private void validateTimes(WorldTrackSaveCommand command, WorldTrackRequest track) {
        try {
            periods(track)
                    .filter(period -> LocalDateTime.parse(period.start()).isAfter(LocalDateTime.parse(period.end())))
                    .findFirst()
                    .ifPresent(period -> {
                        throw rejected(command, "시작 시각이 끝 시각보다 늦습니다.", field("start", period.start()), field("end", period.end()));
                    });

            track.objects().stream()
                    .map(WorldTrackObjectRequest::changes)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .forEach(change -> LocalDateTime.parse(change.at()));
        } catch (DateTimeParseException e) {
            throw rejected(command, "있을 수 없는 시각입니다.", field("time", e.getParsedString()));
        }
    }

    private Stream<WorldTrackPeriodRequest> periods(WorldTrackRequest track) {
        return Stream.<Stream<? extends WorldTrackPeriodRequest>>of(
                track.actors().stream().flatMap(actor -> actor.segments().stream()),
                track.talks().stream(),
                track.gestures().stream(),
                track.events().stream(),
                track.items().stream()
        ).flatMap(periods -> periods);
    }

    private BusinessException rejected(WorldTrackSaveCommand command, String message, LogField... fields) {
        LogField[] keys = {field("poolNumber", command.poolNumber()), field("date", command.date()), field("sectorCode", command.sectorCode())};
        WarnLog.log(log, message, Stream.concat(Stream.of(keys), Stream.of(fields)).toArray(LogField[]::new));
        return new BusinessException(ErrorCode.INVALID_REQUEST, message);
    }
}
