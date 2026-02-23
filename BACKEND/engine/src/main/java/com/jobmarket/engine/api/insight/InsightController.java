package com.jobmarket.engine.api.insight;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * V1 Insight API — 4 endpoints.
 *
 * This controller is intentionally thin.
 * Zero business logic here. Zero query logic.
 * It receives the HTTP request, calls the service, returns the result.
 *
 * WHY @RequestMapping("/api/v1/insights")?
 * API versioning from day one. When V2 adds confidence scores,
 * we create /api/v2/insights without breaking existing callers.
 * You can run v1 and v2 side by side during migration.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    /**
     * GET /api/v1/insights/jobs/new
     *
     * Jobs observed in the last 24 hours.
     * Sorted by most recently seen first.
     *
     * Use case: "What jobs were confirmed active today?"
     */
    @GetMapping("/jobs/new")
    public ResponseEntity<List<JobSummaryResponse>> getNewJobs() {
        log.debug("GET /jobs/new called");
        List<JobSummaryResponse> jobs = insightService.getJobsSeenInLast24Hours();
        return ResponseEntity.ok(jobs);
    }

    /**
     * GET /api/v1/insights/jobs/active
     *
     * Jobs whose derived lifecycle state is ACTIVE.
     * State computed from observation history — never stored.
     *
     * Use case: "What jobs are likely still accepting applications?"
     */
    @GetMapping("/jobs/active")
    public ResponseEntity<List<JobSummaryResponse>> getActiveJobs() {
        log.debug("GET /jobs/active called");
        List<JobSummaryResponse> jobs = insightService.getActiveJobs();
        return ResponseEntity.ok(jobs);
    }

    /**
     * GET /api/v1/insights/skills/frequency
     *
     * Skill demand frequency from active jobs only.
     * Sorted by most demanded skill first.
     *
     * Use case: "What skills should I learn based on current market?"
     */
    @GetMapping("/skills/frequency")
    public ResponseEntity<List<SkillFrequencyResponse>> getSkillFrequency() {
        log.debug("GET /skills/frequency called");
        List<SkillFrequencyResponse> skills = insightService.getSkillFrequency();
        return ResponseEntity.ok(skills);
    }

    /**
     * GET /api/v1/insights/jobs/{id}/timeline
     *
     * Full observation history for a specific job.
     * Shows WHEN and WHERE we saw evidence of this job.
     *
     * Use case: "How do I know this job is real and still open?"
     * Answer: "We saw it 12 times on 3 sites over 4 days. Here's the history."
     *
     * Returns 404 if job has no observations yet.
     */
    @GetMapping("/jobs/{id}/timeline")
    public ResponseEntity<List<TimelineEventResponse>> getJobTimeline(
            @PathVariable Long id) {

        log.debug("GET /jobs/{}/timeline called", id);
        List<TimelineEventResponse> timeline = insightService.getJobTimeline(id);

        if (timeline.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(timeline);
    }

    /**
     * GET /api/v1/insights/health
     *
     * Simple health check — confirms engine is running.
     * Not a Spring Actuator endpoint — just a plain human-readable response.
     * Useful during development to confirm app started correctly.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Job Market Observation Engine — V1 running");
    }
}
