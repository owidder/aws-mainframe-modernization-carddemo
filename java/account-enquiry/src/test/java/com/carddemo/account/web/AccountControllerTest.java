package com.carddemo.account.web;

import com.carddemo.account.service.AccountEnquiryService;
import com.carddemo.account.web.dto.AccountEnquiryRequest;
import com.carddemo.account.web.dto.AccountEnquiryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link AccountController}.
 *
 * <p>Verifies HTTP layer concerns: routing, request/response serialisation,
 * validation error mapping, and status codes.
 */
@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  AccountEnquiryService enquiryService;

    private static final AccountEnquiryResponse SUCCESS_RESPONSE =
            AccountEnquiryResponse.success(
                    1L, "Y",
                    new BigDecimal("1940.00"),
                    new BigDecimal("20200.00"),
                    new BigDecimal("10200.00"),
                    "2014-11-20", "2025-05-20", "2025-05-20",
                    BigDecimal.ZERO, BigDecimal.ZERO, null);

    // ── POST /api/accounts/enquiry ────────────────────────────────────

    @Test
    @DisplayName("POST /api/accounts/enquiry — valid INQA request returns 200 with account data")
    void postEnquiry_valid_returns200() throws Exception {
        when(enquiryService.enquire(any())).thenReturn(SUCCESS_RESPONSE);

        mockMvc.perform(post("/api/accounts/enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"functionCode":"INQA","accountId":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.activeStatus").value("Y"))
                .andExpect(jsonPath("$.currentBalance").value(1940.00))
                .andExpect(jsonPath("$.creditLimit").value(20200.00))
                .andExpect(jsonPath("$.openDate").value("2014-11-20"));
    }

    @Test
    @DisplayName("POST /api/accounts/enquiry — not found returns 200 with error field")
    void postEnquiry_notFound_returns200WithError() throws Exception {
        when(enquiryService.enquire(any()))
                .thenReturn(AccountEnquiryResponse.error(
                        "INVALID REQUEST PARAMETERS ACCT ID : 99999"));

        mockMvc.perform(post("/api/accounts/enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"functionCode":"INQA","accountId":99999}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(containsString("INVALID REQUEST PARAMETERS")))
                .andExpect(jsonPath("$.accountId").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/accounts/enquiry — missing functionCode returns 400")
    void postEnquiry_missingFunctionCode_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("functionCode")));
    }

    @Test
    @DisplayName("POST /api/accounts/enquiry — accountId = 0 (WS-KEY = ZEROES) returns 400")
    void postEnquiry_zeroAccountId_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"functionCode":"INQA","accountId":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/accounts/enquiry — negative accountId returns 400")
    void postEnquiry_negativeAccountId_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"functionCode":"INQA","accountId":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/accounts/enquiry — malformed JSON returns 400")
    void postEnquiry_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/accounts/{id} ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/accounts/1 — found account returns 200")
    void getById_found_returns200() throws Exception {
        when(enquiryService.enquire(any())).thenReturn(SUCCESS_RESPONSE);

        mockMvc.perform(get("/api/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.activeStatus").value("Y"));
    }

    @Test
    @DisplayName("GET /api/accounts/{id} — non-numeric ID returns 400")
    void getById_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/accounts/abc"))
                .andExpect(status().isBadRequest());
    }
}
