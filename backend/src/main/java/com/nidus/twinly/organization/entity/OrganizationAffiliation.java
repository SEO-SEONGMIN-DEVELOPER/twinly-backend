package com.nidus.twinly.organization.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "organization_affiliations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationAffiliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizationId;

    @Column(length = 100)
    private String name;
}
