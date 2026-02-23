package com.jobmarket.engine.domain.job;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// Composite PK class — JPA requires this for multi-column primary keys
// Must implement Serializable
// Must implement equals() and hashCode() — Lombok @EqualsAndHashCode does this
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JobSkillId implements Serializable {

    private Long jobId;
    private Long skillId;
}
