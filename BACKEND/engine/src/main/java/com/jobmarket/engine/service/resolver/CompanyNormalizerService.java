package com.jobmarket.engine.service.resolver;

import com.jobmarket.engine.domain.company.CompanyAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyNormalizerService {

    private final CompanyAliasRepository companyAliasRepository;

    // Words that appear at the end of company names but carry no identity meaning
    // "Infosys Ltd" and "Infosys Limited" are the same company
    // We strip these AFTER lowercasing
    private static final Set<String> SUFFIX_WORDS = Set.of(
            "ltd", "limited", "pvt", "private", "inc", "llc",
            "corp", "corporation", "co", "company", "india",
            "technologies", "technology", "solutions", "services",
            "software", "systems", "global", "consulting"
    );

    /**
     * Converts any raw company name string into its canonical normalized form.
     *
     * Pipeline (must run in this exact order — memory rule 5.1):
     *   Step 1: lowercase
     *   Step 2: remove punctuation
     *   Step 3: remove suffix words
     *   Step 4: collapse spaces
     *   Step 5: alias lookup (DB)
     *
     * Examples:
     *   "HCL Technologies Pvt Ltd" → "hcl"       (then alias → "hcl technologies")
     *   "Tata Consultancy Services" → "tata consultancy"
     *   "  Google   Inc.  " → "google"
     */
    public String normalize(String rawCompanyName) {
        if (rawCompanyName == null || rawCompanyName.isBlank()) {
            log.warn("Received blank company name — returning unknown");
            return "unknown";
        }

        // STEP 1: Lowercase everything
        // "HCL Technologies" → "hcl technologies"
        String result = rawCompanyName.toLowerCase().trim();

        // STEP 2: Remove punctuation
        // "infosys, ltd." → "infosys ltd"
        // We keep spaces — they're needed for word splitting in step 3
        result = result.replaceAll("[^a-z0-9\\s]", "");

        // STEP 3: Remove suffix words
        // Split into words, filter out suffix words, rejoin
        // "tata consultancy services" → ["tata", "consultancy", "services"]
        // → filter → ["tata", "consultancy"] → "tata consultancy"
        String[] words = result.split("\\s+");
        StringBuilder cleaned = new StringBuilder();
        for (String word : words) {
            if (!SUFFIX_WORDS.contains(word)) {
                if (!cleaned.isEmpty()) cleaned.append(" ");
                cleaned.append(word);
            }
        }
        result = cleaned.toString();

        // STEP 4: Collapse any extra spaces
        // "tata  consultancy" → "tata consultancy"
        result = result.replaceAll("\\s+", " ").trim();

        // STEP 5: Alias lookup in DB
        // "tcs" → look up company_alias table → company.normalized_name = "tata consultancy"
        // If alias found → use the canonical company name
        // If not found → use whatever we computed in steps 1-4
        String finalResult = result;
        return companyAliasRepository.findByAlias(result)
                .map(alias -> {
                    log.debug("Alias match: '{}' → '{}'", finalResult,
                            alias.getCompany().getNormalizedName());
                    return alias.getCompany().getNormalizedName();
                })
                .orElseGet(() -> {
                    log.debug("No alias found for '{}' — using normalized form", finalResult);
                    return finalResult;
                });
    }
}
