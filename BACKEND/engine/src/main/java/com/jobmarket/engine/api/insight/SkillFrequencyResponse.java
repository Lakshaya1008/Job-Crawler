package com.jobmarket.engine.api.insight;

import lombok.Builder;
import lombok.Getter;

/**
 * How frequently a skill appears across validated active jobs.
 *
 * Memory rule: computed only from validated jobs (ACTIVE lifecycle state).
 * Never from raw crawled text. Never from ALL jobs including inactive ones.
 */
@Getter
@Builder
public class SkillFrequencyResponse {

    private String skillName;
    private long jobCount;          // how many active jobs mention this skill
    private double percentageShare; // jobCount / total active jobs * 100
}
