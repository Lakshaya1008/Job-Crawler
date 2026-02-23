package com.jobmarket.engine.domain.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyAliasRepository extends JpaRepository<CompanyAlias, Long> {

    // Core dedup query: crawler sees "TCS" → find which company that maps to
    // This is step 5 of company normalization: alias lookup
    Optional<CompanyAlias> findByAlias(String alias);
}
