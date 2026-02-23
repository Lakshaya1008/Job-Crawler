package com.jobmarket.engine.service.resolver;

import com.jobmarket.engine.domain.company.Company;
import com.jobmarket.engine.domain.company.CompanyAliasRepository;
import com.jobmarket.engine.domain.company.CompanyRepository;
import com.jobmarket.engine.domain.job.Job;
import com.jobmarket.engine.domain.job.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobResolverService {

    private final CompanyNormalizerService companyNormalizer;
    private final RoleNormalizerService roleNormalizer;
    private final LocationNormalizerService locationNormalizer;
    private final FingerprintService fingerprintService;
    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository companyAliasRepository;
    private final JobRepository jobRepository;

    /**
     * The heart of deduplication.
     *
     * Given raw scraped data from a job listing page, this method:
     *   1. Normalizes company, role, location
     *   2. Generates fingerprint
     *   3. Checks if this logical job already exists
     *   4. If YES → updates last_seen_at, returns existing job
     *   5. If NO  → creates new company (if needed) + new job, returns it
     *
     * @Transactional — everything in this method is one DB transaction.
     * If anything fails halfway through, ALL changes are rolled back.
     * We never end up with a company but no job, or a job with no company.
     *
     * Why this matters: without @Transactional, a crash between saving
     * Company and saving Job leaves orphan data in the DB.
     */
    @Transactional
    public Job resolve(String rawCompany, String rawTitle, String rawLocation) {

        log.debug("Resolving: company='{}', title='{}', location='{}'",
                rawCompany, rawTitle, rawLocation);

        // STEP 1: Normalize all three components
        String normalizedCompany  = companyNormalizer.normalize(rawCompany);
        String normalizedRole     = roleNormalizer.normalize(rawTitle);
        String normalizedLocation = locationNormalizer.normalize(rawLocation);

        log.debug("Normalized: company='{}', role='{}', location='{}'",
                normalizedCompany, normalizedRole, normalizedLocation);

        // STEP 2: Generate fingerprint from normalized components
        // This is the unique identity of this logical job opportunity
        String fingerprint = fingerprintService.generate(
                normalizedCompany, normalizedRole, normalizedLocation);

        // STEP 3: Does this job already exist in our system?
        // findByFingerprint → single DB query, indexed column, very fast
        return jobRepository.findByFingerprint(fingerprint)
                .map(existingJob -> {
                    // JOB EXISTS — we've seen this logical opportunity before
                    // Update last_seen_at to record we just saw it again
                    // This is how lifecycle tracking works — no active flag needed
                    log.debug("Existing job found for fingerprint: {}", fingerprint);
                    existingJob.recordObservation(LocalDateTime.now());
                    return jobRepository.save(existingJob);
                })
                .orElseGet(() -> {
                    // NEW JOB — first time we've seen this logical opportunity
                    log.info("New job discovered: company='{}', role='{}', location='{}'",
                            normalizedCompany, normalizedRole, normalizedLocation);

                    // Resolve or create the company first
                    Company company = resolveCompany(normalizedCompany, rawCompany);

                    // Create the new job
                    Job newJob = Job.builder()
                            .company(company)
                            .normalizedRole(normalizedRole)
                            .normalizedLocation(normalizedLocation)
                            .fingerprint(fingerprint)
                            .build();

                    return jobRepository.save(newJob);
                });
    }

    /**
     * Finds an existing company by normalizedName, or creates a new one.
     *
     * Why separate method?
     * Company resolution has its own logic — we want to reuse
     * existing companies across many jobs. "TCS" jobs don't create
     * multiple company rows — they all point to the same company.
     */
    private Company resolveCompany(String normalizedName, String rawName) {
        return companyRepository.findByNormalizedName(normalizedName)
                .orElseGet(() -> {
                    log.info("New company discovered: '{}'", normalizedName);

                    // Use raw name as display_name — it's more human-readable
                    // "Tata Consultancy Services" is better display than "tata consultancy"
                    Company newCompany = Company.builder()
                            .normalizedName(normalizedName)
                            .displayName(rawName.trim())
                            .build();

                    return companyRepository.save(newCompany);
                });
    }
}
