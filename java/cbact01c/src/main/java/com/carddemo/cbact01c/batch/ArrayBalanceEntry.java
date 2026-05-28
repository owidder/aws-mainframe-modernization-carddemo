package com.carddemo.cbact01c.batch;

import java.math.BigDecimal;

/**
 * One element of {@code ARR-ACCT-BAL OCCURS 5 TIMES} in the ARRY-FILE record.
 *
 * <p>COBOL: CBACT01C.cbl:74-78 — ARR-ACCT-BAL group inside FD ARRY-FILE.
 */
public record ArrayBalanceEntry(
        BigDecimal currBal,     // ARR-ACCT-CURR-BAL PIC S9(10)V99
        BigDecimal currCycDebit // ARR-ACCT-CURR-CYC-DEBIT PIC S9(10)V99 COMP-3
) {}
