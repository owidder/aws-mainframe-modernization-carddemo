package com.carddemo.account.repository;

import com.carddemo.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Replaces the EXEC CICS READ DATASET('ACCTDAT') RIDFLD(WS-CARD-RID-ACCT-ID-X)
 * call in COACCT01.cbl (paragraph 4000-PROCESS-REQUEST-REPLY).
 *
 * <ul>
 *   <li>{@link #findById(Long)} — random-key read; empty Optional = DFHRESP(NOTFND)</li>
 *   <li>{@link #existsById(Long)} — key existence check without fetching data</li>
 * </ul>
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
}
