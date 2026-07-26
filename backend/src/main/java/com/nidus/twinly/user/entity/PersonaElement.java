package com.nidus.twinly.user.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.common.persona.PersonaDimension;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "persona_elements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private PersonaDimension dimension;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    private Instant createdAt;

    public static PersonaElement create(Long userId, PersonaDimension dimension, String explanation, Instant createdAt) {
        PersonaElement element = new PersonaElement();
        element.userId = userId;
        element.dimension = dimension;
        element.explanation = explanation;
        element.createdAt = createdAt;

        return element;
    }
}
