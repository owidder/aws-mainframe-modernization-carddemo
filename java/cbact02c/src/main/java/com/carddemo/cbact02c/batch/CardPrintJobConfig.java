package com.carddemo.cbact02c.batch;

import com.carddemo.cbact02c.domain.CardRecord;
import com.carddemo.cbact02c.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/**
 * Spring Batch configuration for the CBACT02C card-file print job.
 *
 * <p>Translates the COBOL PROCEDURE DIVISION of CBACT02C.cbl:
 * <pre>
 * L0000-CARDFILE-OPEN   → accountReader (ItemStream.open called by framework)
 * PERFORM UNTIL EOF     → chunk-oriented step (reader → processor → writer loop)
 *   1000-CARDFILE-GET-NEXT → cardReader.read()
 *   DISPLAY CARD-RECORD    → CardDisplayProcessor + cardDisplayWriter
 * 9000-CARDFILE-CLOSE   → cardReader (ItemStream.close called by framework)
 * </pre>
 *
 * <p>The VSAM KSDS sequential scan (ACCESS MODE IS SEQUENTIAL, RECORD KEY IS
 * FD-CARD-NUM) maps to {@code RepositoryItemReader} ordered ascending by
 * {@code cardNum}, which preserves the same key-ordered traversal order.
 */
@Configuration
public class CardPrintJobConfig {

    private static final Logger log = LoggerFactory.getLogger(CardPrintJobConfig.class);

    // ── Job ──────────────────────────────────────────────────────────────────

    /**
     * Top-level job bean — equivalent to the CBACT02C PROCEDURE DIVISION mainline.
     *
     * <p>COBOL: CBACT02C.cbl — DISPLAY 'START/END OF EXECUTION' wrap the single step.
     */
    @Bean
    public Job cardPrintJob(JobRepository jobRepository, Step cardPrintStep) {
        return new JobBuilder("cardPrintJob", jobRepository)
                .start(cardPrintStep)
                .build();
    }

    // ── Step ─────────────────────────────────────────────────────────────────

    /**
     * Single chunk-oriented step: read cards in pages of 20, format each, write to log.
     *
     * <p>Chunk size 20 gives restart granularity suitable for the ~50-row test dataset
     * and scales to production card volumes.
     */
    @Bean
    public Step cardPrintStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            RepositoryItemReader<CardRecord> cardReader,
            CardDisplayProcessor processor,
            ItemWriter<String> cardDisplayWriter) {
        return new StepBuilder("cardPrintStep", jobRepository)
                .<CardRecord, String>chunk(20, txManager)
                .reader(cardReader)
                .processor(processor)
                .writer(cardDisplayWriter)
                .build();
    }

    // ── Reader ───────────────────────────────────────────────────────────────

    /**
     * Reads card records ordered by card_num ASC, mirroring the COBOL KSDS sequential scan.
     *
     * <p>COBOL: CBACT02C.cbl:28-33
     * <pre>
     * SELECT CARDFILE-FILE ASSIGN TO CARDFILE
     *        ORGANIZATION IS INDEXED
     *        ACCESS MODE  IS SEQUENTIAL
     *        RECORD KEY   IS FD-CARD-NUM
     * </pre>
     * COBOL: CBACT02C.cbl:79-97 — 1000-CARDFILE-GET-NEXT → READ CARDFILE-FILE INTO CARD-RECORD
     */
    @Bean
    public RepositoryItemReader<CardRecord> cardReader(CardRepository cardRepository) {
        return new RepositoryItemReaderBuilder<CardRecord>()
                .name("cardReader")
                .repository(cardRepository)
                .methodName("findAll")
                .sorts(Map.of("cardNum", Sort.Direction.ASC))
                .pageSize(20)
                .build();
    }

    // ── Writer ───────────────────────────────────────────────────────────────

    /**
     * Writes each formatted card line to the application log at INFO level.
     *
     * <p>Mirrors COBOL {@code DISPLAY CARD-RECORD} (CBACT02C.cbl — main loop body),
     * which writes the raw 150-byte record to SYSOUT.  Using SLF4J INFO preserves
     * the same observable output while integrating with Spring's logging infrastructure.
     *
     * <p>COBOL: 9999-ABEND-PROGRAM (CBACT02C.cbl) maps to an unchecked exception
     * propagated out of the step; Spring Batch marks the step as FAILED and
     * the {@code GlobalExceptionHandler} converts it to a structured error response.
     */
    @Bean
    public ItemWriter<String> cardDisplayWriter() {
        return chunk -> {
            // COBOL: DISPLAY 'START OF EXECUTION OF PROGRAM CBACT02C' — logged at job start
            for (String line : chunk.getItems()) {
                // COBOL: DISPLAY CARD-RECORD
                log.info("CARD-RECORD: {}", line);
            }
        };
    }
}
