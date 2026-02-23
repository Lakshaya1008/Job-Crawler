package com.jobmarket.engine;

import com.jobmarket.engine.crawler.target.CrawlTarget;
import com.jobmarket.engine.crawler.target.CrawlTargetRepository;
import com.jobmarket.engine.evidence.source.SourceSite;
import com.jobmarket.engine.evidence.source.SourceSiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds minimum required data on first startup.
 *
 * CommandLineRunner runs ONCE after Spring Boot fully starts.
 * Idempotent — checks existence before inserting. Safe to restart.
 *
 * V1 seeds TWO sites:
 *   1. Freshersworld — static HTML, Indian fresher jobs, JSoup works directly
 *   2. TimesJobs     — static HTML, Indian job market, different company set
 *
 * Two sites means:
 *   - Multi-source confirmation works (same job on both → sourceCount=2)
 *   - Fingerprint dedup is actually tested
 *   - LifecycleService conservative threshold across sources is exercised
 *   - All V1 features exercise real data paths
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final SourceSiteRepository sourceSiteRepository;
    private final CrawlTargetRepository crawlTargetRepository;

    @Override
    public void run(String... args) {
        seedFreshersworld();
        seedTimesJobs();
        log.info("DataSeeder complete — system ready to crawl");
    }

    // ─── Freshersworld ────────────────────────────────────────────────────────

    private void seedFreshersworld() {
        if (sourceSiteRepository.findByName("freshersworld").isPresent()) {
            log.debug("Freshersworld already seeded — skipping");
            return;
        }

        log.info("Seeding Freshersworld source site...");

        SourceSite freshersworld = SourceSite.builder()
                .name("freshersworld")
                .inactiveThresholdDays(7)
                .repostThresholdDays(30)
                .reliabilityWeight(0.70)
                .crawlDelaySeconds(3)
                .maxRetries(2)
                .crawlEnabled(true)
                .build();

        freshersworld = sourceSiteRepository.save(freshersworld);
        log.info("Freshersworld source site created with id={}", freshersworld.getId());

        CrawlTarget target = CrawlTarget.builder()
                .sourceSite(freshersworld)
                .url("https://www.freshersworld.com/jobs/jobsearch/java-developer-jobs-for-freshers")
                .active(true)
                .build();

        crawlTargetRepository.save(target);
        log.info("Freshersworld crawl target seeded: {}", target.getUrl());
    }

    // ─── TimesJobs ────────────────────────────────────────────────────────────

    private void seedTimesJobs() {
        if (sourceSiteRepository.findByName("timesjobs").isPresent()) {
            log.debug("TimesJobs already seeded — skipping");
            return;
        }

        log.info("Seeding TimesJobs source site...");

        SourceSite timesjobs = SourceSite.builder()
                .name("timesjobs")
                .inactiveThresholdDays(7)
                .repostThresholdDays(30)
                .reliabilityWeight(0.72)
                .crawlDelaySeconds(4)
                .maxRetries(2)
                .crawlEnabled(true)
                .build();

        timesjobs = sourceSiteRepository.save(timesjobs);
        log.info("TimesJobs source site created with id={}", timesjobs.getId());

        CrawlTarget target = CrawlTarget.builder()
                .sourceSite(timesjobs)
                .url("https://www.timesjobs.com/candidate/job-search.html?searchType=personalizedSearch&from=submit&txtKeywords=java+backend+developer&txtLocation=")
                .active(true)
                .build();

        crawlTargetRepository.save(target);
        log.info("TimesJobs crawl target seeded: {}", target.getUrl());
    }
}