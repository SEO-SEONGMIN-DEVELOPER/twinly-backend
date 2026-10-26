package com.nidus.twinly.season.service;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.season.domain.SeasonRecapPeriod;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.generator.SeasonRecapGenerator;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.season.writer.SeasonRecapWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonRecapService {

    private static final LocalTime NOTIFY_TIME = LocalTime.of(10, 0);

    private final SeasonRepository seasonRepository;
    private final SeasonRecapGenerator seasonRecapGenerator;
    private final SeasonRecapWriter seasonRecapWriter;

    @Transactional(propagation = Propagation.SUPPORTS)
    public void prepare(Instant now) {
        seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().ifPresent(current ->
                seasonRepository.findFirstByIdLessThanOrderByIdDesc(current.getId()).ifPresent(previous ->
                        prepare(previous, current, now)));
    }

    private void prepare(Season previous, Season current, Instant now) {
        seasonRecapWriter.deleteExpired(previous.getId());

        if (previous.getRecapCreatedAt() == null) {
            seasonRecapWriter.create(previous.getId(), seasonRecapGenerator.generate(previous.getId(), SeasonRecapPeriod.of(previous, current)));
        }

        if (!now.isBefore(notifyAt(current))) {
            seasonRecapWriter.notifyAll(previous.getId());
        }
    }

    private Instant notifyAt(Season current) {
        return KstTimes.toInstant(KstTimes.toLocalDateTime(current.getCreatedAt()).toLocalDate().atTime(NOTIFY_TIME));
    }
}
