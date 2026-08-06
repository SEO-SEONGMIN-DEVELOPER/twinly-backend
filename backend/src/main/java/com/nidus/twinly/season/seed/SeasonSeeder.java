package com.nidus.twinly.season.seed;

import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@Profile({"stage", "local"})
@RequiredArgsConstructor
public class SeasonSeeder implements ApplicationRunner {

    private static final int STARTED_DAYS_BEFORE = 30;
    private static final int ENDED_DAYS_AFTER = 335;

    private final SeasonRepository seasonRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().isPresent()) {
            return;
        }

        Instant now = Instant.now();
        Season season = seasonRepository.save(Season.create(
                now.minus(STARTED_DAYS_BEFORE, ChronoUnit.DAYS),
                now.plus(ENDED_DAYS_AFTER, ChronoUnit.DAYS)
        ));

        log.info("활성 시즌이 없어 시드 시즌을 생성했습니다. seasonId={}", season.getId());
    }
}
