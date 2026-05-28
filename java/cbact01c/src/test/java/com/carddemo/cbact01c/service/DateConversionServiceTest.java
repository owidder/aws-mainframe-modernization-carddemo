package com.carddemo.cbact01c.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DateConversionService}.
 *
 * <p>Covers the Java equivalent of CALL 'COBDATFT' with
 * CODATECN-TYPE='2' (YYYY-MM-DD input) and CODATECN-OUTTYPE='2' (YYYYMMDD output).
 * COBOL: CBACT01C.cbl:223-233 — 1300-POPUL-ACCT-RECORD.
 */
class DateConversionServiceTest {

    private final DateConversionService service = new DateConversionService();

    @Test
    @DisplayName("Valid YYYY-MM-DD converts to YYYYMMDD (COBDATFT TYPE=2, OUTTYPE=2)")
    void convert_validDate_returnsYyyymmdd() {
        assertThat(service.convertYyyyMmDdToYyyymmdd("2025-05-20")).isEqualTo("20250520");
    }

    @Test
    @DisplayName("Another valid date converts correctly")
    void convert_anotherValidDate_returnsYyyymmdd() {
        assertThat(service.convertYyyyMmDdToYyyymmdd("2014-11-20")).isEqualTo("20141120");
    }

    @Test
    @DisplayName("Null input returns 8 spaces (CODATECN-0UT-DATE blank)")
    void convert_nullInput_returnsBlank() {
        assertThat(service.convertYyyyMmDdToYyyymmdd(null)).isEqualTo("        ");
    }

    @Test
    @DisplayName("Blank input returns 8 spaces")
    void convert_blankInput_returnsBlank() {
        assertThat(service.convertYyyyMmDdToYyyymmdd("   ")).isEqualTo("        ");
    }

    @Test
    @DisplayName("Empty string returns 8 spaces")
    void convert_emptyInput_returnsBlank() {
        assertThat(service.convertYyyyMmDdToYyyymmdd("")).isEqualTo("        ");
    }

    @Test
    @DisplayName("Invalid format string returns 8 spaces (COBDATFT error path)")
    void convert_invalidFormat_returnsBlank() {
        assertThat(service.convertYyyyMmDdToYyyymmdd("not-a-date")).isEqualTo("        ");
    }

    @Test
    @DisplayName("YYYYMMDD without dashes is not accepted (CODATECN-TYPE='2' requires YYYY-MM-DD)")
    void convert_yyyymmddWithoutDashes_returnsBlank() {
        // CODATECN-TYPE='2' expects YYYY-MM-DD, not YYYYMMDD
        assertThat(service.convertYyyyMmDdToYyyymmdd("20250520")).isEqualTo("        ");
    }

    @Test
    @DisplayName("Date at year boundary converts correctly")
    void convert_yearBoundaryDate_returnsYyyymmdd() {
        assertThat(service.convertYyyyMmDdToYyyymmdd("2024-12-31")).isEqualTo("20241231");
    }
}
