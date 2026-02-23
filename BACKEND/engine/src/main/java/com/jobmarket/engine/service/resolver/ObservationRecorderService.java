package com.jobmarket.engine.service.resolver;

import com.jobmarket.engine.crawler.attempt.CrawlAttempt;
import com.jobmarket.engine.domain.job.Job;
import com.jobmarket.engine.evidence.observation.JobObservation;
import com.jobmarket.engine.evidence.observation.JobObservationRepository;
import com.jobmarket.engine.evidence.source.JobSource;
import com.jobmarket.engine.evidence.source.JobSourceRepository;
import com.jobmarket.engine.evidence.source.SourceSite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObservationRecorderService {

    private final JobSourceRepository jobSourceRepository;
    private final JobObservationRepository jobObservationRepository;

    /**
     * Records one confirmed sighting of a job listing.
     *
     * Called AFTER JobResolverService resolves the logical job.
     *
     * Two things happen:
     *   1. JobSource resolved (created if new URL, updated if known URL)
     *   2. JobObservation ALWAYS created — append only, every sighting
     *
     * Memory Rule 7: One listing page → one JobSource
     * Memory Rule 8: One JobSource → many observations
     * Memory Rule 2: Never overwrite. Always append new observation row.
     * Memory Rule 5: Salary stored on JobSource only.
     */
    @Transactional
    public JobObservation record(Job job,
                                 SourceSite sourceSite,
                                 CrawlAttempt crawlAttempt,
                                 String sourceUrl,
                                 String rawTitle,
                                 String salaryText) {

        // STEP 1: Resolve JobSource
        // Same URL seen before → update last_seen_at
        // New URL → create new JobSource row
        JobSource jobSource = jobSourceRepository.findBySourceUrl(sourceUrl)
                .map(existing -> {
                    log.debug("Existing JobSource for URL: {}", sourceUrl);
                    existing.recordObservation(LocalDateTime.now());
                    return jobSourceRepository.save(existing);
                })
                .orElseGet(() -> {
                    log.info("New JobSource discovered: {}", sourceUrl);
                    JobSource newSource = JobSource.builder()
                            .job(job)
                            .sourceSite(sourceSite)
                            .sourceUrl(sourceUrl)
                            .salaryText(salaryText)
                            .build();
                    return jobSourceRepository.save(newSource);
                });

        // STEP 2: Always create a new JobObservation
        // Never update existing. Never skip. Always append.
        // Frequency of observations becomes evidence of job activity in V2.
        JobObservation observation = JobObservation.builder()
                .jobSource(jobSource)
                .crawlAttempt(crawlAttempt)
                .observedAt(LocalDateTime.now())
                .rawTitle(rawTitle)
                .build();

        JobObservation saved = jobObservationRepository.save(observation);

        log.debug("Observation recorded: jobId={}, url={}, at={}",
                job.getId(), sourceUrl, saved.getObservedAt());

        return saved;
    }
}
