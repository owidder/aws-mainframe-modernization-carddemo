package com.carddemo.cbact01c.repository;

import com.carddemo.cbact01c.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the accounts table.
 *
 * <p>Replaces sequential READ of ACCTFILE-FILE (VSAM KSDS) in CBACT01C.
 * The batch reader uses the inherited {@code findAll(Pageable)} method.
 *
 * <p>COBOL: CBACT01C.cbl:165-198 — 1000-ACCTFILE-GET-NEXT
 */
public interface AccountRepository extends JpaRepository<Account, Long> {}
