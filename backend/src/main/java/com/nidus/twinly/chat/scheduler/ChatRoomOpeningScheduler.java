package com.nidus.twinly.chat.scheduler;

import com.nidus.twinly.chat.service.ChatRoomOpeningService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChatRoomOpeningScheduler {

    private final ChatRoomOpeningService chatRoomOpeningService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "openScheduledChatRooms", lockAtMostFor = "PT5M")
    public void openScheduled() {
        chatRoomOpeningService.openDue(Instant.now());
    }
}
