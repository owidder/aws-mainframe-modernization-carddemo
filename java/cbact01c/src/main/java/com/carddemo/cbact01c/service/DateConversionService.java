package com.carddemo.cbact01c.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Java equivalent of the Assembler call {@code CALL 'COBDATFT' USING CODATECN-REC}.
 *
 * <p>In CBACT01C the call is made with (see CODATECN.cpy):
 * <ul>
 *   <li>CODATECN-TYPE = '2'    → input format YYYY-MM-DD (YYYY-MM-DD-IN value)</li>
 *   <li>CODATECN-OUTTYPE = '2' → output format YYYYMMDD  (YYYYMMDD-OP value)</li>
 * </ul>
 *
 * <p>COBOL: CBACT01C.cbl:223-233 — 1300-POPUL-ACCT-RECORD
 * <pre>
 * L223   MOVE   ACCT-REISSUE-DATE  TO  CODATECN-INP-DATE
 * L225   MOVE   '2'                TO  CODATECN-TYPE
 * L226   MOVE   '2'                TO  CODATECN-OUTTYPE
 * L231   CALL 'COBDATFT'       USING CODATECN-REC
 * L233   MOVE   CODATECN-0UT-DATE  TO  OUT-ACCT-REISSUE-DATE
 * </pre>
 */
@Service
public class DateConversionService {

    private static final Logger log = LoggerFactory.getLogger(DateConversionService.class);

    private static final DateTimeFormatter INPUT_FMT  = DateTimeFormatter.ISO_LOCAL_DATE; // YYYY-MM-DD
    private static final DateTimeFormatter OUTPUT_FMT = DateTimeFormatter.BASIC_ISO_DATE; // YYYYMMDD
    private static final String            BLANK_DATE = "        ";                        // 8 spaces (CODATECN-0UT-DATE PIC X(20) portion)

    /**
     * Converts a YYYY-MM-DD date string to YYYYMMDD format.
     *
     * <p>Maps CODATECN-TYPE='2' (YYYY-MM-DD input) + CODATECN-OUTTYPE='2' (YYYYMMDD output).
     *
     * @param date input date in YYYY-MM-DD format; null or blank returns 8 spaces
     * @return date in YYYYMMDD format, or 8 spaces if input is invalid
     */
    public String convertYyyyMmDdToYyyymmdd(String date) {
        if (date == null || date.isBlank()) {
            return BLANK_DATE;
        }
        try {
            return LocalDate.parse(date, INPUT_FMT).format(OUTPUT_FMT);
        } catch (DateTimeParseException e) {
            log.warn("COBDATFT equivalent: invalid date '{}', returning blank", date);
            return BLANK_DATE;
        }
    }
}
