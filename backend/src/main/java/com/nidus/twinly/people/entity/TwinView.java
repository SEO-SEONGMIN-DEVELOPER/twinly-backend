package com.nidus.twinly.people.entity;

import com.nidus.twinly.people.domain.TwinViewKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "twin_views")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long targetUserId;

    private Long viewerUserId;

    @Enumerated(EnumType.STRING)
    private TwinViewKind kind;

    private Instant viewedAt;

    private Instant createdAt;

    public static TwinView create(Long targetUserId, Long viewerUserId, TwinViewKind kind) {
        TwinView view = new TwinView();
        view.targetUserId = targetUserId;
        view.viewerUserId = viewerUserId;
        view.kind = kind;
        view.viewedAt = Instant.now();
        view.createdAt = Instant.now();
        return view;
    }
}
