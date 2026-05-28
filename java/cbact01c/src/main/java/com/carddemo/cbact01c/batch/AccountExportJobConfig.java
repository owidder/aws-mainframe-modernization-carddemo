package com.carddemo.cbact01c.batch;

import com.carddemo.cbact01c.domain.Account;
import com.carddemo.cbact01c.repository.AccountRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * Spring Batch configuration for the CBACT01C account file export job.
 *
 * <p>Maps the COBOL PROCEDURE DIVISION main flow (CBACT01C.cbl:140-160):
 * <pre>
 * L141  DISPLAY 'START OF EXECUTION'          → Spring Boot startup log
 * L142  PERFORM 0000-ACCTFILE-OPEN            → accountReader (ItemStream.open)
 * L143  PERFORM 2000-OUTFILE-OPEN             → outFileWriter (ItemStream.open via MultiFileItemWriter)
 * L144  PERFORM 3000-ARRFILE-OPEN             → arryFileWriter (ItemStream.open)
 * L145  PERFORM 4000-VBRFILE-OPEN             → vbrcFileWriter (ItemStream.open)
 * L147-154  PERFORM UNTIL END-OF-FILE         → chunk-oriented step (reader→processor→writer)
 * L156  PERFORM 9000-ACCTFILE-CLOSE           → step teardown (ItemStream.close)
 * L158  DISPLAY 'END OF EXECUTION'            → Spring Batch job completion log
 * </pre>
 */
@Configuration
public class AccountExportJobConfig {

    /** Output directory; override with {@code app.output-dir} property. */
    @Value("${app.output-dir:target/output}")
    private String outputDir;

    // ── Job ──────────────────────────────────────────────────────────────

    /**
     * Mirrors the CBACT01C top-level PROCEDURE DIVISION flow.
     * The {@link JobExecutionListener} ensures the output directory exists before
     * the step tries to open any {@link FlatFileItemWriter}.
     */
    @Bean
    public Job accountExportJob(JobRepository jobRepository, Step accountExportStep) {
        return new JobBuilder("accountExportJob", jobRepository)
                .listener(outputDirListener())
                .start(accountExportStep)
                .build();
    }

    // ── Step ─────────────────────────────────────────────────────────────

    /**
     * Chunk-oriented step: reads accounts in pages of 10, processes each, writes to 3 files.
     * Chunk size 10 gives restart granularity without excessive I/O overhead.
     */
    @Bean
    public Step accountExportStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            RepositoryItemReader<Account> accountReader,
            AccountExportProcessor processor,
            MultiFileItemWriter multiFileItemWriter) {
        return new StepBuilder("accountExportStep", jobRepository)
                .<Account, AccountExportBundle>chunk(10, txManager)
                .reader(accountReader)
                .processor(processor)
                .writer(multiFileItemWriter)
                .build();
    }

    // ── Reader ───────────────────────────────────────────────────────────

    /**
     * Reads accounts sequentially ordered by ACCT-ID, mirroring the COBOL
     * {@code ACCESS MODE IS SEQUENTIAL} on the KSDS ACCTFILE.
     *
     * <p>COBOL: CBACT01C.cbl:29-33 — SELECT ACCTFILE-FILE with SEQUENTIAL access.
     * COBOL: CBACT01C.cbl:165-198 — 1000-ACCTFILE-GET-NEXT → READ ACCTFILE-FILE
     */
    @Bean
    public RepositoryItemReader<Account> accountReader(AccountRepository accountRepository) {
        return new RepositoryItemReaderBuilder<Account>()
                .name("accountReader")
                .repository(accountRepository)
                .methodName("findAll")
                .sorts(Map.of("acctId", Sort.Direction.ASC))
                .pageSize(10)
                .build();
    }

    // ── Writer ───────────────────────────────────────────────────────────

    /**
     * Composite writer for all three output files. Spring Batch auto-registers it
     * as an {@link org.springframework.batch.item.ItemStream} via the step builder.
     */
    @Bean
    public MultiFileItemWriter multiFileItemWriter() {
        return new MultiFileItemWriter(outFileWriter(), arryFileWriter(), vbrcFileWriter());
    }

    /**
     * OUTFILE: one flat record per account.
     * COBOL: CBACT01C.cbl:56-69 — FD OUT-FILE / 1350-WRITE-ACCT-RECORD
     *
     * <p>Format: ACCT-ID|ACTIVE|CURR-BAL|CREDIT-LIMIT|CASH-CREDIT-LIMIT|
     * OPEN-DATE|EXPIRATION-DATE|REISSUE-DATE(YYYYMMDD)|CYC-CREDIT|CYC-DEBIT|GROUP-ID
     */
    private FlatFileItemWriter<OutRecord> outFileWriter() {
        new File(outputDir).mkdirs();
        return new FlatFileItemWriterBuilder<OutRecord>()
                .name("outFileWriter")
                .resource(new FileSystemResource(outputDir + "/OUTFILE.txt"))
                .lineAggregator(r -> String.format(Locale.US, "%011d|%s|%14.2f|%14.2f|%14.2f|%s|%s|%s|%14.2f|%14.2f|%s",
                        r.acctId(),
                        r.activeStatus(),
                        r.currBal(),
                        r.creditLimit(),
                        r.cashCreditLimit(),
                        r.openDate(),
                        r.expirationDate(),
                        r.reissueDate(),
                        r.currCycCredit(),
                        r.currCycDebit(),
                        r.groupId() != null ? r.groupId() : ""))
                .build();
    }

    /**
     * ARRYFILE: one record per account with 5 balance/debit pairs.
     * COBOL: CBACT01C.cbl:71-78 — FD ARRY-FILE / 1450-WRITE-ARRY-RECORD
     *
     * <p>Format: ACCT-ID|BAL1|DEBIT1|BAL2|DEBIT2|BAL3|DEBIT3|BAL4|DEBIT4|BAL5|DEBIT5
     */
    private FlatFileItemWriter<ArrayRecord> arryFileWriter() {
        return new FlatFileItemWriterBuilder<ArrayRecord>()
                .name("arryFileWriter")
                .resource(new FileSystemResource(outputDir + "/ARRYFILE.txt"))
                .lineAggregator(r -> {
                    StringBuilder sb = new StringBuilder(String.format(Locale.US, "%011d", r.acctId()));
                    for (ArrayBalanceEntry e : r.balances()) {
                        sb.append(String.format(Locale.US, "|%14.2f|%14.2f", e.currBal(), e.currCycDebit()));
                    }
                    return sb.toString();
                })
                .build();
    }

    /**
     * VBRCFILE: two variable-length records per account (VBR1 then VBR2).
     * COBOL: CBACT01C.cbl:80-85 — FD VBRC-FILE (RECORDING MODE IS V)
     * / 1550-WRITE-VB1-RECORD + 1575-WRITE-VB2-RECORD
     */
    private FlatFileItemWriter<String> vbrcFileWriter() {
        return new FlatFileItemWriterBuilder<String>()
                .name("vbrcFileWriter")
                .resource(new FileSystemResource(outputDir + "/VBRCFILE.txt"))
                .lineAggregator(line -> line)
                .build();
    }

    // ── Listener ─────────────────────────────────────────────────────────

    /** Ensures output directory exists before any writer opens its file. */
    private JobExecutionListener outputDirListener() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                new File(outputDir).mkdirs();
            }
        };
    }
}
