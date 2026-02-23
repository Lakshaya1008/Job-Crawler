package com.jobmarket.engine.domain.job;

import com.jobmarket.engine.domain.company.Company;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which company is hiring
    // ManyToOne → many jobs can belong to one company
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // Normalized role cluster — not raw title
    // "Java Backend Engineer" → "BACKEND"
    // This is what we compare for dedup, not the raw string
    @Column(name = "normalized_role", nullable = false)
    private String normalizedRole;

    // Normalized location cluster
    // "Bangalore / Remote" → "BANGALORE_OR_REMOTE"
    @Column(name = "normalized_location", nullable = false)
    private String normalizedLocation;

    // THE dedup key: normalized_company + normalized_role + normalized_location
    // UNIQUE constraint means database rejects duplicate fingerprints
    // If fingerprint already exists → same job, don't create new row
    @Column(name = "fingerprint", nullable = false, unique = true)
    private String fingerprint;

    // When did we FIRST see evidence of this job?
    // Set on creation, never changed
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    // When did we LAST see evidence of this job?
    // Updated every time a new observation comes in
    // Lifecycle is DERIVED from this — we never store "active=true"
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Many-to-many with Skill through JobSkill
    // Skills attached ONLY after job is validated
    // Never used for fingerprinting — only for analytics
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<JobSkill> jobSkills = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.firstSeenAt = LocalDateTime.now();
        this.lastSeenAt = LocalDateTime.now();
    }

    // The ONLY allowed mutation on a Job
    // Called when a new observation confirms this job still exists
    // We update last_seen_at — that's it. Nothing else changes.
    public void recordObservation(LocalDateTime observedAt) {
        if (observedAt.isAfter(this.lastSeenAt)) {
            this.lastSeenAt = observedAt;
        }
    }
}
