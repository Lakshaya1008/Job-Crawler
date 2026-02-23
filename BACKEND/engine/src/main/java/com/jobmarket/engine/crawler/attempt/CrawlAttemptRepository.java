package com.jobmarket.engine.crawler.attempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlAttemptRepository extends JpaRepository<CrawlAttempt, Long> {

    // History of all attempts for a target — used for retry logic
    // Most recent first so we can check last N attempts quickly
    List<CrawlAttempt> findByCrawlTargetIdOrderByStartedAtDesc(Long crawlTargetId);
}
