package com.nidus.twinly.main.service;

import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.main.dto.result.MainTabResult;
import com.nidus.twinly.main.dto.result.MainTabSeasonResult;
import com.nidus.twinly.notification.repository.NotificationRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MainService {

    @Value("${app.current-season-id}")
    private Long currentSeasonId;

    private final SeasonRepository seasonRepository;
    private final ChatRepository chatRepository;
    private final NotificationRepository notificationRepository;

    public MainTabResult mainTab(Long userId) {
        Season season = seasonRepository.findById(currentSeasonId)
                .orElseThrow(() -> new IllegalStateException("현재 시즌으로 설정된 시즌이 존재하지 않습니다: seasonId=" + currentSeasonId));

        Instant now = Instant.now();
        long totalMillis = Duration.between(season.getStartedAt(), season.getEndedAt()).toMillis();
        long elapsedMillis = Duration.between(season.getStartedAt(), now).toMillis();
        long progressPercent = Math.min(100, Math.max(0, elapsedMillis * 100 / totalMillis));

        MainTabSeasonResult seasonResult = new MainTabSeasonResult(season.getId(), now, progressPercent + "%");

        return new MainTabResult(
                seasonResult,
                chatRepository.countUnreadRoomsByUserId(userId),
                notificationRepository.countUnreadByUserId(userId)
        );
    }
}
