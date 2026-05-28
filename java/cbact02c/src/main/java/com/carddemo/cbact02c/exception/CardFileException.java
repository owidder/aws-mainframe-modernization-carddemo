package com.carddemo.cbact02c.exception;

/**
 * Thrown when an unrecoverable error occurs reading the card file.
 *
 * <p>Maps the COBOL 9999-ABEND-PROGRAM paragraph in CBACT02C.cbl:
 * <pre>
 * 9999-ABEND-PROGRAM.
 *     DISPLAY 'ABENDING PROGRAM'
 *     MOVE 999 TO ABCODE
 *     CALL 'CEE3ABD' USING ABCODE, TIMING.
 * </pre>
 *
 * <p>Spring Batch catches this unchecked exception, marks the step as FAILED,
 * and records the failure in the batch job repository — equivalent to a non-zero
 * return code on the COBOL GOBACK.
 */
public class CardFileException extends RuntimeException {

    public CardFileException(String message) {
        super(message);
    }

    public CardFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
