package com.carddemo.account.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Replaces the MQ request message layout from COACCT01.cbl:
 * <pre>
 *   01 REQUEST-MSG-COPY.
 *      10 WS-FUNC   PIC X(04) VALUE SPACES.   ← functionCode
 *      10 WS-KEY    PIC 9(11) VALUE ZEROES.   ← accountId
 *      10 WS-FILLER PIC X(985).
 * </pre>
 *
 * <p>The COBOL program validates {@code WS-FUNC = 'INQA'} and {@code WS-KEY > ZEROES}
 * before performing the account lookup.  The same guard is implemented in
 * {@link com.carddemo.account.service.AccountEnquiryService#enquire}.
 *
 * @param functionCode must be "INQA" to trigger an account lookup
 * @param accountId    ACCT-ID PIC 9(11); must be positive
 */
public record AccountEnquiryRequest(

        @NotBlank
        String functionCode,

        @NotNull @Positive
        Long accountId
) {}
