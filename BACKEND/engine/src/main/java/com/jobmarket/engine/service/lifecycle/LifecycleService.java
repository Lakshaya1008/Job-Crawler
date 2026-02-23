package com.jobmarket.engine.service.lifecycle;

import com.jobmarket.engine.domain.job.Job;
import com.jobmarket.engine.evidence.observation.JobObservation;
import com.jobmarket.engine.evidence.observation.JobObservationRepository;
import com.jobmarket.engine.evidence.source.JobSource;
import com.jobmarket.engine.evidence.source.JobSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifecycleService {

    private final JobSourceRepository jobSourceRepository;
    private final JobObservationRepository jobObservationRepository;

    public enum LifecycleState {
        ACTIVE,     // seen within inactive_threshold_days
        INACTIVE,   // not seen beyond inactive_threshold_days
        NEW_CYCLE,  // reappeared after repost_threshold_days
        UNKNOWN     // insufficient data
    }

    /**
     * Derives current lifecycle state for a job.
     *
     * Memory Rule 3: NEVER store active=true/false.
     * State is computed ON DEMAND from observation history.
     * Never written to DB.
     *
     * Thresholds are PER SITE from source_site table — not global constants.
     */
    public LifecycleState computeState(Job job) {
        List<JobSource> sources = jobSourceRepository.findByJobId(job.getId());

        if (sources.isEmpty()) return LifecycleState.UNKNOWN;

        LocalDateTime now = LocalDateTime.now();

        // Most recent observation across ALL sources
        // If LinkedIn saw it yesterday, job is ACTIVE — even if Naukri hasn't shown it
        Optional<LocalDateTime> mostRecent = sources.stream()
                .flatMap(source -> {
                    List<JobObservation> obs =
                            jobObservationRepository
                                    .findByJobSourceIdOrderByObservedAtDesc(source.getId());
                    return obs.stream().findFirst().stream().map(JobObservation::getObservedAt);
                })
                .max(Comparator.naturalOrder());

        if (mostRecent.isEmpty()) return LifecycleState.UNKNOWN;

        long daysSinceLastSeen = ChronoUnit.DAYS.between(mostRecent.get(), now);

        // Use most conservative threshold across all sites
        int inactiveThreshold = sources.stream()
                .map(s -> s.getSourceSite().getInactiveThresholdDays())
                .min(Integer::compareTo)
                .orElse(7);

        int repostThreshold = sources.stream()
                .map(s -> s.getSourceSite().getRepostThresholdDays())
                .min(Integer::compareTo)
                .orElse(30);

        if (daysSinceLastSeen <= inactiveThreshold)  return LifecycleState.ACTIVE;
        if (daysSinceLastSeen > repostThreshold)     return LifecycleState.NEW_CYCLE;
        return LifecycleState.INACTIVE;
    }

    public long daysSinceLastSeen(Job job) {
        return ChronoUnit.DAYS.between(job.getLastSeenAt(), LocalDateTime.now());
    }

    public long totalObservationSpanDays(Job job) {
        return ChronoUnit.DAYS.between(job.getFirstSeenAt(), job.getLastSeenAt());
    }

    public int confirmedSourceCount(Job job) {
        return jobSourceRepository.findByJobId(job.getId()).size();
    }
}
