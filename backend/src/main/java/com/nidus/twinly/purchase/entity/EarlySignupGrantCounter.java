package com.nidus.twinly.purchase.entity;

import com.nidus.twinly.common.domain.Gender;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "early_signup_grant_counters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarlySignupGrantCounter {

    public static final int LIMIT_PER_GENDER = 150;

    @Id
    @Enumerated(EnumType.STRING)
    private Gender gender;

    private int assignedCount;

    public boolean isFull() {
        return assignedCount >= LIMIT_PER_GENDER;
    }

    public void increase() {
        assignedCount++;
    }
}
