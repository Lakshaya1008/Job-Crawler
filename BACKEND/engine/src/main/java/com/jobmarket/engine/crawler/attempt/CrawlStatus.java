package com.jobmarket.engine.crawler.attempt;

public enum CrawlStatus {

    // Page loaded successfully AND jobs were parsed
    SUCCESS,

    // Could not reach the site at all
    // Network error, timeout, 4xx/5xx response
    // IMPORTANT: do NOT conclude jobs disappeared — we simply couldn't check
    HTTP_FAIL,

    // Site responded fine but our HTML parser couldn't extract job data
    // This usually means the site changed its HTML structure
    // Our parser is broken, not the jobs gone
    PARSE_FAIL
}
