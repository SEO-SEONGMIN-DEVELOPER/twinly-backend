package com.nidus.twinly.main.service;

import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.main.dto.result.MainTabResult;
import com.nidus.twinly.main.dto.result.MainTabSeasonResult;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainService {

    private final CurrentSeasonReader currentSeasonReader;
    private final ChatRepository chatRepository;
    private final AppNotificationFeedRepository appNotificationFeedRepository;

    public MainTabResult mainTab(Long userId) {
        Season season = currentSeasonReader.read();

        Instant now = Instant.now();
        long totalMillis = Duration.between(season.getStartedAt(), season.getEndedAt()).toMillis();
        long elapsedMillis = Duration.between(season.getStartedAt(), now).toMillis();
        long progressPercent = Math.min(100, Math.max(0, elapsedMillis * 100 / totalMillis));

        MainTabSeasonResult seasonResult = new MainTabSeasonResult(season.getId(), now, progressPercent + "%");

        return new MainTabResult(
                seasonResult,
                chatRepository.countUnreadRoomsByUserId(userId),
                appNotificationFeedRepository.countByUserIdAndReadAtIsNull(userId)
        );
    }
}
