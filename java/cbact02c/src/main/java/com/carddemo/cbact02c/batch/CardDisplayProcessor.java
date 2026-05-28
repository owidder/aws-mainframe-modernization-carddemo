package com.carddemo.cbact02c.batch;

import com.carddemo.cbact02c.domain.CardRecord;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Formats a {@link CardRecord} as a fixed-width display string.
 *
 * <p>Mirrors the COBOL {@code DISPLAY CARD-RECORD} statement in CBACT02C.cbl.
 * In COBOL, {@code DISPLAY} writes the raw bytes of the record group to SYSOUT,
 * concatenating all sub-fields in declaration order.  This processor reproduces
 * that layout so the batch writer can log or write an equivalent line.
 *
 * <p>COBOL source reference: CBACT02C.cbl — main PERFORM loop:
 * <pre>
 * PERFORM UNTIL END-OF-FILE = 'Y'
 *     IF END-OF-FILE = 'N'
 *         PERFORM 1000-CARDFILE-GET-NEXT
 *         IF END-OF-FILE = 'N'
 *             DISPLAY CARD-RECORD        ← this processor
 *         END-IF
 *     END-IF
 * END-PERFORM.
 * </pre>
 *
 * <p>Record layout (CVACT02Y.cpy, total 150 bytes):
 * <pre>
 * Pos  0-15  : CARD-NUM            PIC X(16)
 * Pos 16-26  : CARD-ACCT-ID        PIC 9(11)
 * Pos 27-29  : CARD-CVV-CD         PIC 9(03)
 * Pos 30-79  : CARD-EMBOSSED-NAME  PIC X(50)
 * Pos 80-89  : CARD-EXPIRAION-DATE PIC X(10)
 * Pos 90     : CARD-ACTIVE-STATUS  PIC X(01)
 * Pos 91-149 : FILLER              PIC X(59)
 * </pre>
 */
@Component
public class CardDisplayProcessor implements ItemProcessor<CardRecord, String> {

    /**
     * Converts a {@link CardRecord} entity to its COBOL DISPLAY representation.
     *
     * <p>Numeric sub-fields (CARD-ACCT-ID, CARD-CVV-CD) are zero-padded to their
     * declared PIC widths, exactly as COBOL stores them in the record buffer.
     * Alphanumeric sub-fields are left-justified and space-padded to their declared widths.
     *
     * @param card the card record read from the database
     * @return the 150-character display line (matching COBOL DISPLAY CARD-RECORD output)
     */
    @Override
    public String process(CardRecord card) {
        // COBOL: DISPLAY CARD-RECORD — outputs all sub-fields concatenated
        return String.format("%-16s%011d%03d%-50s%-10s%-1s%-59s",
                nullToEmpty(card.getCardNum()),
                card.getCardAcctId(),
                parseCvv(card.getCardCvvCd()),
                nullToEmpty(card.getCardEmbossedName()),
                nullToEmpty(card.getExpirationDate()),
                nullToEmpty(card.getActiveStatus()),
                "");  // FILLER PIC X(59) — always spaces
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private int parseCvv(String cvv) {
        if (cvv == null || cvv.isBlank()) return 0;
        try {
            return Integer.parseInt(cvv.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
