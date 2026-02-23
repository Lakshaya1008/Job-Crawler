package com.jobmarket.engine.crawler;

import lombok.Builder;
import lombok.Getter;

/**
 * Holds raw extracted data from one job card on a listing page.
 *
 * DTO — Data Transfer Object. Just carries data, zero business logic.
 * Carries data FROM the HTML parser TO the resolver services.
 *
 * All fields are raw strings exactly as extracted from HTML.
 * Normalization happens in service layer — NOT here.
 * Crawler stays dumb. Services do the thinking.
 */
@Getter
@Builder
public class ParsedJobData {

    // Raw job title as it appears on the site
    // "Sr. Java Backend Developer - 2024 Batch"
    private final String rawTitle;

    // Raw company name as it appears on the site
    // "TCS", "Tata Consultancy Services Limited"
    private final String rawCompany;

    // Raw location string from the page
    // "Bangalore / Remote", "Work from home"
    private final String rawLocation;

    // Direct URL to this specific job listing
    // Becomes JobSource.sourceUrl
    private final String listingUrl;

    // Salary text if shown — nullable
    // "₹3-5 LPA", "Not Disclosed"
    private final String salaryText;
}
