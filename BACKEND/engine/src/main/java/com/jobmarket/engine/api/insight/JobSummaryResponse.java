package com.jobmarket.engine.api.insight;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the API returns for a single job.
 *
 * Intentionally flat — no nested Hibernate entities.
 * Only fields the caller actually needs.
 * Lifecycle state is included — derived by LifecycleService, never stored.
 */
@Getter
@Builder
public class JobSummaryResponse {

    private Long jobId;
    private String company;          // display_name from company table
    private String role;             // normalized_role cluster
    private String location;         // normalized_location cluster
    private String lifecycleState;   // ACTIVE / INACTIVE / NEW_CYCLE — computed
    private Long daysSinceLastSeen;  // computed from last_seen_at
    private int sourceCount;         // how many sites confirmed this job
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private List<String> skills;     // skill names attached to this job
}
