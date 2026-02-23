package com.jobmarket.engine.domain.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // Used during dedup: does this normalized company already exist?
    // Returns Optional because it might not exist yet — caller decides what to do
    Optional<Company> findByNormalizedName(String normalizedName);
}
