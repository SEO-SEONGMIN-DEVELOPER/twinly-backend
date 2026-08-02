package com.nidus.twinly.school.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "school_affiliations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolAffiliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long schoolId;

    @Column(length = 100)
    private String name;
}
