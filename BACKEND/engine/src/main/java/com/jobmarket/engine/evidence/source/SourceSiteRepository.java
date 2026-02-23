package com.jobmarket.engine.evidence.source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceSiteRepository extends JpaRepository<SourceSite, Long> {

    Optional<SourceSite> findByName(String name);

    // Only crawl enabled sites — respects crawl_enabled flag
    List<SourceSite> findByCrawlEnabledTrue();
}
