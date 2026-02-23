package com.jobmarket.engine.api.insight;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * One event in a job's observation timeline.
 *
 * This is what makes this system different from a job portal.
 * Instead of showing "this job exists", we show:
 * "We saw this job on LinkedIn at 2pm yesterday, on Naukri this morning..."
 *
 * The raw_title field lets users see what the site actually claimed —
 * useful for understanding how normalization worked.
 */
@Getter
@Builder
public class TimelineEventResponse {

    private LocalDateTime observedAt;
    private String sourceSite;     // which platform saw it
    private String sourceUrl;      // exact listing URL
    private String rawTitle;       // what the site actually said
    private String crawlStatus;    // SUCCESS / HTTP_FAIL / PARSE_FAIL
}
