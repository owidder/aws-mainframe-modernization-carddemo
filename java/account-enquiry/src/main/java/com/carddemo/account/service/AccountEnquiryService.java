package com.carddemo.account.service;

import com.carddemo.account.domain.Account;
import com.carddemo.account.repository.AccountRepository;
import com.carddemo.account.web.dto.AccountEnquiryRequest;
import com.carddemo.account.web.dto.AccountEnquiryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implements the core account enquiry logic from COACCT01.cbl paragraph
 * {@code 4000-PROCESS-REQUEST-REPLY}.
 *
 * <h2>COBOL to Java mapping</h2>
 * <pre>
 * COBOL (COACCT01.cbl:4000-PROCESS-REQUEST-REPLY)       Java
 * ─────────────────────────────────────────────       ──────────────────────────────
 * IF WS-FUNC = 'INQA' AND WS-KEY > ZEROES          → functionCode.equals("INQA")
 *   EXEC CICS READ DATASET('ACCTDAT')               → accountRepository.findById(id)
 *     RIDFLD(WS-CARD-RID-ACCT-ID-X)
 *     RESP(WS-RESP-CD)
 *   EVALUATE WS-RESP-CD
 *     WHEN DFHRESP(NORMAL)  → build WS-ACCT-RESPONSE  → AccountEnquiryResponse.success(…)
 *     WHEN DFHRESP(NOTFND)  → STRING 'INVALID…'       → AccountEnquiryResponse.error(…)
 *     WHEN OTHER            → PERFORM 9000-ERROR       → throw RuntimeException (→ 500)
 * ELSE                      → STRING 'INVALID…'       → AccountEnquiryResponse.error(…)
 * END-IF
 * </pre>
 */
@Service
@Transactional(readOnly = true)
public class AccountEnquiryService {

    private static final Logger log =
            LoggerFactory.getLogger(AccountEnquiryService.class);

    /** Only supported function code — mirrors WS-FUNC = 'INQA' */
    static final String FUNC_INQA = "INQA";

    private final AccountRepository accountRepository;

    public AccountEnquiryService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Processes one account enquiry request.
     *
     * <p>Mirrors the COBOL control flow in {@code 4000-PROCESS-REQUEST-REPLY}:
     * <ol>
     *   <li>Validate function code and key (replaces {@code IF WS-FUNC = 'INQA' AND WS-KEY > ZEROES}).</li>
     *   <li>Read account by ID (replaces {@code EXEC CICS READ DATASET('ACCTDAT')}).</li>
     *   <li>Map found account to response (replaces the series of {@code MOVE ACCT-* TO WS-ACCT-*}).</li>
     *   <li>Return error response if not found (replaces {@code DFHRESP(NOTFND)} branch).</li>
     * </ol>
     *
     * @param request validated enquiry request
     * @return success response with account fields, or error response
     */
    public AccountEnquiryResponse enquire(AccountEnquiryRequest request) {

        // Guard: WS-FUNC = 'INQA' AND WS-KEY > ZEROES (COACCT01:4000)
        if (!FUNC_INQA.equals(request.functionCode())) {
            String msg = String.format(
                    "INVALID REQUEST PARAMETERS ACCT ID : %d FUNCTION : %s",
                    request.accountId(), request.functionCode());
            log.warn(msg);
            return AccountEnquiryResponse.error(msg);
        }

        log.debug("Account enquiry: id={}", request.accountId());

        // EXEC CICS READ DATASET('ACCTDAT') RIDFLD(WS-CARD-RID-ACCT-ID-X)
        Optional<Account> found = accountRepository.findById(request.accountId());

        // EVALUATE WS-RESP-CD
        //   WHEN DFHRESP(NOTFND)
        if (found.isEmpty()) {
            String msg = String.format(
                    "INVALID REQUEST PARAMETERS ACCT ID : %d",
                    request.accountId());
            log.debug("Account not found: {}", request.accountId());
            return AccountEnquiryResponse.error(msg);
        }

        // WHEN DFHRESP(NORMAL) → MOVE ACCT-* TO WS-ACCT-*
        Account acct = found.get();
        log.debug("Account found: id={} status={} bal={}",
                acct.getAcctId(), acct.getActiveStatus(), acct.getCurrBal());

        return AccountEnquiryResponse.success(
                acct.getAcctId(),
                acct.getActiveStatus(),
                acct.getCurrBal(),
                acct.getCreditLimit(),
                acct.getCashCreditLimit(),
                acct.getOpenDate(),
                acct.getExpirationDate(),
                acct.getReissueDate(),
                acct.getCurrCycCredit(),
                acct.getCurrCycDebit(),
                acct.getGroupId()
        );
    }
}
