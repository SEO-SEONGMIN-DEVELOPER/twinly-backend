package com.nidus.twinly.legal.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "policy_names")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String name;

    @Column(length = 100)
    private String identifier;

    private Boolean requiresAgreement;

    private Boolean isDeprecated;
}
