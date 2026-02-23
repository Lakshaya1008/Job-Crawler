package com.jobmarket.engine.service.analytics;

import com.jobmarket.engine.domain.job.Job;
import com.jobmarket.engine.domain.job.JobSkill;
import com.jobmarket.engine.domain.job.JobSkillId;
import com.jobmarket.engine.domain.job.JobRepository;
import com.jobmarket.engine.domain.skill.Skill;
import com.jobmarket.engine.domain.skill.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExtractorService {

    private final SkillRepository skillRepository;
    private final JobRepository jobRepository;

    /**
     * Skill dictionary — canonical lowercase skill names.
     *
     * Memory rule: dictionary-based only, no ML, no NLP libraries.
     * Fully deterministic. Fully debuggable.
     * If "spring boot" appears in text → skill "spring boot" detected. Period.
     *
     * WHY a Set and not a List?
     * Set has O(1) lookup vs List's O(n). With 100+ skills, this matters.
     * Also prevents duplicate skill entries automatically.
     */
    private static final Set<String> SKILL_DICTIONARY = Set.of(
            // JVM
            "java", "kotlin", "scala",
            // Frameworks
            "spring", "spring boot", "spring mvc", "spring security", "hibernate",
            "micronaut", "quarkus",
            // Frontend
            "react", "angular", "vue", "javascript", "typescript", "html", "css",
            // Backend
            "node.js", "express", "django", "flask", "fastapi",
            // Databases
            "postgresql", "mysql", "mongodb", "redis", "elasticsearch",
            "cassandra", "oracle",
            // Cloud & DevOps
            "aws", "gcp", "azure", "docker", "kubernetes", "jenkins",
            "terraform", "ansible", "linux",
            // Data
            "python", "spark", "kafka", "airflow", "pandas", "sql",
            // Tools
            "git", "maven", "gradle", "jira", "rest api", "graphql",
            "microservices", "rabbitmq"
    );

    /**
     * Extracts skills from raw job description text and attaches them to the job.
     *
     * Memory rule: Only called on VALIDATED jobs (lifecycle = ACTIVE or known good).
     * Never called on raw crawled text — text must be from a resolved job first.
     *
     * Process:
     *   1. Scan description text for dictionary matches
     *   2. For each match → find or create Skill in DB
     *   3. Create JobSkill mapping if not already exists
     */
    @Transactional
    public List<Skill> extractAndAttach(Job job, String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            log.debug("No description for job {} — skipping skill extraction", job.getId());
            return List.of();
        }

        String text = rawDescription.toLowerCase();
        List<Skill> extracted = new ArrayList<>();

        for (String skillName : SKILL_DICTIONARY) {
            // Check if skill keyword appears in text
            // Using word boundary check to avoid "java" matching "javascript" wrongly
            if (containsSkill(text, skillName)) {
                // Find existing skill in dictionary or create new entry
                Skill skill = skillRepository.findByName(skillName)
                        .orElseGet(() -> {
                            log.debug("New skill added to dictionary: {}", skillName);
                            return skillRepository.save(
                                    Skill.builder().name(skillName).build()
                            );
                        });

                // Attach to job via JobSkill mapping
                // The composite PK (job_id, skill_id) prevents duplicates at DB level
                JobSkillId id = new JobSkillId(job.getId(), skill.getId());
                boolean alreadyMapped = job.getJobSkills().stream()
                        .anyMatch(js -> js.getId().equals(id));

                if (!alreadyMapped) {
                    JobSkill jobSkill = JobSkill.builder()
                            .id(id)
                            .job(job)
                            .skill(skill)
                            .build();
                    job.getJobSkills().add(jobSkill);
                    extracted.add(skill);
                    log.debug("Skill '{}' attached to job {}", skillName, job.getId());
                }
            }
        }

        if (!extracted.isEmpty()) {
            jobRepository.save(job);
            log.info("Extracted {} skills for job {}", extracted.size(), job.getId());
        }

        return extracted;
    }

    /**
     * Checks if skill keyword appears as a meaningful unit in the text.
     *
     * Simple approach: check if text contains the skill string.
     * For single words like "java" — risk of false match in "javascript".
     * Mitigation: "javascript" is also in our dictionary, so it's detected correctly.
     * Longer phrases like "spring boot" have natural boundary protection.
     */
    private boolean containsSkill(String text, String skill) {
        // For single-word skills, add space checks to reduce false matches
        if (!skill.contains(" ")) {
            return text.contains(" " + skill + " ")
                    || text.startsWith(skill + " ")
                    || text.endsWith(" " + skill)
                    || text.equals(skill);
        }
        // Multi-word skills — direct contains is fine
        return text.contains(skill);
    }
}
