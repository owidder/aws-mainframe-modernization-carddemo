package com.carddemo.account;

import com.carddemo.account.web.dto.AccountEnquiryRequest;
import com.carddemo.account.web.dto.AccountEnquiryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests — Spring Boot context + real H2 database + HTTP client.
 *
 * <p>These tests exercise the complete stack: REST controller → service → JPA → H2.
 * Test data is loaded from {@code src/main/resources/db/data.sql}.
 *
 * <p>Covers the three COACCT01 response paths:
 * <ol>
 *   <li>Account found (DFHRESP NORMAL)</li>
 *   <li>Account not found (DFHRESP NOTFND)</li>
 *   <li>Invalid function code</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountEnquiryIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port + "/api/accounts";
    }

    // ── Happy path ────────────────────────────────────────────────────

    @Test
    @DisplayName("INQA account 1 — found in H2 (data.sql seed), all fields returned")
    void fullStack_accountFound_returnsAllFields() {
        var request = new AccountEnquiryRequest("INQA", 1L);

        ResponseEntity<AccountEnquiryResponse> resp =
                rest.postForEntity(base() + "/enquiry", request,
                        AccountEnquiryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AccountEnquiryResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isNull();
        assertThat(body.accountId()).isEqualTo(1L);
        assertThat(body.activeStatus()).isEqualTo("Y");
        assertThat(body.currentBalance()).isNotNull();
        assertThat(body.creditLimit()).isNotNull();
        assertThat(body.openDate()).isEqualTo("2014-11-20");
        assertThat(body.expirationDate()).isEqualTo("2025-05-20");
    }

    @Test
    @DisplayName("INQA account 7 — has non-zero cycle credits/debits and groupId")
    void fullStack_accountWithCycleData() {
        ResponseEntity<AccountEnquiryResponse> resp =
                rest.postForEntity(base() + "/enquiry",
                        new AccountEnquiryRequest("INQA", 7L),
                        AccountEnquiryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AccountEnquiryResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isNull();
        assertThat(body.currCycCredit()).isPositive();
        assertThat(body.currCycDebit()).isPositive();
        assertThat(body.groupId()).isEqualTo("GRP2");
    }

    @Test
    @DisplayName("GET /api/accounts/2 — convenience endpoint works end-to-end")
    void fullStack_getById_found() {
        ResponseEntity<AccountEnquiryResponse> resp =
                rest.getForEntity(base() + "/2", AccountEnquiryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().accountId()).isEqualTo(2L);
        assertThat(resp.getBody().error()).isNull();
    }

    // ── Not found (DFHRESP NOTFND) ───────────────────────────────────

    @Test
    @DisplayName("INQA account 99999 — not in H2, returns 200 with error field")
    void fullStack_accountNotFound_returnsErrorField() {
        ResponseEntity<AccountEnquiryResponse> resp =
                rest.postForEntity(base() + "/enquiry",
                        new AccountEnquiryRequest("INQA", 99999L),
                        AccountEnquiryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AccountEnquiryResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error())
                .contains("INVALID REQUEST PARAMETERS")
                .contains("99999");
        assertThat(body.accountId()).isNull();
    }

    // ── Invalid function code ─────────────────────────────────────────

    @Test
    @DisplayName("Wrong function code → 200 with error field (not 400)")
    void fullStack_wrongFunctionCode_returns200WithError() {
        ResponseEntity<AccountEnquiryResponse> resp =
                rest.postForEntity(base() + "/enquiry",
                        new AccountEnquiryRequest("UNKN", 1L),
                        AccountEnquiryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().error())
                .contains("INVALID REQUEST PARAMETERS")
                .contains("FUNCTION")
                .contains("UNKN");
    }

    // ── Validation (HTTP 400) ─────────────────────────────────────────

    @Test
    @DisplayName("accountId=0 fails Bean Validation — HTTP 400 (WS-KEY = ZEROES guard)")
    void fullStack_zeroAccountId_returns400() {
        // Cannot use AccountEnquiryRequest record here because @Positive rejects 0;
        // send raw JSON with explicit Content-Type to test server-side validation
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> req = new HttpEntity<>(
                "{\"functionCode\":\"INQA\",\"accountId\":0}", headers);

        ResponseEntity<AccountEnquiryResponse> resp =
                rest.postForEntity(base() + "/enquiry", req, AccountEnquiryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Inactive account ──────────────────────────────────────────────

    @Test
    @DisplayName("Inactive account (status='N') is returned — COBOL applies no status filter")
    void fullStack_inactiveAccount_returnedAsIs() {
        // Account 6 has active_status='N' in data.sql
        ResponseEntity<AccountEnquiryResponse> resp =
                rest.postForEntity(base() + "/enquiry",
                        new AccountEnquiryRequest("INQA", 6L),
                        AccountEnquiryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AccountEnquiryResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isNull();
        assertThat(body.activeStatus()).isEqualTo("N");
    }
}
