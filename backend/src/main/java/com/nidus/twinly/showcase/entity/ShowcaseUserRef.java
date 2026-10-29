package com.nidus.twinly.showcase.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "showcase_user_refs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseUserRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long showcaseId;

    private Long userId;

    private Long userRef;

    private Instant createdAt;

    public static ShowcaseUserRef create(Long showcaseId, Long userId, Long userRef) {
        ShowcaseUserRef showcaseUserRef = new ShowcaseUserRef();

        showcaseUserRef.showcaseId = showcaseId;
        showcaseUserRef.userId = userId;
        showcaseUserRef.userRef = userRef;
        showcaseUserRef.createdAt = Instant.now();

        return showcaseUserRef;
    }
}
