package com.nidus.twinly.purchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    public static final int SINGLETON_ID = 1;
    public static final int FIRST_POOL_NUMBER = 13;
    public static final int POOL_SIZE = 80;

    @Id
    @Column(columnDefinition = "TINYINT")
    private Integer id;

    private int assignedCount;

    public static int poolNumberAt(int index) {
        return FIRST_POOL_NUMBER + index / POOL_SIZE;
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
