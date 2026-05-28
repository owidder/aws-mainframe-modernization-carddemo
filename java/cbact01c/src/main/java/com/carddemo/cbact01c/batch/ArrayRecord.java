package com.carddemo.cbact01c.batch;

import java.util.List;

/**
 * Maps to ARRY-FILE output record (FD ARRY-FILE / ARR-ARRAY-REC).
 *
 * <p>COBOL: CBACT01C.cbl:71-78 — FD ARRY-FILE layout.
 * Populated by 1400-POPUL-ARRAY-RECORD (CBACT01C.cbl:253-261).
 *
 * <p>The {@code OCCURS 5 TIMES} clause maps to {@link List} of 5 {@link ArrayBalanceEntry}.
 * Elements 1–3 carry hardcoded values; elements 4–5 are zero (INITIALIZE).
 */
public record ArrayRecord(
        Long acctId,                       // ARR-ACCT-ID PIC 9(11)
        List<ArrayBalanceEntry> balances   // ARR-ACCT-BAL OCCURS 5 TIMES
) {}
