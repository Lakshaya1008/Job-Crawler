package com.jobmarket.engine.evidence.source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobSourceRepository extends JpaRepository<JobSource, Long> {

    // Dedup check: have we seen this exact URL before?
    Optional<JobSource> findBySourceUrl(String sourceUrl);

    // All sources for a given logical job — how many sites confirm this job?
    List<JobSource> findByJobId(Long jobId);
}
