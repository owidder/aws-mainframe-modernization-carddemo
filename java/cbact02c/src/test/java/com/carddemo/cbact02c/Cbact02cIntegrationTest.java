package com.carddemo.cbact02c;

import com.carddemo.cbact02c.repository.CardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test for the CBACT02C card-print batch job.
 *
 * <p>Launches the Spring Batch job end-to-end against the in-memory H2 database
 * seeded by {@code db/data.sql} (10 card records).  Validates that the job
 * completes with COMPLETED status and processes all seeded records, mirroring the
 * COBOL behaviour:
 *
 * <pre>
 * COBOL: CBACT02C.cbl — main PERFORM UNTIL END-OF-FILE loop reads and DISPLAYs
 *        every record from CARDFILE-FILE, then terminates normally (GOBACK).
 *        The Java job must complete with BatchStatus.COMPLETED and read count == row count.
 * </pre>
 */
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class Cbact02cIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job cardPrintJob;

    @Autowired
    private CardRepository cardRepository;

    /**
     * Happy-path integration test: job reads all 10 seeded card records and completes.
     *
     * <p>COBOL analogue: run CBACT02C against a CARDFILE with 10 records;
     * expect DISPLAY output for each and RC=0 on GOBACK.
     */
    @Test
    void cardPrintJob_withSeededData_completesSuccessfully() throws Exception {
        jobLauncherTestUtils.setJob(cardPrintJob);

        JobExecution execution = jobLauncherTestUtils.launchJob();

        // COBOL: normal GOBACK = BatchStatus.COMPLETED
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // All step executions must also be COMPLETED (none FAILED/STOPPED)
        execution.getStepExecutions().forEach(step ->
                assertThat(step.getStatus()).isEqualTo(BatchStatus.COMPLETED));
    }

    /**
     * Verifies the reader processes exactly as many records as are in the database.
     *
     * <p>COBOL: 1000-CARDFILE-GET-NEXT increments APPL-RESULT=0 for each successful read;
     * the loop terminates only when CARDFILE-STATUS = '10' (EOF). The read count in the
     * step execution must equal the table row count.
     */
    @Test
    void cardPrintJob_readCountMatchesDatabaseRows() throws Exception {
        jobLauncherTestUtils.setJob(cardPrintJob);

        long rowCount = cardRepository.count();
        JobExecution execution = jobLauncherTestUtils.launchJob();

        long totalRead = execution.getStepExecutions().stream()
                .mapToLong(s -> s.getReadCount())
                .sum();

        assertThat(totalRead).isEqualTo(rowCount);
    }

    /**
     * Verifies no records are skipped or filtered.
     *
     * <p>COBOL: CBACT02C does not filter records — every card read is displayed.
     * Write count must equal read count (processor returns a value for every input).
     */
    @Test
    void cardPrintJob_writeCountEqualsReadCount() throws Exception {
        jobLauncherTestUtils.setJob(cardPrintJob);

        JobExecution execution = jobLauncherTestUtils.launchJob();

        long totalRead = execution.getStepExecutions().stream()
                .mapToLong(s -> s.getReadCount()).sum();
        long totalWrite = execution.getStepExecutions().stream()
                .mapToLong(s -> s.getWriteCount()).sum();

        assertThat(totalWrite).isEqualTo(totalRead);
    }
}
