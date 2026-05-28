package com.carddemo.cbact01c.batch;

import java.math.BigDecimal;

/**
 * Long variable-length record written to VBRC-FILE (WS-RECD-LEN = 39).
 *
 * <p>Content: ACCT-ID (11) + CURR-BAL + CREDIT-LIMIT + REISSUE-YYYY (4) = 39 bytes.
 * The REISSUE-YYYY is the year portion extracted from ACCT-REISSUE-DATE via
 * WS-ACCT-REISSUE-YYYY (first 4 chars of WS-REISSUE-DATE redefine).
 *
 * <p>COBOL: CBACT01C.cbl:126-129 — VBRC-REC2 layout.
 * Populated/written by 1500-POPUL-VBRC-RECORD + 1575-WRITE-VB2-RECORD
 * (CBACT01C.cbl:276-315).
 */
public record VbrRecord2(
        Long acctId,            // VB2-ACCT-ID PIC 9(11)
        BigDecimal currBal,     // VB2-ACCT-CURR-BAL PIC S9(10)V99
        BigDecimal creditLimit, // VB2-ACCT-CREDIT-LIMIT PIC S9(10)V99
        String reissueYear      // VB2-ACCT-REISSUE-YYYY PIC X(04) — year portion of ACCT-REISSUE-DATE
) {}
