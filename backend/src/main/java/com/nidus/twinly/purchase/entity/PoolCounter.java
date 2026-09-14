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
    public static final int POOL_SIZE = 50;

    @Id
    @Column(columnDefinition = "TINYINT")
    private Integer id;

    private int assignedCount;

    public int nextPoolNumber() {
        return assignedCount / POOL_SIZE + 1;
    }

    public void increase() {
        assignedCount++;
    }
}
