package com.nidus.twinly.season.reader;

import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentSeasonReader {

    private final SeasonRepository seasonRepository;

    public Season read() {
        return seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new IllegalStateException("활성화된 시즌이 존재하지 않습니다."));
    }
}
