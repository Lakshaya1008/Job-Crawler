package com.jobmarket.engine.crawler;

import com.jobmarket.engine.crawler.target.CrawlTarget;
import com.jobmarket.engine.crawler.target.CrawlTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlScheduler {

    private final CrawlTargetRepository crawlTargetRepository;
    private final CrawlWorker crawlWorker;

    /**
     * Runs automatically every 30 minutes.
     *
     * fixedDelay vs fixedRate:
     *   fixedRate  = run every 30min regardless of last run duration → risk of overlap
     *   fixedDelay = wait 30min AFTER completion → runs never overlap ← we use this
     *
     * @EnableScheduling must be added to EngineApplication.java
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 1_800_000)
    public void runCrawlCycle() {
        log.info("=== Crawl cycle starting ===");

        List<CrawlTarget> targets = crawlTargetRepository.findByActiveTrue();

        if (targets.isEmpty()) {
            log.warn("No active crawl targets — add rows to crawl_target table");
            return;
        }

        log.info("Processing {} targets", targets.size());
        int success = 0, failed = 0;

        for (CrawlTarget target : targets) {
            if (!target.getSourceSite().getCrawlEnabled()) {
                log.debug("Skipping disabled site: {}", target.getSourceSite().getName());
                continue;
            }
            try {
                crawlWorker.process(target);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("Target failed {}: {}", target.getUrl(), e.getMessage());
            }
        }

        log.info("=== Cycle complete: success={}, failed={} ===", success, failed);
    }
}
