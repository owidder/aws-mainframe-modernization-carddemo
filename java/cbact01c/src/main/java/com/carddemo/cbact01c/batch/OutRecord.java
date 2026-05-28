package com.carddemo.cbact01c.batch;

import java.math.BigDecimal;

/**
 * Maps to OUT-FILE output record (FD OUT-FILE / OUT-ACCT-REC).
 *
 * <p>COBOL: CBACT01C.cbl:56-69 — FD OUT-FILE layout.
 * Populated by 1300-POPUL-ACCT-RECORD (CBACT01C.cbl:215-240).
 *
 * <p>Key transformation: {@link #reissueDate} holds the YYYYMMDD-formatted date
 * produced by the COBDATFT assembler call (see {@link DateConversionService}).
 */
public record OutRecord(
        Long acctId,               // OUT-ACCT-ID PIC 9(11)
        String activeStatus,       // OUT-ACCT-ACTIVE-STATUS PIC X(01)
        BigDecimal currBal,        // OUT-ACCT-CURR-BAL PIC S9(10)V99
        BigDecimal creditLimit,    // OUT-ACCT-CREDIT-LIMIT PIC S9(10)V99
        BigDecimal cashCreditLimit,// OUT-ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99
        String openDate,           // OUT-ACCT-OPEN-DATE PIC X(10)
        String expirationDate,     // OUT-ACCT-EXPIRAION-DATE PIC X(10)
        String reissueDate,        // OUT-ACCT-REISSUE-DATE PIC X(10) — YYYYMMDD via COBDATFT
        BigDecimal currCycCredit,  // OUT-ACCT-CURR-CYC-CREDIT PIC S9(10)V99
        BigDecimal currCycDebit,   // OUT-ACCT-CURR-CYC-DEBIT PIC S9(10)V99 COMP-3 (2525.00 if zero)
        String groupId             // OUT-ACCT-GROUP-ID PIC X(10)
) {}
