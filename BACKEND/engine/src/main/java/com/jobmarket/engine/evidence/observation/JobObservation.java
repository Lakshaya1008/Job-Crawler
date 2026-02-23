package com.jobmarket.engine.evidence.observation;

import com.jobmarket.engine.crawler.attempt.CrawlAttempt;
import com.jobmarket.engine.evidence.source.JobSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_observation")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which listing page we saw
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_source_id", nullable = false)
    private JobSource jobSource;

    // Which crawl attempt produced this observation
    // THIS IS THE KEY DESIGN: observation is linked to the crawl that found it
    // If we have no observation but the crawl failed → job might still exist
    // If we have no observation and crawl SUCCEEDED → job probably gone
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crawl_attempt_id", nullable = false)
    private CrawlAttempt crawlAttempt;

    // Exact timestamp when we saw this job
    // NEVER UPDATED — this record is immutable once written
    // append only — Rule 2 from memory
    @Column(name = "observed_at", nullable = false, updatable = false)
    private LocalDateTime observedAt;

    // Raw job title exactly as the site showed it
    // "Sr. Java Developer", "Backend Engineer - 2024"
    // Nullable because older observations might not have captured it
    // Used for debugging dedup mistakes — can trace back to raw claim
    @Column(name = "raw_title")
    private String rawTitle;

    // No @PrePersist needed — caller sets observedAt explicitly
    // We want the observation to record WHEN the crawler saw it
    // not when we saved it to DB (those could differ by seconds/minutes)
}
