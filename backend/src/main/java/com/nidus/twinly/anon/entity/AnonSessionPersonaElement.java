package com.nidus.twinly.anon.entity;

import com.nidus.twinly.common.persona.PersonaDimension;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "anon_session_persona_elements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonSessionPersonaElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long anonSessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PersonaDimension dimension;

    private String explanation;

    private Instant createdAt;

    public static AnonSessionPersonaElement create(Long anonSessionId, PersonaDimension dimension, String explanation) {
        AnonSessionPersonaElement element = new AnonSessionPersonaElement();
        element.anonSessionId = anonSessionId;
        element.dimension = dimension;
        element.explanation = explanation;
        element.createdAt = Instant.now();

        return element;
    }

    public void changeExplanation(String explanation) {
        this.explanation = explanation;
    }
}