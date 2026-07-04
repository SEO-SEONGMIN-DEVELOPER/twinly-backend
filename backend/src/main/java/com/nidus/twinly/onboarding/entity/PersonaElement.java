package com.nidus.twinly.onboarding.entity;

import com.nidus.twinly.common.survey.PersonaDimension;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "persona_elements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long anonSessionId;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PersonaDimension dimension;

    private String explanation;

    private Instant createdAt;

    public static PersonaElement create(Long anonSessionId, PersonaDimension dimension, String explanation) {
        PersonaElement personaElement = new PersonaElement();
        personaElement.anonSessionId = anonSessionId;
        personaElement.dimension = dimension;
        personaElement.explanation = explanation;
        personaElement.createdAt = Instant.now();

        return personaElement;
    }

    public void changeExplanation(String explanation) {
        this.explanation = explanation;
    }
}