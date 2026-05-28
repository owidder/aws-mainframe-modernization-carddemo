package com.carddemo.cbact01c.batch;

import com.carddemo.cbact01c.domain.Account;
import com.carddemo.cbact01c.service.DateConversionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps one {@link Account} (from ACCTFILE) into an {@link AccountExportBundle}
 * containing records for all three output files.
 *
 * <p>Mirrors the CBACT01C main loop processing:
 * <pre>
 * L170-213  1100-DISPLAY-ACCT-RECORD  → log.debug(account fields)
 * L215-240  1300-POPUL-ACCT-RECORD    → populate OutRecord
 *             L223-233  CALL 'COBDATFT' → dateConversionService.convertYyyyMmDdToYyyymmdd
 *             L236-238  IF ACCT-CURR-CYC-DEBIT = ZERO → MOVE 2525.00
 * L253-261  1400-POPUL-ARRAY-RECORD   → populate ArrayRecord (5 balance slots, entries 4-5 zero)
 * L276-285  1500-POPUL-VBRC-RECORD    → populate VbrRecord1 + VbrRecord2
 * </pre>
 */
@Component
public class AccountExportProcessor implements ItemProcessor<Account, AccountExportBundle> {

    private static final Logger log = LoggerFactory.getLogger(AccountExportProcessor.class);

    /**
     * Default substituted when ACCT-CURR-CYC-DEBIT = ZERO.
     * COBOL: CBACT01C.cbl:237 — MOVE 2525.00 TO OUT-ACCT-CURR-CYC-DEBIT
     */
    static final BigDecimal DEFAULT_CYC_DEBIT = new BigDecimal("2525.00");

    private final DateConversionService dateConversionService;

    public AccountExportProcessor(DateConversionService dateConversionService) {
        this.dateConversionService = dateConversionService;
    }

    @Override
    public AccountExportBundle process(Account account) {
        // COBOL: CBACT01C.cbl:200-213 — 1100-DISPLAY-ACCT-RECORD
        log.debug("ACCT-ID: {} ACCT-ACTIVE-STATUS: {} ACCT-CURR-BAL: {} ACCT-CREDIT-LIMIT: {} "
                + "ACCT-CASH-CREDIT-LIMIT: {} ACCT-OPEN-DATE: {} ACCT-EXPIRAION-DATE: {} "
                + "ACCT-REISSUE-DATE: {} ACCT-CURR-CYC-CREDIT: {} ACCT-CURR-CYC-DEBIT: {} ACCT-GROUP-ID: {}",
                account.getAcctId(), account.getActiveStatus(), account.getCurrBal(),
                account.getCreditLimit(), account.getCashCreditLimit(),
                account.getOpenDate(), account.getExpirationDate(), account.getReissueDate(),
                account.getCurrCycCredit(), account.getCurrCycDebit(), account.getGroupId());

        // COBOL: CBACT01C.cbl:215-240 — 1300-POPUL-ACCT-RECORD
        // CALL 'COBDATFT': CODATECN-TYPE='2' (YYYY-MM-DD in), CODATECN-OUTTYPE='2' (YYYYMMDD out)
        String formattedReissueDate = dateConversionService.convertYyyyMmDdToYyyymmdd(account.getReissueDate());

        // COBOL: CBACT01C.cbl:236-238 — IF ACCT-CURR-CYC-DEBIT EQUAL TO ZERO MOVE 2525.00
        BigDecimal outCycDebit = BigDecimal.ZERO.compareTo(account.getCurrCycDebit()) == 0
                ? DEFAULT_CYC_DEBIT
                : account.getCurrCycDebit();

        OutRecord outRecord = new OutRecord(
                account.getAcctId(),
                account.getActiveStatus(),
                account.getCurrBal(),
                account.getCreditLimit(),
                account.getCashCreditLimit(),
                account.getOpenDate(),
                account.getExpirationDate(),
                formattedReissueDate,
                account.getCurrCycCredit(),
                outCycDebit,
                account.getGroupId()
        );

        // COBOL: CBACT01C.cbl:253-261 — 1400-POPUL-ARRAY-RECORD
        // INITIALIZE ARR-ARRAY-REC sets all 5 slots to zero before populating entries 1-3
        List<ArrayBalanceEntry> entries = List.of(
                new ArrayBalanceEntry(account.getCurrBal(), new BigDecimal("1005.00")),         // entry 1
                new ArrayBalanceEntry(account.getCurrBal(), new BigDecimal("1525.00")),         // entry 2
                new ArrayBalanceEntry(new BigDecimal("-1025.00"), new BigDecimal("-2500.00")),  // entry 3
                new ArrayBalanceEntry(BigDecimal.ZERO, BigDecimal.ZERO),                        // entry 4 (INITIALIZE)
                new ArrayBalanceEntry(BigDecimal.ZERO, BigDecimal.ZERO)                         // entry 5 (INITIALIZE)
        );
        ArrayRecord arrayRecord = new ArrayRecord(account.getAcctId(), entries);

        // COBOL: CBACT01C.cbl:276-285 — 1500-POPUL-VBRC-RECORD
        // WS-ACCT-REISSUE-YYYY = first 4 chars of ACCT-REISSUE-DATE (YYYY-MM-DD → "YYYY")
        VbrRecord1 vbr1 = new VbrRecord1(account.getAcctId(), account.getActiveStatus());
        VbrRecord2 vbr2 = new VbrRecord2(
                account.getAcctId(),
                account.getCurrBal(),
                account.getCreditLimit(),
                extractYear(account.getReissueDate())
        );

        return new AccountExportBundle(outRecord, arrayRecord, vbr1, vbr2);
    }

    /**
     * Extracts the YYYY portion from a YYYY-MM-DD date string.
     * Mirrors WS-ACCT-REISSUE-YYYY PIC X(04) which is the first 4 chars of
     * the WS-ACCT-REISSUE-DATE group item (CBACT01C.cbl:131-136).
     */
    private String extractYear(String date) {
        if (date != null && date.length() >= 4) {
            return date.substring(0, 4);
        }
        return "    ";
    }
}
