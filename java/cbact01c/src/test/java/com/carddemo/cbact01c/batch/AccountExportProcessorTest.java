package com.carddemo.cbact01c.batch;

import com.carddemo.cbact01c.domain.Account;
import com.carddemo.cbact01c.service.DateConversionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccountExportProcessor}.
 *
 * <p>Covers all business rules in CBACT01C paragraphs:
 * <ul>
 *   <li>1300-POPUL-ACCT-RECORD: date conversion and zero-debit default (CBACT01C.cbl:215-240)</li>
 *   <li>1400-POPUL-ARRAY-RECORD: 5-element array with hardcoded values (CBACT01C.cbl:253-261)</li>
 *   <li>1500-POPUL-VBRC-RECORD: VBR1 and VBR2 field population (CBACT01C.cbl:276-285)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AccountExportProcessorTest {

    @Mock
    DateConversionService dateConversionService;

    @InjectMocks
    AccountExportProcessor processor;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setAcctId(1L);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal("1940.00"));
        account.setCreditLimit(new BigDecimal("20200.00"));
        account.setCashCreditLimit(new BigDecimal("10200.00"));
        account.setOpenDate("2014-11-20");
        account.setExpirationDate("2025-05-20");
        account.setReissueDate("2025-05-20");
        account.setCurrCycCredit(BigDecimal.ZERO);
        account.setCurrCycDebit(BigDecimal.ZERO);
        account.setGroupId("GRP1");
    }

    // ── 1300-POPUL-ACCT-RECORD: zero debit default ───────────────────────

    @Test
    @DisplayName("currCycDebit=0 → OutRecord carries 2525.00 (COBOL: IF ACCT-CURR-CYC-DEBIT=ZERO MOVE 2525.00)")
    void process_zeroCycDebit_applies2525Default() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd("2025-05-20")).thenReturn("20250520");

        AccountExportBundle bundle = processor.process(account);

        assertThat(bundle.outRecord().currCycDebit())
                .isEqualByComparingTo("2525.00");
    }

    @Test
    @DisplayName("currCycDebit non-zero → OutRecord carries original value (no substitution)")
    void process_nonZeroCycDebit_passesThrough() throws Exception {
        account.setCurrCycDebit(new BigDecimal("200.00"));
        when(dateConversionService.convertYyyyMmDdToYyyymmdd("2025-05-20")).thenReturn("20250520");

        AccountExportBundle bundle = processor.process(account);

        assertThat(bundle.outRecord().currCycDebit())
                .isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("currCycDebit=100.00 → not replaced (only zero triggers default)")
    void process_smallNonZeroCycDebit_notReplaced() throws Exception {
        account.setCurrCycDebit(new BigDecimal("100.00"));
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        AccountExportBundle bundle = processor.process(account);

        assertThat(bundle.outRecord().currCycDebit())
                .isEqualByComparingTo("100.00");
    }

    // ── 1300-POPUL-ACCT-RECORD: date conversion ──────────────────────────

    @Test
    @DisplayName("Reissue date is delegated to DateConversionService (CALL 'COBDATFT')")
    void process_reissueDateFormatted_viaDateService() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd("2025-05-20")).thenReturn("20250520");

        AccountExportBundle bundle = processor.process(account);

        assertThat(bundle.outRecord().reissueDate()).isEqualTo("20250520");
        verify(dateConversionService).convertYyyyMmDdToYyyymmdd("2025-05-20");
    }

    @Test
    @DisplayName("All OutRecord fields are populated from account")
    void process_outRecord_allFieldsMapped() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd("2025-05-20")).thenReturn("20250520");

        OutRecord r = processor.process(account).outRecord();

        assertThat(r.acctId()).isEqualTo(1L);
        assertThat(r.activeStatus()).isEqualTo("Y");
        assertThat(r.currBal()).isEqualByComparingTo("1940.00");
        assertThat(r.creditLimit()).isEqualByComparingTo("20200.00");
        assertThat(r.cashCreditLimit()).isEqualByComparingTo("10200.00");
        assertThat(r.openDate()).isEqualTo("2014-11-20");
        assertThat(r.expirationDate()).isEqualTo("2025-05-20");
        assertThat(r.reissueDate()).isEqualTo("20250520");
        assertThat(r.groupId()).isEqualTo("GRP1");
    }

    // ── 1400-POPUL-ARRAY-RECORD ───────────────────────────────────────────

    @Test
    @DisplayName("ArrayRecord has exactly 5 balance entries (ARR-ACCT-BAL OCCURS 5 TIMES)")
    void process_arrayRecord_hasFiveEntries() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        ArrayRecord arr = processor.process(account).arrayRecord();

        assertThat(arr.acctId()).isEqualTo(1L);
        assertThat(arr.balances()).hasSize(5);
    }

    @Test
    @DisplayName("Array entry 1: currBal from account, debit=1005.00 (CBACT01C.cbl:255-256)")
    void process_arrayEntry1_usesAccountCurrBalAndHardcodedDebit() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        ArrayRecord arr = processor.process(account).arrayRecord();

        assertThat(arr.balances().get(0).currBal()).isEqualByComparingTo("1940.00");
        assertThat(arr.balances().get(0).currCycDebit()).isEqualByComparingTo("1005.00");
    }

    @Test
    @DisplayName("Array entry 2: currBal from account, debit=1525.00 (CBACT01C.cbl:257-258)")
    void process_arrayEntry2_usesAccountCurrBalAndHardcodedDebit() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        ArrayRecord arr = processor.process(account).arrayRecord();

        assertThat(arr.balances().get(1).currBal()).isEqualByComparingTo("1940.00");
        assertThat(arr.balances().get(1).currCycDebit()).isEqualByComparingTo("1525.00");
    }

    @Test
    @DisplayName("Array entry 3: hardcoded -1025.00 and -2500.00 (CBACT01C.cbl:259-260)")
    void process_arrayEntry3_usesHardcodedNegativeValues() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        ArrayRecord arr = processor.process(account).arrayRecord();

        assertThat(arr.balances().get(2).currBal()).isEqualByComparingTo("-1025.00");
        assertThat(arr.balances().get(2).currCycDebit()).isEqualByComparingTo("-2500.00");
    }

    @Test
    @DisplayName("Array entries 4 and 5 are zero (INITIALIZE ARR-ARRAY-REC, CBACT01C.cbl:169)")
    void process_arrayEntries4And5_areZero() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        ArrayRecord arr = processor.process(account).arrayRecord();

        assertThat(arr.balances().get(3).currBal()).isEqualByComparingTo("0.00");
        assertThat(arr.balances().get(3).currCycDebit()).isEqualByComparingTo("0.00");
        assertThat(arr.balances().get(4).currBal()).isEqualByComparingTo("0.00");
        assertThat(arr.balances().get(4).currCycDebit()).isEqualByComparingTo("0.00");
    }

    // ── 1500-POPUL-VBRC-RECORD ───────────────────────────────────────────

    @Test
    @DisplayName("VBR1 carries acctId and activeStatus (CBACT01C.cbl:277-279)")
    void process_vbr1_hasCorrectFields() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        VbrRecord1 vbr1 = processor.process(account).vbrRecord1();

        assertThat(vbr1.acctId()).isEqualTo(1L);
        assertThat(vbr1.activeStatus()).isEqualTo("Y");
    }

    @Test
    @DisplayName("VBR2 reissueYear is first 4 chars of ACCT-REISSUE-DATE (WS-ACCT-REISSUE-YYYY, CBACT01C.cbl:282)")
    void process_vbr2_reissueYearIsYearPortion() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        VbrRecord2 vbr2 = processor.process(account).vbrRecord2();

        assertThat(vbr2.reissueYear()).isEqualTo("2025");
    }

    @Test
    @DisplayName("VBR2 carries acctId, currBal, creditLimit (CBACT01C.cbl:277-281)")
    void process_vbr2_hasCorrectFinancialFields() throws Exception {
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(any())).thenReturn("20250520");

        VbrRecord2 vbr2 = processor.process(account).vbrRecord2();

        assertThat(vbr2.acctId()).isEqualTo(1L);
        assertThat(vbr2.currBal()).isEqualByComparingTo("1940.00");
        assertThat(vbr2.creditLimit()).isEqualByComparingTo("20200.00");
    }

    @Test
    @DisplayName("Null reissueDate → VBR2 reissueYear is 4 spaces")
    void process_nullReissueDate_vbr2YearIsSpaces() throws Exception {
        account.setReissueDate(null);
        when(dateConversionService.convertYyyyMmDdToYyyymmdd(null)).thenReturn("        ");

        VbrRecord2 vbr2 = processor.process(account).vbrRecord2();

        assertThat(vbr2.reissueYear()).isEqualTo("    ");
    }
}
