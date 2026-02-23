package com.jobmarket.engine.domain.job;

import com.jobmarket.engine.domain.skill.Skill;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_skill")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSkill {

    // Composite primary key — defined in separate class
    // Combination of job_id + skill_id must be unique
    // Same skill can't be attached to same job twice
    @EmbeddedId
    private JobSkillId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("jobId")   // tells JPA which field in JobSkillId this maps to
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id")
    private Skill skill;
}
