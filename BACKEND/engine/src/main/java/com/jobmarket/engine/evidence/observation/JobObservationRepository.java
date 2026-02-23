package com.jobmarket.engine.evidence.observation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JobObservationRepository extends JpaRepository<JobObservation, Long> {

    // All observations for a specific source — the evidence timeline
    // Used by insight API: "show me all sightings of this job"
    List<JobObservation> findByJobSourceIdOrderByObservedAtDesc(Long jobSourceId);

    // Observations within a time window — used for analytics
    List<JobObservation> findByObservedAtAfter(LocalDateTime since);

    // Custom JPQL query: get observations for a job across ALL its sources
    // We navigate: observation → job_source → job
    @Query("SELECT o FROM JobObservation o WHERE o.jobSource.job.id = :jobId ORDER BY o.observedAt DESC")
    List<JobObservation> findAllByJobId(@Param("jobId") Long jobId);
}
