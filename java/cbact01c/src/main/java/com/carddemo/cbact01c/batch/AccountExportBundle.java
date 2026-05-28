package com.carddemo.cbact01c.batch;

/**
 * Groups all output records produced for one account in a single processing step.
 *
 * <p>Passed from {@link AccountExportProcessor} to {@link MultiFileItemWriter}.
 * Mirrors the four write calls in the CBACT01C main loop:
 * <pre>
 * L172   PERFORM 1350-WRITE-ACCT-RECORD   → outRecord  → OUTFILE
 * L174   PERFORM 1450-WRITE-ARRY-RECORD   → arrayRecord → ARRYFILE
 * L177   PERFORM 1550-WRITE-VB1-RECORD    → vbrRecord1  → VBRCFILE
 * L178   PERFORM 1575-WRITE-VB2-RECORD    → vbrRecord2  → VBRCFILE
 * </pre>
 */
public record AccountExportBundle(
        OutRecord outRecord,
        ArrayRecord arrayRecord,
        VbrRecord1 vbrRecord1,
        VbrRecord2 vbrRecord2
) {}
