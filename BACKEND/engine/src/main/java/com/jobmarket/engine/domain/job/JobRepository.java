package com.jobmarket.engine.domain.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    // THE most important query in the entire system
    // Called by JobResolverService during dedup
    // If fingerprint exists → same job, return it
    // If not → new job, create it
    Optional<Job> findByFingerprint(String fingerprint);

    // Used by lifecycle service to find jobs not seen recently
    // "Give me all jobs whose last observation was before X time"
    // This is how we detect potentially inactive jobs
    List<Job> findByLastSeenAtBefore(LocalDateTime threshold);

    // Used by analytics API: jobs seen recently (active window)
    List<Job> findByLastSeenAtAfter(LocalDateTime since);

    // Used by analytics: all jobs from a specific company
    List<Job> findByCompanyId(Long companyId);
}
