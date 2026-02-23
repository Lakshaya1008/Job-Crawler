package com.jobmarket.engine.service.resolver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class RoleNormalizerService {

    /**
     * Role clusters — what logical role category does this title belong to?
     *
     * Key design decision (from memory):
     * - Determined from SEMANTICS, not skills
     * - "Java Backend Engineer" → BACKEND (not "java" role)
     * - Prefer false split over false merge
     *   → better to call two same jobs different than merge two different jobs
     */
    public enum RoleCluster {
        BACKEND,
        FRONTEND,
        FULLSTACK,
        DATA_ENGINEER,
        BACKEND_DATA,       // hybrid: backend + data responsibilities
        DEVOPS,
        MOBILE,
        GENERIC_SE,         // "Software Engineer" with no further signal
        QA,
        UNKNOWN             // fallback — never used for dedup merging
    }

    /**
     * Ordered keyword → cluster mapping.
     *
     * WHY LinkedHashMap (ordered)?
     * Because "software engineer - backend" must match BACKEND before GENERIC_SE.
     * Order of evaluation matters — more specific patterns first.
     *
     * WHY keywords, not ML?
     * V1 rule: no ML libraries. Deterministic logic only.
     * Dictionary-based is fully explainable and debuggable.
     */
    private static final LinkedHashMap<Set<String>, RoleCluster> KEYWORD_MAP;

    static {
        KEYWORD_MAP = new LinkedHashMap<>();

        // Hybrid clusters first — must be detected before single-role clusters
        // "backend data engineer" should not become BACKEND or DATA_ENGINEER alone
        KEYWORD_MAP.put(Set.of("backend", "data"), RoleCluster.BACKEND_DATA);

        // Single role clusters — ordered most specific to least specific
        KEYWORD_MAP.put(Set.of("devops", "sre", "platform", "infrastructure", "cloud"),
                RoleCluster.DEVOPS);
        KEYWORD_MAP.put(Set.of("android", "ios", "mobile", "flutter", "react native"),
                RoleCluster.MOBILE);
        KEYWORD_MAP.put(Set.of("data engineer", "pipeline", "spark", "kafka", "airflow"),
                RoleCluster.DATA_ENGINEER);
        KEYWORD_MAP.put(Set.of("fullstack", "full stack", "full-stack"),
                RoleCluster.FULLSTACK);
        KEYWORD_MAP.put(Set.of("frontend", "front end", "front-end", "ui developer",
                "react developer", "angular developer", "vue"),
                RoleCluster.FRONTEND);
        KEYWORD_MAP.put(Set.of("backend", "back end", "back-end", "api developer",
                "server side", "java developer", "spring", "node developer",
                "python developer", "golang"),
                RoleCluster.BACKEND);
        KEYWORD_MAP.put(Set.of("qa", "quality assurance", "test engineer",
                "automation engineer", "sdet"),
                RoleCluster.QA);

        // Generic SE — least specific, must be last
        // Only matches if nothing more specific matched first
        KEYWORD_MAP.put(Set.of("software engineer", "software developer",
                "sde", "swe", "programmer"),
                RoleCluster.GENERIC_SE);
    }

    /**
     * Takes raw job title → returns role cluster string
     *
     * Returns String (not enum) so it can be stored directly in DB column
     * and compared without enum serialization concerns
     */
    public String normalize(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            log.warn("Blank job title received — returning UNKNOWN");
            return RoleCluster.UNKNOWN.name();
        }

        // Lowercase for case-insensitive matching
        String title = rawTitle.toLowerCase().trim();

        // Check each keyword group in order
        for (Map.Entry<Set<String>, RoleCluster> entry : KEYWORD_MAP.entrySet()) {
            Set<String> keywords = entry.getKey();
            RoleCluster cluster = entry.getValue();

            // Count how many keywords from this group appear in the title
            long matches = keywords.stream()
                    .filter(title::contains)
                    .count();

            // For hybrid clusters (BACKEND_DATA) — need both keywords present
            // For single clusters — need at least one keyword
            boolean isHybrid = (cluster == RoleCluster.BACKEND_DATA);
            boolean matched = isHybrid ? (matches >= 2) : (matches >= 1);

            if (matched) {
                log.debug("Role '{}' → cluster '{}'", rawTitle, cluster);
                return cluster.name();
            }
        }

        // Nothing matched — return UNKNOWN
        // IMPORTANT: UNKNOWN jobs are stored but excluded from analytics
        // We never skip storing — we always record what we saw
        log.debug("No cluster match for '{}' — returning UNKNOWN", rawTitle);
        return RoleCluster.UNKNOWN.name();
    }
}
