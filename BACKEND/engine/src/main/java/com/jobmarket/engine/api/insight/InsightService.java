package com.jobmarket.engine.api.insight;

import com.jobmarket.engine.domain.job.Job;
import com.jobmarket.engine.domain.job.JobRepository;
import com.jobmarket.engine.domain.job.JobSkill;
import com.jobmarket.engine.domain.skill.Skill;
import com.jobmarket.engine.domain.skill.SkillRepository;
import com.jobmarket.engine.evidence.observation.JobObservation;
import com.jobmarket.engine.evidence.observation.JobObservationRepository;
import com.jobmarket.engine.evidence.source.JobSource;
import com.jobmarket.engine.evidence.source.JobSourceRepository;
import com.jobmarket.engine.service.lifecycle.LifecycleService;
import com.jobmarket.engine.service.lifecycle.LifecycleService.LifecycleState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final JobRepository jobRepository;
    private final JobSourceRepository jobSourceRepository;
    private final JobObservationRepository jobObservationRepository;
    private final SkillRepository skillRepository;
    private final LifecycleService lifecycleService;

    // ─────────────────────────────────────────────────────────
    // ENDPOINT 1: Jobs seen in the last 24 hours
    // ─────────────────────────────────────────────────────────

    /**
     * Returns jobs that had at least one observation in the last 24 hours.
     *
     * "New" here means "newly observed" — could be a job we've seen before
     * that just got confirmed again. That's honest — we don't claim it's
     * a brand new posting, just that we saw it recently.
     */
    public List<JobSummaryResponse> getJobsSeenInLast24Hours() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        // All jobs whose last_seen_at is within 24 hours
        List<Job> recentJobs = jobRepository.findByLastSeenAtAfter(since);

        log.debug("Jobs seen in last 24h: {}", recentJobs.size());

        return recentJobs.stream()
                .map(this::toJobSummary)
                .sorted(Comparator.comparing(JobSummaryResponse::getLastSeenAt).reversed())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    // ENDPOINT 2: Currently active jobs
    // ─────────────────────────────────────────────────────────

    /**
     * Returns jobs whose derived lifecycle state is ACTIVE.
     *
     * Memory rule: we compute state here — never read a stored flag.
     * Each job's state is derived fresh from its observation history.
     *
     * Performance note: this calls lifecycleService.computeState() per job.
     * For V1 with small data this is fine.
     * For V2 with thousands of jobs, we'd add caching or batch computation.
     * That's a known tradeoff we can explain in interviews.
     */
    public List<JobSummaryResponse> getActiveJobs() {
        // Candidate pool: jobs seen recently (last 30 days)
        // We don't check ALL jobs ever — only recent candidates
        LocalDateTime candidateSince = LocalDateTime.now().minusDays(30);
        List<Job> candidates = jobRepository.findByLastSeenAtAfter(candidateSince);

        return candidates.stream()
                .filter(job -> lifecycleService.computeState(job) == LifecycleState.ACTIVE)
                .map(this::toJobSummary)
                .sorted(Comparator.comparing(JobSummaryResponse::getLastSeenAt).reversed())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    // ENDPOINT 3: Skill frequency across active jobs
    // ─────────────────────────────────────────────────────────

    /**
     * Returns skill demand frequency computed from ACTIVE jobs only.
     *
     * Memory rule: computed only from validated jobs — never raw text.
     * Only ACTIVE lifecycle state jobs count toward skill frequency.
     * An inactive job's skills don't represent current market demand.
     */
    public List<SkillFrequencyResponse> getSkillFrequency() {
        // Only active jobs contribute to skill demand signal
        List<JobSummaryResponse> activeJobs = getActiveJobs();
        long totalActiveJobs = activeJobs.size();

        if (totalActiveJobs == 0) {
            log.warn("No active jobs found — skill frequency will be empty");
            return List.of();
        }

        // Count how many active jobs mention each skill
        // We go through the Job entities (not summaries) to access job_skill relations
        LocalDateTime candidateSince = LocalDateTime.now().minusDays(30);
        List<Job> activeJobEntities = jobRepository.findByLastSeenAtAfter(candidateSince)
                .stream()
                .filter(job -> lifecycleService.computeState(job) == LifecycleState.ACTIVE)
                .collect(Collectors.toList());

        // Count skill appearances
        Map<String, Long> skillCounts = activeJobEntities.stream()
                .flatMap(job -> job.getJobSkills().stream())
                .map(JobSkill::getSkill)
                .collect(Collectors.groupingBy(Skill::getName, Collectors.counting()));

        // Convert to response objects, sorted by frequency descending
        return skillCounts.entrySet().stream()
                .map(entry -> SkillFrequencyResponse.builder()
                        .skillName(entry.getKey())
                        .jobCount(entry.getValue())
                        .percentageShare(
                                Math.round((entry.getValue() * 100.0 / totalActiveJobs) * 10.0) / 10.0
                        )
                        .build())
                .sorted(Comparator.comparingLong(SkillFrequencyResponse::getJobCount).reversed())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    // ENDPOINT 4: Evidence timeline for a specific job
    // ─────────────────────────────────────────────────────────

    /**
     * Returns the full observation history for a job.
     *
     * This is the most powerful V1 endpoint — it shows HOW we know a job exists.
     * "We saw this job 14 times across 3 sites over 5 days."
     * That's evidence, not just data.
     *
     * The crawl_status per observation lets you see:
     *   - When did crawl failures happen?
     *   - Were there gaps in observation due to crawler issues?
     *   - Which site confirmed it most recently?
     */
    public List<TimelineEventResponse> getJobTimeline(Long jobId) {
        // Get all observations across all sources for this job
        List<JobObservation> observations = jobObservationRepository.findAllByJobId(jobId);

        if (observations.isEmpty()) {
            log.warn("No observations found for jobId: {}", jobId);
            return List.of();
        }

        return observations.stream()
                .map(obs -> TimelineEventResponse.builder()
                        .observedAt(obs.getObservedAt())
                        .sourceSite(obs.getJobSource().getSourceSite().getName())
                        .sourceUrl(obs.getJobSource().getSourceUrl())
                        .rawTitle(obs.getRawTitle())
                        .crawlStatus(obs.getCrawlAttempt().getStatus().name())
                        .build())
                .sorted(Comparator.comparing(TimelineEventResponse::getObservedAt).reversed())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    // Private helper
    // ─────────────────────────────────────────────────────────

    /**
     * Converts a Job entity to a clean API response object.
     * This is where we compute derived fields (lifecycle, days since seen, source count).
     * None of these are stored — all computed on demand.
     */
    private JobSummaryResponse toJobSummary(Job job) {
        LifecycleState state = lifecycleService.computeState(job);
        long daysSince = lifecycleService.daysSinceLastSeen(job);
        int sourceCount = lifecycleService.confirmedSourceCount(job);

        List<String> skills = job.getJobSkills().stream()
                .map(js -> js.getSkill().getName())
                .collect(Collectors.toList());

        return JobSummaryResponse.builder()
                .jobId(job.getId())
                .company(job.getCompany().getDisplayName())
                .role(job.getNormalizedRole())
                .location(job.getNormalizedLocation())
                .lifecycleState(state.name())
                .daysSinceLastSeen(daysSince)
                .sourceCount(sourceCount)
                .firstSeenAt(job.getFirstSeenAt())
                .lastSeenAt(job.getLastSeenAt())
                .skills(skills)
                .build();
    }
}
