package com.nidus.twinly.schoolverification.scheduler;

import com.nidus.twinly.schoolverification.service.StudentIdPhotoPurgeService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudentIdPhotoPurgeScheduler {

    private final StudentIdPhotoPurgeService studentIdPhotoPurgeService;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "purgeStudentIdPhotos", lockAtMostFor = "PT30M")
    public void purgeStudentIdPhotos() {
        studentIdPhotoPurgeService.purge();
    }
}
