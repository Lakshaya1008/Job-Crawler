package com.jobmarket.engine.crawler.target;

import com.jobmarket.engine.evidence.source.SourceSite;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "crawl_target")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which site this target belongs to
    // Used to look up crawl_delay_seconds, max_retries, crawl_enabled
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_site_id", nullable = false)
    private SourceSite sourceSite;

    // The actual URL the crawler will fetch
    // e.g. "https://internshala.com/jobs/backend-developer"
    @Column(name = "url", nullable = false)
    private String url;

    // Can deactivate specific targets without deleting them
    // e.g. if a specific page is gone but the site is still active
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
