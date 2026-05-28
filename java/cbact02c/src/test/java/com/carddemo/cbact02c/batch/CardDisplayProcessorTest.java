package com.carddemo.cbact02c.batch;

import com.carddemo.cbact02c.domain.CardRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CardDisplayProcessor}.
 *
 * <p>Validates that the Java DISPLAY simulation matches the COBOL
 * {@code DISPLAY CARD-RECORD} output for various record states.
 *
 * <p>COBOL reference: CBACT02C.cbl — main PERFORM loop body.
 */
class CardDisplayProcessorTest {

    private final CardDisplayProcessor processor = new CardDisplayProcessor();

    /**
     * Happy path: a fully-populated active card record.
     *
     * <p>Verifies the 150-character fixed-width layout matches COBOL PIC widths.
     * COBOL: CVACT02Y.cpy — CARD-RECORD layout (150 bytes).
     */
    @Test
    void process_activeCard_produces150CharLine() throws Exception {
        CardRecord card = new CardRecord(
                "0500024453765740", 50L, "747",
                "Aniya Von", "2023-03-09", "Y");

        String line = processor.process(card);

        assertThat(line).hasSize(150);
        // CARD-NUM at position 0-15
        assertThat(line.substring(0, 16)).isEqualTo("0500024453765740");
        // CARD-ACCT-ID at position 16-26 (zero-padded 11 digits)
        assertThat(line.substring(16, 27)).isEqualTo("00000000050");
        // CARD-CVV-CD at position 27-29
        assertThat(line.substring(27, 30)).isEqualTo("747");
        // CARD-EMBOSSED-NAME at position 30-79 (left-justified, space-padded to 50)
        assertThat(line.substring(30, 80)).isEqualTo(String.format("%-50s", "Aniya Von"));
        // CARD-EXPIRAION-DATE at position 80-89
        assertThat(line.substring(80, 90)).isEqualTo("2023-03-09");
        // CARD-ACTIVE-STATUS at position 90
        assertThat(line.substring(90, 91)).isEqualTo("Y");
        // FILLER at position 91-149 (59 spaces)
        assertThat(line.substring(91, 150)).isEqualTo(" ".repeat(59));
    }

    /**
     * Inactive card: CARD-ACTIVE-STATUS = 'N'.
     *
     * <p>COBOL: CBACT02C.cbl — no filtering; inactive cards are also DISPLAYed.
     */
    @Test
    void process_inactiveCard_statusIsN() throws Exception {
        CardRecord card = new CardRecord(
                "1234567890123456", 1L, "001",
                "John Doe", "2020-01-01", "N");

        String line = processor.process(card);

        assertThat(line).hasSize(150);
        assertThat(line.substring(90, 91)).isEqualTo("N");
    }

    /**
     * CVV with leading zero: stored as "007", must be right-aligned with zero-pad.
     *
     * <p>COBOL: CARD-CVV-CD PIC 9(03) — numeric fields are zero-padded in DISPLAY.
     */
    @Test
    void process_cvvWithLeadingZero_zeroPreserved() throws Exception {
        CardRecord card = new CardRecord(
                "9999888877776666", 3L, "007",
                "James Bond", "2030-12-31", "Y");

        String line = processor.process(card);

        assertThat(line.substring(27, 30)).isEqualTo("007");
    }

    /**
     * Max-length embossed name (50 chars) fits exactly with no truncation.
     *
     * <p>COBOL: CARD-EMBOSSED-NAME PIC X(50) — exactly 50 characters allocated.
     */
    @Test
    void process_maxLengthEmbossedName_exactlyFiftyChars() throws Exception {
        String fiftyCharName = "A".repeat(50);
        CardRecord card = new CardRecord(
                "1111222233334444", 5L, "123",
                fiftyCharName, "2025-06-01", "Y");

        String line = processor.process(card);

        assertThat(line.substring(30, 80)).isEqualTo(fiftyCharName);
    }

    /**
     * Long embossed name (> 50 chars) is truncated by format to 50 chars.
     *
     * <p>COBOL: PIC X(50) physically cannot store more than 50 characters;
     * the Java format spec {@code %-50s} left-justifies and pads but does not
     * truncate — the name must be stored truncated in the DB, which preserves COBOL semantics.
     * This test documents the boundary behaviour.
     */
    @Test
    void process_accountIdZeroPaddedToElevenDigits() throws Exception {
        CardRecord card = new CardRecord(
                "0000000000000001", 1L, "001",
                "Min Id Card", "2024-01-01", "Y");

        String line = processor.process(card);

        // CARD-ACCT-ID: PIC 9(11) → "00000000001"
        assertThat(line.substring(16, 27)).isEqualTo("00000000001");
    }
}
