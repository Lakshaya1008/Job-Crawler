package com.jobmarket.engine.evidence.source;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "source_site")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "linkedin", "naukri", "internshala", "company_site"
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    // After how many days of no sighting do we consider a job inactive?
    // LinkedIn might be 3 days, small company site might be 14 days
    // Per-site because different platforms update at different rates
    @Column(name = "inactive_threshold_days", nullable = false)
    private Integer inactiveThresholdDays;

    // After how many days of absence do we call a re-appearance a NEW hiring cycle?
    // vs just a gap in our crawling
    @Column(name = "repost_threshold_days", nullable = false)
    private Integer repostThresholdDays;

    // How much to trust observations from this site (0.0 to 1.0)
    // LinkedIn = 0.9, random small job board = 0.5
    // Used in confidence score calculation (V2) — stored now so V2 can use it
    @Column(name = "reliability_weight", nullable = false)
    private Double reliabilityWeight;

    // How long to wait between requests to this site (seconds)
    // Prevents getting blocked. LinkedIn needs more delay than a small site
    @Column(name = "crawl_delay_seconds", nullable = false)
    private Integer crawlDelaySeconds;

    // How many times to retry a failed crawl before giving up
    // Stored in DB so you can change per-site without redeploying
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    // Can pause crawling this site without touching code
    // If LinkedIn blocks us → set crawl_enabled=false, fix issue, re-enable
    @Column(name = "crawl_enabled", nullable = false)
    private Boolean crawlEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
