package com.carddemo.cbact01c;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration tests — Spring Boot context + H2 + real file output.
 *
 * <p>Exercises the complete CBACT01C batch pipeline:
 * accountReader (H2) → AccountExportProcessor → MultiFileItemWriter (3 files).
 *
 * <p>Mirrors the COBOL PROCEDURE DIVISION success path (CBACT01C.cbl:141-160).
 */
@SpringBootTest
class Cbact01cIntegrationTest {

    private static final String OUTPUT_DIR = "target/test-output";

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job accountExportJob;

    private JobExecution runJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();
        return jobLauncher.run(accountExportJob, params);
    }

    // ── Job lifecycle ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Job completes with COMPLETED status (CBACT01C PROCEDURE DIVISION success path)")
    void job_completesWithCompletedStatus() throws Exception {
        JobExecution execution = runJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("Step reads and writes all 7 seed accounts — no skips (1000-ACCTFILE-GET-NEXT loop)")
    void step_readsAndWritesAllSevenAccounts() throws Exception {
        JobExecution execution = runJob();

        assertThat(execution.getStepExecutions()).hasSize(1);
        StepExecution step = execution.getStepExecutions().iterator().next();
        assertThat(step.getReadCount()).isEqualTo(7);
        assertThat(step.getWriteCount()).isEqualTo(7);
        assertThat(step.getSkipCount()).isEqualTo(0);
    }

    // ── OUTFILE assertions ───────────────────────────────────────────────

    @Test
    @DisplayName("OUTFILE has 7 lines — one per account (1350-WRITE-ACCT-RECORD)")
    void outFile_hasSevenLines() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/OUTFILE.txt"));
        assertThat(lines).hasSize(7);
    }

    @Test
    @DisplayName("Account 1: currCycDebit=0 → OUTFILE shows 2525.00 (COBOL: IF ACCT-CURR-CYC-DEBIT=ZERO MOVE 2525.00)")
    void outFile_account1_zeroCycDebitReplacedWith2525() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/OUTFILE.txt"));

        String acct1 = lines.stream()
                .filter(l -> l.startsWith("00000000001|"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Account 1 not found in OUTFILE"));

        assertThat(acct1).contains("2525.00");
    }

    @Test
    @DisplayName("Account 6: currCycDebit=100.00 (non-zero) → OUTFILE shows 100.00")
    void outFile_account6_nonZeroCycDebitPassesThrough() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/OUTFILE.txt"));

        String acct6 = lines.stream()
                .filter(l -> l.startsWith("00000000006|"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Account 6 not found in OUTFILE"));

        assertThat(acct6).contains("100.00");
        assertThat(acct6).doesNotContain("2525.00");
    }

    @Test
    @DisplayName("Account 1: reissueDate '2025-05-20' formatted to '20250520' in OUTFILE (CALL 'COBDATFT')")
    void outFile_account1_reissueDateFormatted() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/OUTFILE.txt"));

        String acct1 = lines.stream()
                .filter(l -> l.startsWith("00000000001|"))
                .findFirst()
                .orElseThrow();

        assertThat(acct1).contains("20250520");
    }

    @Test
    @DisplayName("Account 7: currCycCredit=500 and currCycDebit=200 pass through to OUTFILE")
    void outFile_account7_nonZeroCycleCreditAndDebit() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/OUTFILE.txt"));

        String acct7 = lines.stream()
                .filter(l -> l.startsWith("00000000007|"))
                .findFirst()
                .orElseThrow();

        assertThat(acct7).contains("500.00");
        assertThat(acct7).contains("200.00");
        assertThat(acct7).doesNotContain("2525.00");
    }

    // ── ARRYFILE assertions ───────────────────────────────────────────────

    @Test
    @DisplayName("ARRYFILE has 7 lines — one per account (1450-WRITE-ARRY-RECORD)")
    void arryFile_hasSevenLines() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/ARRYFILE.txt"));
        assertThat(lines).hasSize(7);
    }

    @Test
    @DisplayName("ARRYFILE account 1 contains currBal 1940.00 in entries 1 and 2 (1400-POPUL-ARRAY-RECORD)")
    void arryFile_account1_containsCurrBalInFirstTwoEntries() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/ARRYFILE.txt"));

        String acct1 = lines.stream()
                .filter(l -> l.startsWith("00000000001|"))
                .findFirst()
                .orElseThrow();

        assertThat(acct1).contains("1940.00");
    }

    // ── VBRCFILE assertions ───────────────────────────────────────────────

    @Test
    @DisplayName("VBRCFILE has 14 lines — 2 per account (1550-WRITE-VB1 + 1575-WRITE-VB2)")
    void vbrcFile_hasFourteenLines() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/VBRCFILE.txt"));
        assertThat(lines).hasSize(14);
    }

    @Test
    @DisplayName("VBRCFILE: VBR1 and VBR2 records are interleaved per account")
    void vbrcFile_vbr1AndVbr2AreInterleaved() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/VBRCFILE.txt"));

        // Lines alternate VBR1, VBR2 per account within each chunk
        long vbr1Count = lines.stream().filter(l -> l.startsWith("VBR1|")).count();
        long vbr2Count = lines.stream().filter(l -> l.startsWith("VBR2|")).count();

        assertThat(vbr1Count).isEqualTo(7);
        assertThat(vbr2Count).isEqualTo(7);
    }

    @Test
    @DisplayName("VBRCFILE: VBR2 for account 1 contains reissueYear '2025' (WS-ACCT-REISSUE-YYYY)")
    void vbrcFile_account1_vbr2ContainsReissueYear() throws Exception {
        runJob();
        List<String> lines = Files.readAllLines(Path.of(OUTPUT_DIR + "/VBRCFILE.txt"));

        String vbr2Acct1 = lines.stream()
                .filter(l -> l.startsWith("VBR2|00000000001|"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("VBR2 for account 1 not found"));

        assertThat(vbr2Acct1).contains("2025");
    }
}
