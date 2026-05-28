package com.carddemo.cbact01c.batch;

/**
 * Short variable-length record written to VBRC-FILE (WS-RECD-LEN = 12).
 *
 * <p>Content: ACCT-ID (11 chars) + ACCT-ACTIVE-STATUS (1 char) = 12 bytes.
 *
 * <p>COBOL: CBACT01C.cbl:123-125 — VBRC-REC1 layout.
 * Written by 1550-WRITE-VB1-RECORD (CBACT01C.cbl:287-300).
 */
public record VbrRecord1(
        Long acctId,        // VB1-ACCT-ID PIC 9(11)
        String activeStatus // VB1-ACCT-ACTIVE-STATUS PIC X(01)
) {}
