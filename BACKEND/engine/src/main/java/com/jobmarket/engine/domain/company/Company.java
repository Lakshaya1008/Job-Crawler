package com.jobmarket.engine.domain.company;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "company")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // This is the dedup key — normalized once, never changes
    // "HCL Tech", "HCL Technologies", "HCL" all become the same normalized_name
    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    // Human-readable version — what we show in APIs
    // Can be "HCL Technologies" even if normalized is "hcl technologies"
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // One company has many aliases ("TCS", "Tata Consultancy", "Tata CS")
    // CascadeType.ALL → if company is saved, aliases save too
    // mappedBy = "company" → CompanyAlias owns the FK side
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CompanyAlias> aliases = new ArrayList<>();

    // Called automatically before first save
    // Sets created_at so we never forget it
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
