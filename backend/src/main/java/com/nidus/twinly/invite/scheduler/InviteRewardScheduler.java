package com.nidus.twinly.invite.scheduler;

import com.nidus.twinly.invite.service.InviteRewardGrantService;
import com.nidus.twinly.invite.writer.InviteRewardWriter;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InviteRewardScheduler {

    private static final Duration DI_HASH_RETENTION = Duration.ofDays(365);

    private final InviteRewardGrantService inviteRewardGrantService;
    private final InviteRewardWriter inviteRewardWriter;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "processInviteRewards", lockAtMostFor = "PT10M")
    public void processPending() {
        inviteRewardGrantService.processPending();
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "clearInviteDiHashes", lockAtMostFor = "PT10M")
    public void clearExpiredDiHashes() {
        inviteRewardWriter.clearDiHashesOfWithdrawnBefore(Instant.now().minus(DI_HASH_RETENTION));
    }
}
