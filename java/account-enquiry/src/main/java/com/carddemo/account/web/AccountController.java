package com.carddemo.account.web;

import com.carddemo.account.service.AccountEnquiryService;
import com.carddemo.account.web.dto.AccountEnquiryRequest;
import com.carddemo.account.web.dto.AccountEnquiryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST entry point replacing the IBM MQ interface of COACCT01.cbl.
 *
 * <h2>Interface mapping</h2>
 * <table border="1">
 *   <tr><th>COBOL</th><th>REST</th></tr>
 *   <tr><td>MQ GET from input queue</td><td>POST /api/accounts/enquiry (request body)</td></tr>
 *   <tr><td>MQ PUT to CARD.DEMO.REPLY.ACCT</td><td>HTTP 200 with JSON body</td></tr>
 *   <tr><td>MQ PUT to CARD.DEMO.ERROR</td><td>HTTP 200 with JSON {@code error} field, or HTTP 500</td></tr>
 *   <tr><td>PERFORM 8000-TERMINATION</td><td>HTTP 500 (unhandled exception)</td></tr>
 * </table>
 *
 * <p>Note: the COBOL program uses a fire-and-forget MQ pattern — the caller's
 * correlation ID is echoed back in the reply.  In this REST mapping the response
 * is returned synchronously, which is the natural HTTP equivalent.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountEnquiryService enquiryService;

    public AccountController(AccountEnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    /**
     * Account enquiry — replaces the COACCT01 MQ request/reply cycle.
     *
     * <p>HTTP 200 is returned in all handled cases (found, not-found, invalid function).
     * This mirrors the COBOL behaviour: even error messages are placed on the reply
     * queue with a normal MQ completion code.
     *
     * @param request validated request DTO
     * @return enquiry response — check {@code error} field for business-level errors
     */
    @PostMapping("/enquiry")
    public ResponseEntity<AccountEnquiryResponse> enquire(
            @RequestBody @Valid AccountEnquiryRequest request) {

        AccountEnquiryResponse response = enquiryService.enquire(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Convenience GET endpoint for quick account lookup by ID.
     * Not in the original COACCT01 — added for REST usability.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountEnquiryResponse> getById(
            @PathVariable Long accountId) {

        AccountEnquiryRequest request = new AccountEnquiryRequest("INQA", accountId);
        AccountEnquiryResponse response = enquiryService.enquire(request);
        return ResponseEntity.ok(response);
    }
}
