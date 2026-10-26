package com.nidus.twinly.twinupdate.entity;

import com.nidus.twinly.twinupdate.domain.TwinUpdateStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "twin_updates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID updateId;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private TwinUpdateStatus status;

    private Instant completedAt;

    private Instant createdAt;

    public static TwinUpdate create(Long userId) {
        TwinUpdate twinUpdate = new TwinUpdate();
        twinUpdate.updateId = UUID.randomUUID();
        twinUpdate.userId = userId;
        twinUpdate.status = TwinUpdateStatus.IN_PROGRESS;
        twinUpdate.createdAt = Instant.now();

        return twinUpdate;
    }

    public boolean isInProgress() {
        return status == TwinUpdateStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return status == TwinUpdateStatus.COMPLETED;
    }

    public void discard() {
        this.status = TwinUpdateStatus.DISCARDED;
    }

    public void complete(Instant completedAt) {
        this.status = TwinUpdateStatus.COMPLETED;
        this.completedAt = completedAt;
    }
}
