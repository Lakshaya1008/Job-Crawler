package com.jobmarket.engine.domain.company;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company_alias")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The raw alias exactly as it appears on websites
    // "TCS", "Tata Consultancy Services", "Tata CS" are all valid aliases
    // UNIQUE → same alias can't point to two different companies
    @Column(name = "alias", nullable = false, unique = true)
    private String alias;

    // Which company this alias resolves to
    // ManyToOne → many aliases can point to one company
    // LAZY → don't fetch company unless needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
