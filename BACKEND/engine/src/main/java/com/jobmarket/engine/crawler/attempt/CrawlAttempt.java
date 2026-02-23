package com.jobmarket.engine.crawler.attempt;

import com.jobmarket.engine.crawler.target.CrawlTarget;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawl_attempt")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crawl_target_id", nullable = false)
    private CrawlTarget crawlTarget;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    // Null until crawl completes — set when finished
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    // SUCCESS    → page loaded, jobs parsed
    // HTTP_FAIL  → site returned 4xx/5xx, or network timeout
    // PARSE_FAIL → page loaded but our parser couldn't extract jobs
    //
    // This distinction is critical:
    // HTTP_FAIL  → don't conclude anything about jobs, site was unreachable
    // PARSE_FAIL → site changed its HTML structure, our parser broke
    // SUCCESS    → we can trust the observation (or absence of observation)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CrawlStatus status;

    // HTTP response code — 200, 404, 503, etc.
    // Nullable because we might not get a response at all (timeout)
    @Column(name = "http_code")
    private Integer httpCode;

    // Error details if something went wrong
    // "Connection timeout", "404 Not Found", "NullPointerException at parser line 42"
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // How many job cards were successfully parsed in this crawl
    // If SUCCESS but jobs_found_count = 0 → parser probably broke silently
    // This is how you detect HTML structure changes on the site
    @Column(name = "jobs_found_count", nullable = false)
    @Builder.Default
    private Integer jobsFoundCount = 0;

    // Called when crawl finishes — sets completion fields
    public void complete(CrawlStatus status, Integer httpCode, String errorMessage, Integer jobsFound) {
        this.finishedAt = LocalDateTime.now();
        this.status = status;
        this.httpCode = httpCode;
        this.errorMessage = errorMessage;
        this.jobsFoundCount = jobsFound;
    }
}
