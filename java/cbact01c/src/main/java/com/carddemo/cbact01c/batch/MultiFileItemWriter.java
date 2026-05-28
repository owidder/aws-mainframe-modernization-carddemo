package com.carddemo.cbact01c.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

import java.util.Locale;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes an {@link AccountExportBundle} to three output files simultaneously,
 * mirroring the four WRITE statements in the CBACT01C main loop.
 *
 * <p>Per account the COBOL executes:
 * <pre>
 * L172  PERFORM 1350-WRITE-ACCT-RECORD → outFileWriter  (OUTFILE)
 * L174  PERFORM 1450-WRITE-ARRY-RECORD → arryFileWriter (ARRYFILE)
 * L177  PERFORM 1550-WRITE-VB1-RECORD  → vbrcFileWriter (VBRCFILE, VBR1 record, 12 bytes)
 * L178  PERFORM 1575-WRITE-VB2-RECORD  → vbrcFileWriter (VBRCFILE, VBR2 record, 39 bytes)
 * </pre>
 *
 * <p>Implements {@link ItemStream} so Spring Batch manages open/close of all
 * three {@link FlatFileItemWriter} instances through the standard step lifecycle
 * (replaces OPEN OUTPUT / CLOSE for OUT-FILE, ARRY-FILE, VBRC-FILE).
 */
public class MultiFileItemWriter implements ItemWriter<AccountExportBundle>, ItemStream {

    private final FlatFileItemWriter<OutRecord>   outWriter;
    private final FlatFileItemWriter<ArrayRecord> arryWriter;
    private final FlatFileItemWriter<String>      vbrcWriter;

    public MultiFileItemWriter(
            FlatFileItemWriter<OutRecord>   outWriter,
            FlatFileItemWriter<ArrayRecord> arryWriter,
            FlatFileItemWriter<String>      vbrcWriter) {
        this.outWriter  = outWriter;
        this.arryWriter = arryWriter;
        this.vbrcWriter = vbrcWriter;
    }

    /**
     * Distributes one chunk of bundles across all three output files.
     * VBR records are interleaved: VBR1 then VBR2 per account, matching
     * the COBOL write order in CBACT01C.cbl:177-178.
     */
    @Override
    public void write(Chunk<? extends AccountExportBundle> chunk) throws Exception {
        List<OutRecord>   outRecords  = new ArrayList<>();
        List<ArrayRecord> arryRecords = new ArrayList<>();
        List<String>      vbrcLines   = new ArrayList<>();

        for (AccountExportBundle bundle : chunk.getItems()) {
            outRecords.add(bundle.outRecord());
            arryRecords.add(bundle.arrayRecord());
            // COBOL: 1550-WRITE-VB1-RECORD then 1575-WRITE-VB2-RECORD per account
            vbrcLines.add(formatVbr1(bundle.vbrRecord1()));
            vbrcLines.add(formatVbr2(bundle.vbrRecord2()));
        }

        // COBOL: CBACT01C.cbl:242-251 — 1350-WRITE-ACCT-RECORD → WRITE OUT-ACCT-REC
        outWriter.write(new Chunk<>(outRecords));
        // COBOL: CBACT01C.cbl:263-274 — 1450-WRITE-ARRY-RECORD → WRITE ARR-ARRAY-REC
        arryWriter.write(new Chunk<>(arryRecords));
        // COBOL: CBACT01C.cbl:287-315 — 1550+1575-WRITE-VB*-RECORD → WRITE VBR-REC
        vbrcWriter.write(new Chunk<>(vbrcLines));
    }

    /**
     * Formats VBR1: type tag + ACCT-ID (11) + ACTIVE-STATUS (1) = 12 payload bytes.
     * COBOL: CBACT01C.cbl:287-300 — 1550-WRITE-VB1-RECORD (WS-RECD-LEN=12)
     */
    private String formatVbr1(VbrRecord1 r) {
        return String.format(Locale.US, "VBR1|%011d|%s", r.acctId(), r.activeStatus());
    }

    /**
     * Formats VBR2: type tag + ACCT-ID (11) + CURR-BAL + CREDIT-LIMIT + REISSUE-YYYY (4) = 39 payload bytes.
     * COBOL: CBACT01C.cbl:302-315 — 1575-WRITE-VB2-RECORD (WS-RECD-LEN=39)
     */
    private String formatVbr2(VbrRecord2 r) {
        return String.format(Locale.US, "VBR2|%011d|%14.2f|%14.2f|%4s",
                r.acctId(), r.currBal(), r.creditLimit(), r.reissueYear());
    }

    // ── ItemStream — delegates lifecycle to inner writers ────────────────

    /** Replaces OPEN OUTPUT OUT-FILE / ARRY-FILE / VBRC-FILE (CBACT01C.cbl:142-145) */
    @Override
    public void open(ExecutionContext executionContext) {
        outWriter.open(executionContext);
        arryWriter.open(executionContext);
        vbrcWriter.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) {
        outWriter.update(executionContext);
        arryWriter.update(executionContext);
        vbrcWriter.update(executionContext);
    }

    /** Replaces CLOSE ACCTFILE (and implicit close of output files) in CBACT01C.cbl:156-160 */
    @Override
    public void close() {
        outWriter.close();
        arryWriter.close();
        vbrcWriter.close();
    }
}
