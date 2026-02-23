package com.jobmarket.engine.domain.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    // Used during skill extraction: does this skill already exist in dictionary?
    // If yes → reuse it. If no → create new entry.
    // Prevents duplicate "java", "Java", "JAVA" entries (normalized at service layer)
    Optional<Skill> findByName(String name);
}
