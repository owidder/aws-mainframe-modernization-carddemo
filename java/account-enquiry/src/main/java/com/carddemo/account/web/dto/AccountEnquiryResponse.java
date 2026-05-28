package com.carddemo.account.web.dto;

import java.math.BigDecimal;

/**
 * Replaces the WS-ACCT-RESPONSE working-storage area in COACCT01.cbl.
 *
 * <p>The COBOL program formatted all fields into a 1000-byte text buffer
 * (REPLY-MESSAGE) that was placed on the MQ reply queue.  This DTO
 * replaces that with a structured JSON response.
 *
 * <p>When the account is found ({@code DFHRESP(NORMAL)}), {@code error} is null
 * and all account fields are populated.
 * When the account is not found ({@code DFHRESP(NOTFND)}) or the request is
 * invalid, only {@code error} is set.
 *
 * @param accountId       ACCT-ID PIC 9(11)
 * @param activeStatus    ACCT-ACTIVE-STATUS PIC X(01) — 'Y' or 'N'
 * @param currentBalance  ACCT-CURR-BAL PIC S9(10)V99
 * @param creditLimit     ACCT-CREDIT-LIMIT PIC S9(10)V99
 * @param cashCreditLimit ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99
 * @param openDate        ACCT-OPEN-DATE PIC X(10) — YYYY-MM-DD
 * @param expirationDate  ACCT-EXPIRAION-DATE (sic) PIC X(10) — YYYY-MM-DD
 * @param reissueDate     ACCT-REISSUE-DATE PIC X(10) — YYYY-MM-DD
 * @param currCycCredit   ACCT-CURR-CYC-CREDIT PIC S9(10)V99
 * @param currCycDebit    ACCT-CURR-CYC-DEBIT PIC S9(10)V99
 * @param groupId         ACCT-GROUP-ID PIC X(10)
 * @param error           null on success; error description on failure
 */
public record AccountEnquiryResponse(
        Long accountId,
        String activeStatus,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        String openDate,
        String expirationDate,
        String reissueDate,
        BigDecimal currCycCredit,
        BigDecimal currCycDebit,
        String groupId,
        String error
) {
    /** Successful response — account found. */
    public static AccountEnquiryResponse success(
            Long accountId,
            String activeStatus,
            BigDecimal currentBalance,
            BigDecimal creditLimit,
            BigDecimal cashCreditLimit,
            String openDate,
            String expirationDate,
            String reissueDate,
            BigDecimal currCycCredit,
            BigDecimal currCycDebit,
            String groupId) {
        return new AccountEnquiryResponse(
                accountId, activeStatus, currentBalance,
                creditLimit, cashCreditLimit,
                openDate, expirationDate, reissueDate,
                currCycCredit, currCycDebit, groupId, null);
    }

    /**
     * Error response — mirrors the COBOL STRING … INTO REPLY-MESSAGE pattern
     * used for DFHRESP(NOTFND) and invalid-request cases.
     */
    public static AccountEnquiryResponse error(String message) {
        return new AccountEnquiryResponse(
                null, null, null, null, null,
                null, null, null, null, null, null, message);
    }
}
