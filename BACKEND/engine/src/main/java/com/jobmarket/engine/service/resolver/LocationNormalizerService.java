package com.jobmarket.engine.service.resolver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LocationNormalizerService {

    /**
     * Converts raw location string to a hiring eligibility cluster.
     *
     * Key principle (from memory):
     * Location cluster = hiring eligibility boundary.
     * Different hiring pool → different cluster → different job fingerprint.
     * Prefer separation over merge — false split is safer than false merge.
     *
     * Examples:
     *   "Bangalore"          → "BANGALORE"
     *   "Bengaluru"          → "BANGALORE"    (alias handling)
     *   "Remote - India"     → "REMOTE_INDIA"
     *   "Bangalore / Remote" → "BANGALORE_OR_REMOTE"
     *   "Work from home"     → "REMOTE_INDIA" (India context assumed)
     */
    public String normalize(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            log.warn("Blank location received — returning UNKNOWN");
            return "UNKNOWN";
        }

        String loc = rawLocation.toLowerCase().trim();

        // --- DETECT REMOTE FIRST ---
        // Remote detection must happen before city detection
        // because "Remote - Bangalore" has both signals

        boolean isRemote = loc.contains("remote") || loc.contains("work from home")
                || loc.contains("wfh") || loc.contains("anywhere");

        boolean hasCity = containsCity(loc);

        // Composite: both remote AND a city mentioned
        // "Bangalore / Remote", "Hybrid - Mumbai"
        if (isRemote && hasCity) {
            String city = detectCity(loc);
            log.debug("Location '{}' → '{}_OR_REMOTE'", rawLocation, city);
            return city + "_OR_REMOTE";
        }

        // Pure remote — which geography?
        if (isRemote) {
            if (loc.contains("us") || loc.contains("usa") || loc.contains("united states")) {
                return "REMOTE_US";
            }
            if (loc.contains("global") || loc.contains("worldwide") || loc.contains("anywhere")) {
                return "REMOTE_GLOBAL";
            }
            // Default remote context: India (since we're targeting Indian job market in V1)
            return "REMOTE_INDIA";
        }

        // Pure city — detect which one
        if (hasCity) {
            return detectCity(loc);
        }

        // Nothing matched — return raw uppercased
        // Better to store "AHMEDABAD" than lose the info
        String fallback = rawLocation.toUpperCase().replaceAll("\\s+", "_").trim();
        log.debug("No cluster match for '{}' — using fallback '{}'", rawLocation, fallback);
        return fallback;
    }

    /**
     * Returns true if location string contains any recognized city name
     */
    private boolean containsCity(String loc) {
        return loc.contains("bangalore") || loc.contains("bengaluru")
                || loc.contains("mumbai") || loc.contains("bombay")
                || loc.contains("delhi") || loc.contains("ncr") || loc.contains("gurugram") || loc.contains("gurgaon") || loc.contains("noida")
                || loc.contains("hyderabad") || loc.contains("hyd")
                || loc.contains("chennai") || loc.contains("madras")
                || loc.contains("pune")
                || loc.contains("kolkata") || loc.contains("calcutta")
                || loc.contains("ahmedabad")
                || loc.contains("indore");
    }

    /**
     * Detects the primary city cluster from a location string.
     * Handles common aliases (bengaluru=bangalore, bombay=mumbai, etc.)
     */
    private String detectCity(String loc) {
        if (loc.contains("bangalore") || loc.contains("bengaluru")) return "BANGALORE";
        if (loc.contains("mumbai") || loc.contains("bombay"))        return "MUMBAI";
        if (loc.contains("delhi") || loc.contains("ncr")
                || loc.contains("gurugram") || loc.contains("gurgaon")
                || loc.contains("noida"))                            return "DELHI_NCR";
        if (loc.contains("hyderabad") || loc.contains("hyd"))        return "HYDERABAD";
        if (loc.contains("chennai") || loc.contains("madras"))       return "CHENNAI";
        if (loc.contains("pune"))                                    return "PUNE";
        if (loc.contains("kolkata") || loc.contains("calcutta"))     return "KOLKATA";
        if (loc.contains("ahmedabad"))                               return "AHMEDABAD";
        if (loc.contains("indore"))                                  return "INDORE";

        return "OTHER_INDIA";
    }
}
