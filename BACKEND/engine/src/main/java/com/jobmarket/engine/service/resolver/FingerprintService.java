package com.jobmarket.engine.service.resolver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
public class FingerprintService {

    /**
     * Generates a unique, stable fingerprint for a logical job.
     *
     * fingerprint = SHA-256( normalized_company | normalized_role | normalized_location )
     *
     * WHY SHA-256 instead of just concatenating strings?
     * If we store "tata consultancy::BACKEND::BANGALORE" directly:
     *   - Very long column values
     *   - Sensitive to separator choice
     *   - If normalization changes, old fingerprints are messy to compare
     *
     * SHA-256 gives us:
     *   - Fixed length (64 chars always) — efficient DB indexing
     *   - Same inputs always produce same hash — deterministic
     *   - Tiny difference in input = completely different hash — safe
     *
     * CRITICAL: What is NOT in the fingerprint (memory rule):
     *   ❌ Skills        — claim, not identity
     *   ❌ Salary        — claim, not identity
     *   ❌ Description   — varies per site
     *   ❌ Posted date   — timing, not identity
     *   ❌ Source URL    — platform detail, not job identity
     */
    public String generate(String normalizedCompany,
                           String normalizedRole,
                           String normalizedLocation) {

        // Build the input string with a separator that won't appear in values
        // Using "::" as separator — company names won't have "::"
        String input = normalizedCompany + "::" + normalizedRole + "::" + normalizedLocation;

        log.debug("Generating fingerprint for: '{}'", input);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert byte array to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            String fingerprint = hexString.toString();
            log.debug("Fingerprint generated: {}", fingerprint);
            return fingerprint;

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist in all JVMs — this should never happen
            // But we must handle checked exception
            throw new RuntimeException("SHA-256 not available — JVM issue", e);
        }
    }
}
