package com.nidus.twinly.people.writer;

import com.nidus.twinly.people.domain.TwinViewKind;
import com.nidus.twinly.people.entity.TwinView;
import com.nidus.twinly.people.repository.TwinViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TwinViewWriter {

    private final TwinViewRepository twinViewRepository;

    @Async("twinViewTaskExecutor")
    public void write(Long targetUserId, Long viewerUserId, TwinViewKind kind) {
        if (targetUserId.equals(viewerUserId)) {
            return;
        }

        twinViewRepository.save(TwinView.create(targetUserId, viewerUserId, kind));
    }
}
