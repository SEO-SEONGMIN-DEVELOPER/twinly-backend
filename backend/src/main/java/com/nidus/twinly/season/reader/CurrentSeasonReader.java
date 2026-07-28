package com.nidus.twinly.season.reader;

import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/*
 * [멘토링 피드백 반영 완료]
 * DB에 활성 상태 컬럼을 추가(isActive)하고, 활성화되어있으면서 가장 높은 시즌 id를 가져오기
 */

@Component
@RequiredArgsConstructor
public class CurrentSeasonReader {

    private final SeasonRepository seasonRepository;

    public Season read() {
        return seasonRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new IllegalStateException("활성화된 시즌이 존재하지 않습니다."));
    }
}
