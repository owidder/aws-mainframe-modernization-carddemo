package com.carddemo.account.service;

import com.carddemo.account.domain.Account;
import com.carddemo.account.repository.AccountRepository;
import com.carddemo.account.web.dto.AccountEnquiryRequest;
import com.carddemo.account.web.dto.AccountEnquiryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AccountEnquiryService}.
 *
 * <p>Tests replicate the three branches of COACCT01 paragraph
 * {@code 4000-PROCESS-REQUEST-REPLY}:
 * <ol>
 *   <li>DFHRESP(NORMAL)  → account found, all fields populated</li>
 *   <li>DFHRESP(NOTFND)  → account not found, error response returned</li>
 *   <li>Invalid function → error response (IF WS-FUNC ≠ 'INQA' branch)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AccountEnquiryServiceTest {

    @Mock
    AccountRepository accountRepository;

    @InjectMocks
    AccountEnquiryService service;

    private Account sampleAccount;

    @BeforeEach
    void setUp() {
        sampleAccount = new Account();
        sampleAccount.setAcctId(1L);
        sampleAccount.setActiveStatus("Y");
        sampleAccount.setCurrBal(new BigDecimal("1940.00"));
        sampleAccount.setCreditLimit(new BigDecimal("20200.00"));
        sampleAccount.setCashCreditLimit(new BigDecimal("10200.00"));
        sampleAccount.setOpenDate("2014-11-20");
        sampleAccount.setExpirationDate("2025-05-20");
        sampleAccount.setReissueDate("2025-05-20");
        sampleAccount.setCurrCycCredit(BigDecimal.ZERO);
        sampleAccount.setCurrCycDebit(BigDecimal.ZERO);
        sampleAccount.setGroupId(null);
    }

    // ── DFHRESP(NORMAL) — account found ──────────────────────────────

    @Test
    @DisplayName("INQA + valid ID → success response with all account fields")
    void enquire_inqa_accountFound_returnsSuccess() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(sampleAccount));

        var response = service.enquire(new AccountEnquiryRequest("INQA", 1L));

        assertThat(response.error()).isNull();
        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.activeStatus()).isEqualTo("Y");
        assertThat(response.currentBalance()).isEqualByComparingTo("1940.00");
        assertThat(response.creditLimit()).isEqualByComparingTo("20200.00");
        assertThat(response.cashCreditLimit()).isEqualByComparingTo("10200.00");
        assertThat(response.openDate()).isEqualTo("2014-11-20");
        assertThat(response.expirationDate()).isEqualTo("2025-05-20");
        assertThat(response.reissueDate()).isEqualTo("2025-05-20");
        assertThat(response.currCycCredit()).isEqualByComparingTo("0.00");
        assertThat(response.currCycDebit()).isEqualByComparingTo("0.00");

        verify(accountRepository).findById(1L);
    }

    @Test
    @DisplayName("All account fields are mapped — no MOVE … TO WS-ACCT-* is skipped")
    void enquire_allFieldsMapped() {
        sampleAccount.setCurrCycCredit(new BigDecimal("500.00"));
        sampleAccount.setCurrCycDebit(new BigDecimal("200.00"));
        sampleAccount.setGroupId("GRP2");
        when(accountRepository.findById(7L)).thenReturn(Optional.of(sampleAccount));

        var response = service.enquire(new AccountEnquiryRequest("INQA", 7L));

        assertThat(response.currCycCredit()).isEqualByComparingTo("500.00");
        assertThat(response.currCycDebit()).isEqualByComparingTo("200.00");
        assertThat(response.groupId()).isEqualTo("GRP2");
    }

    // ── DFHRESP(NOTFND) — account not found ──────────────────────────

    @Test
    @DisplayName("INQA + unknown ID → error response (mirrors DFHRESP(NOTFND))")
    void enquire_inqa_accountNotFound_returnsError() {
        when(accountRepository.findById(99999L)).thenReturn(Optional.empty());

        var response = service.enquire(new AccountEnquiryRequest("INQA", 99999L));

        assertThat(response.error())
                .contains("INVALID REQUEST PARAMETERS")
                .contains("99999");
        assertThat(response.accountId()).isNull();
        assertThat(response.activeStatus()).isNull();

        verify(accountRepository).findById(99999L);
    }

    // ── Invalid function code ─────────────────────────────────────────

    @Test
    @DisplayName("Wrong function code → error response, no DB call (IF WS-FUNC ≠ 'INQA')")
    void enquire_wrongFunctionCode_returnsErrorWithoutDbCall() {
        var response = service.enquire(new AccountEnquiryRequest("XXXX", 1L));

        assertThat(response.error())
                .contains("INVALID REQUEST PARAMETERS")
                .contains("FUNCTION")
                .contains("XXXX");

        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Function code is case-sensitive — 'inqa' must fail (COBOL WS-FUNC = 'INQA')")
    void enquire_lowercaseFunctionCode_returnsError() {
        var response = service.enquire(new AccountEnquiryRequest("inqa", 1L));

        assertThat(response.error()).isNotNull();
        verifyNoInteractions(accountRepository);
    }

    // ── Repository interaction ────────────────────────────────────────

    @Test
    @DisplayName("Repository is called exactly once per enquiry (no caching, no extra reads)")
    void enquire_repositoryCalledOnce() {
        when(accountRepository.findById(2L)).thenReturn(Optional.of(sampleAccount));

        service.enquire(new AccountEnquiryRequest("INQA", 2L));

        verify(accountRepository, times(1)).findById(2L);
        verifyNoMoreInteractions(accountRepository);
    }

    // ── Business rules ────────────────────────────────────────────────

    @Test
    @DisplayName("Inactive account (status='N') is returned without modification (no filtering in COBOL)")
    void enquire_inactiveAccount_returnedAsIs() {
        sampleAccount.setActiveStatus("N");
        when(accountRepository.findById(6L)).thenReturn(Optional.of(sampleAccount));

        var response = service.enquire(new AccountEnquiryRequest("INQA", 6L));

        // COBOL does no status filtering — all accounts are returned
        assertThat(response.error()).isNull();
        assertThat(response.activeStatus()).isEqualTo("N");
    }
}
