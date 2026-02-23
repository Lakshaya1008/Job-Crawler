package com.jobmarket.engine.crawler;

import com.jobmarket.engine.crawler.attempt.CrawlAttempt;
import com.jobmarket.engine.crawler.attempt.CrawlAttemptRepository;
import com.jobmarket.engine.crawler.attempt.CrawlStatus;
import com.jobmarket.engine.crawler.target.CrawlTarget;
import com.jobmarket.engine.domain.job.Job;
import com.jobmarket.engine.evidence.source.SourceSite;
import com.jobmarket.engine.service.resolver.JobResolverService;
import com.jobmarket.engine.service.resolver.ObservationRecorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlWorker {

    private final CrawlAttemptRepository crawlAttemptRepository;
    private final JobResolverService jobResolverService;
    private final ObservationRecorderService observationRecorderService;

    // Realistic browser user agent — reduces chance of being blocked
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MS = 15_000;

    /**
     * Processes one CrawlTarget completely:
     *   1. Creates CrawlAttempt record (always recorded regardless of outcome)
     *   2. Fetches HTML with JSoup — retries up to max_retries with backoff
     *   3. Dispatches to site-specific parser based on site name
     *   4. For each parsed card → resolve job (dedup) → record observation
     *   5. Completes CrawlAttempt with final status + count
     *
     * KEY RULE:
     * If fetch fails → HTTP_FAIL → STOP. Job data not touched.
     * Missing observation after HTTP_FAIL ≠ job disappeared. (Memory Rule 6)
     */
    public void process(CrawlTarget target) {
        SourceSite site = target.getSourceSite();
        log.info("Starting crawl: site='{}', url='{}'", site.getName(), target.getUrl());

        // Record attempt BEFORE trying — we always know we tried
        CrawlAttempt attempt = CrawlAttempt.builder()
                .crawlTarget(target)
                .startedAt(LocalDateTime.now())
                .status(CrawlStatus.HTTP_FAIL)   // pessimistic default
                .jobsFoundCount(0)
                .build();
        attempt = crawlAttemptRepository.save(attempt);

        // Fetch HTML
        Document document = fetchWithRetry(target, site, attempt);
        if (document == null) return; // HTTP_FAIL already saved in fetchWithRetry

        // Parse — dispatches to correct parser based on site name
        List<ParsedJobData> parsedJobs = parseJobCards(document, target.getUrl(), site.getName());

        // 0 jobs is NOT a PARSE_FAIL — it could be an empty page or selector mismatch
        // We log it as a warning but still record SUCCESS so the operator can investigate
        // PARSE_FAIL is only when the parser itself crashes (exception thrown)
        if (parsedJobs.isEmpty()) {
            log.warn("[{}] Parsed 0 jobs — page may have changed structure. " +
                            "Open URL in browser, press Ctrl+U, verify selector finds job cards.",
                    site.getName());
        }

        log.info("[{}] Parsed {} job cards", site.getName(), parsedJobs.size());

        // Process each card through the full dedup + observation pipeline
        int successCount = 0;
        for (ParsedJobData jobData : parsedJobs) {
            try {
                // Step 1: Dedup — find existing or create new logical job
                Job job = jobResolverService.resolve(
                        jobData.getRawCompany(),
                        jobData.getRawTitle(),
                        jobData.getRawLocation()
                );

                // Step 2: Record evidence — always append new observation row
                observationRecorderService.record(
                        job,
                        site,
                        attempt,
                        jobData.getListingUrl(),
                        jobData.getRawTitle(),
                        jobData.getSalaryText()
                );

                successCount++;
                sleep(site.getCrawlDelaySeconds() * 200L); // polite inter-card delay

            } catch (Exception e) {
                // One bad card never stops the rest — partial failure is fine
                log.warn("[{}] Failed processing card '{}': {}",
                        site.getName(), jobData.getRawTitle(), e.getMessage());
            }
        }

        // Mark attempt complete with final count
        attempt.complete(CrawlStatus.SUCCESS, 200, null, successCount);
        crawlAttemptRepository.save(attempt);
        log.info("[{}] Crawl complete — recorded {}/{} jobs",
                site.getName(), successCount, parsedJobs.size());
    }

    // ─── Fetch with exponential backoff ──────────────────────────────────────

    /**
     * Backoff schedule (max_retries from source_site table):
     *   Attempt 1 fails → wait 2s
     *   Attempt 2 fails → wait 4s
     *   All exhausted   → record HTTP_FAIL, return null
     */
    private Document fetchWithRetry(CrawlTarget target, SourceSite site, CrawlAttempt attempt) {
        int maxRetries = site.getMaxRetries();

        for (int i = 0; i <= maxRetries; i++) {
            try {
                sleep(site.getCrawlDelaySeconds() * 1000L);
                Document doc = Jsoup.connect(target.getUrl())
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .timeout(TIMEOUT_MS)
                        .followRedirects(true)
                        .get();
                log.debug("[{}] Fetch success on attempt {}", site.getName(), i + 1);
                return doc;

            } catch (IOException e) {
                log.warn("[{}] Fetch failed (attempt {}/{}): {}",
                        site.getName(), i + 1, maxRetries + 1, e.getMessage());
                if (i < maxRetries) {
                    long backoffMs = (long) Math.pow(2, i + 1) * 1000;
                    log.debug("[{}] Backing off {}ms", site.getName(), backoffMs);
                    sleep(backoffMs);
                }
            }
        }

        log.error("[{}] All {} attempts failed", site.getName(), maxRetries + 1);
        attempt.complete(CrawlStatus.HTTP_FAIL, null,
                "All " + (maxRetries + 1) + " attempts failed", 0);
        crawlAttemptRepository.save(attempt);
        return null;
    }

    // ─── Parser dispatcher ────────────────────────────────────────────────────

    /**
     * Routes to the correct site-specific parser.
     *
     * Why per-site parsers instead of one generic one?
     * Each site has a completely different HTML structure.
     * One generic parser with fallback selectors = unreliable fragile mess.
     * One dedicated method per site = explicit, testable, debuggable.
     *
     * Adding a new site = add one case here + one parser method. Nothing else changes.
     */
    private List<ParsedJobData> parseJobCards(Document document, String baseUrl, String siteName) {
        return switch (siteName.toLowerCase()) {
            case "freshersworld" -> parseFreshersworld(document);
            case "timesjobs"     -> parseTimesJobs(document);
            default -> {
                log.warn("No parser defined for site: '{}' — add a case and parser method", siteName);
                yield List.of();
            }
        };
    }

    // ─── Freshersworld Parser ─────────────────────────────────────────────────

    /**
     * Parses job listings from Freshersworld search results page.
     *
     * HOW TO VERIFY SELECTORS:
     * 1. Open https://www.freshersworld.com/jobs/jobsearch/java-developer-jobs-for-freshers in Chrome
     * 2. Press Ctrl+U (View Page Source)
     * 3. Press Ctrl+F and search for a job title you see on screen
     * 4. If found in source → JSoup will see it ✅
     * 5. Right-click a job card → Inspect → find the wrapping div class
     * 6. Update JOB_CARD_SELECTOR below to match
     *
     * CURRENT SELECTORS — verify against live page before running:
     */
    private static final String FW_CARD     = ".job-container";
    private static final String FW_TITLE    = "h3.latest-jobs-title a";
    private static final String FW_COMPANY  = ".company-name";
    private static final String FW_LOCATION = ".job-location, .location, .jobs-location";
    private static final String FW_URL_ATTR = "job_display_url";   // data attribute on card

    private List<ParsedJobData> parseFreshersworld(Document document) {
        List<ParsedJobData> results = new ArrayList<>();

        Elements cards = document.select(FW_CARD);
        log.info("[Freshersworld] Found {} raw card elements with selector '{}'",
                cards.size(), FW_CARD);

        if (cards.isEmpty()) {
            log.warn("[Freshersworld] ZERO cards found. " +
                    "Ctrl+U the page source and verify '{}' exists.", FW_CARD);
            // Return empty — NOT an exception. Caller logs warning, records SUCCESS with 0.
            return results;
        }

        for (Element card : cards) {
            try {
                String title    = extractText(card, FW_TITLE);
                String company  = extractText(card, FW_COMPANY);
                String location = extractText(card, FW_LOCATION);
                String url      = card.attr(FW_URL_ATTR);

                // Fallback: if data attribute empty, try href from title link
                if (url.isBlank()) {
                    url = card.select(FW_TITLE).attr("abs:href");
                }

                if (location.isBlank()) location = "India";

                if (title.isBlank() || company.isBlank() || url.isBlank()) {
                    log.debug("[Freshersworld] Skipping incomplete card — title='{}' company='{}' url='{}'",
                            title, company, url);
                    continue;
                }

                results.add(ParsedJobData.builder()
                        .rawTitle(title)
                        .rawCompany(company)
                        .rawLocation(location)
                        .listingUrl(url)
                        .salaryText(null)
                        .build());

                log.debug("[Freshersworld] Parsed: [{}] @ [{}] in [{}]", title, company, location);

            } catch (Exception e) {
                log.debug("[Freshersworld] Skipping one card: {}", e.getMessage());
            }
        }

        log.info("[Freshersworld] Successfully extracted {} jobs", results.size());
        return results;
    }

    // ─── TimesJobs Parser ─────────────────────────────────────────────────────

    /**
     * Parses job listings from TimesJobs search results page.
     *
     * HOW TO VERIFY SELECTORS:
     * 1. Open the TimesJobs URL seeded in DataSeeder in Chrome
     * 2. Press Ctrl+U (View Page Source)
     * 3. Ctrl+F for a job title visible on screen
     * 4. If found → JSoup will work ✅
     * 5. Right-click a job card → Inspect → note the li/div class wrapping one job
     * 6. Update TJ_CARD selector below if different
     *
     * CURRENT SELECTORS — verify against live page before running:
     */
    private static final String TJ_CARD     = "li.clearfix.job-bx";
    private static final String TJ_TITLE_1  = "h2 a.jobTitle";          // primary selector
    private static final String TJ_TITLE_2  = "h2.job-tittle a";        // alternate selector
    private static final String TJ_COMPANY  = "h3.joblist-comp-name";
    private static final String TJ_LOCATION = "ul.top-jd-dtl li span";  // first span = location

    private List<ParsedJobData> parseTimesJobs(Document document) {
        List<ParsedJobData> results = new ArrayList<>();

        Elements cards = document.select(TJ_CARD);
        log.info("[TimesJobs] Found {} raw card elements with selector '{}'",
                cards.size(), TJ_CARD);

        if (cards.isEmpty()) {
            log.warn("[TimesJobs] ZERO cards found. " +
                    "Ctrl+U the page source and verify '{}' exists.", TJ_CARD);
            return results;
        }

        for (Element card : cards) {
            try {
                // Try primary title selector, fall back to alternate
                String title = extractText(card, TJ_TITLE_1);
                if (title.isBlank()) title = extractText(card, TJ_TITLE_2);

                String company  = extractText(card, TJ_COMPANY);
                String location = extractText(card, TJ_LOCATION);
                if (location.isBlank()) location = "India";

                // Get URL from whichever title selector worked
                String url = card.select(TJ_TITLE_1 + ", " + TJ_TITLE_2).attr("abs:href");

                if (title.isBlank() || company.isBlank() || url.isBlank()) {
                    log.debug("[TimesJobs] Skipping incomplete card — title='{}' company='{}' url='{}'",
                            title, company, url);
                    continue;
                }

                results.add(ParsedJobData.builder()
                        .rawTitle(title)
                        .rawCompany(company)
                        .rawLocation(location)
                        .listingUrl(url)
                        .salaryText(null)
                        .build());

                log.debug("[TimesJobs] Parsed: [{}] @ [{}] in [{}]", title, company, location);

            } catch (Exception e) {
                log.debug("[TimesJobs] Skipping one card: {}", e.getMessage());
            }
        }

        log.info("[TimesJobs] Successfully extracted {} jobs", results.size());
        return results;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String extractText(Element parent, String cssSelector) {
        Element el = parent.selectFirst(cssSelector);
        return el != null ? el.text().trim() : "";
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}