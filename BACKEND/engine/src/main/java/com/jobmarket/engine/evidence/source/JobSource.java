package com.jobmarket.engine.evidence.source;

import com.jobmarket.engine.domain.job.Job;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_source")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which logical job this listing belongs to (after dedup)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    // Which platform this listing came from
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_site_id", nullable = false)
    private SourceSite sourceSite;

    // The exact URL of this listing page
    // UNIQUE → same URL can't be two different JobSources
    // One listing page = one JobSource (Rule 7 from memory)
    @Column(name = "source_url", nullable = false, unique = true)
    private String sourceUrl;

    // Salary as raw text from the site — "₹8-12 LPA", "Not disclosed"
    // Nullable because many listings don't show salary
    // BELONGS HERE not in Job — it's a claim by this source, not truth
    @Column(name = "salary_text")
    private String salaryText;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    // Updated when we see this specific URL again
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.firstSeenAt = LocalDateTime.now();
        this.lastSeenAt = LocalDateTime.now();
    }

    // Called when this same URL is observed again
    public void recordObservation(LocalDateTime observedAt) {
        if (observedAt.isAfter(this.lastSeenAt)) {
            this.lastSeenAt = observedAt;
        }
    }
}
