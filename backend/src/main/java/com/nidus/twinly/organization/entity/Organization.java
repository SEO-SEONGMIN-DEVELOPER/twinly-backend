package com.nidus.twinly.organization.entity;

import com.nidus.twinly.organization.domain.OrganizationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrganizationType type;

    public static Organization createCompany(String name) {
        Organization organization = new Organization();
        organization.name = name;
        organization.type = OrganizationType.COMPANY;
        return organization;
    }
}
