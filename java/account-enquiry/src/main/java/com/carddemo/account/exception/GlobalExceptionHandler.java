package com.carddemo.account.exception;

import com.carddemo.account.web.dto.AccountEnquiryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Maps validation and unexpected errors to HTTP responses.
 *
 * <p>COBOL equivalent: PERFORM 9000-ERROR + PERFORM 8000-TERMINATION
 * (COACCT01.cbl — terminates program and puts error on CARD.DEMO.ERROR queue).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean Validation failures (e.g. missing functionCode, accountId ≤ 0).
     * Returns HTTP 400 with a descriptive error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AccountEnquiryResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("Validation error: {}", msg);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(AccountEnquiryResponse.error("INVALID REQUEST: " + msg));
    }

    /** Malformed request body (unparseable JSON). Returns HTTP 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AccountEnquiryResponse> handleNotReadable(
            HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(AccountEnquiryResponse.error("INVALID REQUEST: " + ex.getMessage()));
    }

    /** Path-variable type mismatch (e.g. non-numeric account ID). Returns HTTP 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AccountEnquiryResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String msg = ex.getName() + ": invalid value '" + ex.getValue() + "'";
        log.warn("Type mismatch: {}", msg);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(AccountEnquiryResponse.error("INVALID REQUEST: " + msg));
    }

    /**
     * Unexpected errors — replaces PERFORM 9000-ERROR + 8000-TERMINATION.
     * Returns HTTP 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AccountEnquiryResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error during account enquiry", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AccountEnquiryResponse.error(
                        "INTERNAL ERROR: " + ex.getMessage()));
    }
}
