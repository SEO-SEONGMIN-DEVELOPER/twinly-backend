package com.nidus.twinly.season.reader;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.season.domain.SeasonScope;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CurrentSeasonReader {

    private final SeasonRepository seasonRepository;

    public Season read() {
        return seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new IllegalStateException("활성화된 시즌이 존재하지 않습니다."));
    }

    public LocalDate startDateOf(LocalDate date) {
        return scopeOf(date).startDate();
    }

    public SeasonScope scopeOf(LocalDate date) {
        return seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .map(current -> scopeOf(current, date))
                .orElse(SeasonScope.UNBOUNDED);
    }

    private SeasonScope scopeOf(Season current, LocalDate date) {
        LocalDate nextStartDate = current.nextStartDate();
        if (!date.isBefore(nextStartDate)) {
            return new SeasonScope(
                    seasonRepository.findFirstByIsActiveFalseAndActivatedAtIsNullOrderByStartedAtAsc().map(Season::getId).orElse(null),
                    nextStartDate);
        }

        if (!date.isBefore(current.startDate())) {
            return new SeasonScope(current.getId(), current.startDate());
        }

        return seasonRepository.findFirstByStartedAtBeforeOrderByStartedAtDesc(KstTimes.toInstant(date.plusDays(1).atStartOfDay()))
                .map(season -> new SeasonScope(season.getId(), season.startDate()))
                .orElse(SeasonScope.UNBOUNDED);
    }
}
