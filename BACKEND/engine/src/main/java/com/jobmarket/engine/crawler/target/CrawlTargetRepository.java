package com.jobmarket.engine.crawler.target;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlTargetRepository extends JpaRepository<CrawlTarget, Long> {

    // Scheduler calls this every cycle — only fetch active targets
    List<CrawlTarget> findByActiveTrue();

    // All active targets for a specific site
    List<CrawlTarget> findBySourceSiteIdAndActiveTrue(Long sourceSiteId);
}
