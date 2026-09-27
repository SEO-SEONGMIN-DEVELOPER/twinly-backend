package com.nidus.twinly.purchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "early_signup_grant_counter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarlySignupGrantCounter {

    public static final int SINGLETON_ID = 1;
    public static final int LIMIT = 300;

    @Id
    @Column(columnDefinition = "TINYINT")
    private Integer id;

    private int assignedCount;

    public boolean isFull() {
        return assignedCount >= LIMIT;
    }

    public void increase() {
        assignedCount++;
    }
}
