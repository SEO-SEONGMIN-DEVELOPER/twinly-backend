package com.nidus.twinly.purchase.entity;

import com.nidus.twinly.organization.domain.OrganizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pool_counter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PoolCounter {

    public static final int POOL_SIZE = 80;

    @Id
    @Column(columnDefinition = "TINYINT")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrganizationType organizationType;

    private int firstPoolNumber;

    private int assignedCount;

    public int poolNumberAt(int index) {
        return firstPoolNumber + index / POOL_SIZE;
    }

    public int nextPoolNumber() {
        return poolNumberAt(assignedCount);
    }

    public void resetTo(int assignedCount) {
        this.assignedCount = assignedCount;
    }

    public void increase() {
        assignedCount++;
    }
}
