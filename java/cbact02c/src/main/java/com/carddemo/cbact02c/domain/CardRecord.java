package com.carddemo.cbact02c.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing one card data record.
 *
 * <p>Maps the COBOL copybook {@code CVACT02Y.cpy} (CARD-RECORD, 150 bytes):
 * <pre>
 * 01  CARD-RECORD.
 *     05  CARD-NUM               PIC X(16)    → card_num       CHAR(16)   PK
 *     05  CARD-ACCT-ID           PIC 9(11)    → card_acct_id   BIGINT
 *     05  CARD-CVV-CD            PIC 9(03)    → card_cvv_cd    CHAR(3)    (sensitive)
 *     05  CARD-EMBOSSED-NAME     PIC X(50)    → card_embossed_name VARCHAR(50)
 *     05  CARD-EXPIRAION-DATE    PIC X(10)    → expiration_date CHAR(10)  (typo from source)
 *     05  CARD-ACTIVE-STATUS     PIC X(01)    → active_status  CHAR(1)
 *     05  FILLER                 PIC X(59)    → not stored
 * </pre>
 *
 * <p>COBOL FD: CBACT02C.cbl:37-40 — FD CARDFILE-FILE / 01 FD-CARDFILE-REC.
 * COBOL SELECT: CBACT02C.cbl:28-33 — ORGANIZATION IS INDEXED, RECORD KEY IS FD-CARD-NUM.
 */
@Entity
@Table(name = "card_records")
public class CardRecord {

    /** COBOL: CARD-NUM PIC X(16) — primary KSDS record key. */
    @Id
    @Column(name = "card_num", length = 16, nullable = false)
    private String cardNum;

    /** COBOL: CARD-ACCT-ID PIC 9(11) — associated account identifier. */
    @Column(name = "card_acct_id", nullable = false)
    private Long cardAcctId;

    /**
     * COBOL: CARD-CVV-CD PIC 9(03) — card verification value.
     * Stored as CHAR(3) to preserve leading zeros (e.g. "007").
     * NOTE: in a production system this field should not be persisted.
     */
    @Column(name = "card_cvv_cd", length = 3, nullable = false)
    private String cardCvvCd;

    /** COBOL: CARD-EMBOSSED-NAME PIC X(50) — name printed on the card face. */
    @Column(name = "card_embossed_name", length = 50, nullable = false)
    private String cardEmbossedName;

    /**
     * COBOL: CARD-EXPIRAION-DATE PIC X(10) — card expiry date.
     * Field name preserves the original COBOL typo ("EXPIRAION") from CVACT02Y.
     */
    @Column(name = "expiration_date", length = 10)
    private String expirationDate;

    /** COBOL: CARD-ACTIVE-STATUS PIC X(01) — 'Y' = active, 'N' = inactive. */
    @Column(name = "active_status", length = 1, nullable = false)
    private String activeStatus;

    // ── Constructors ──────────────────────────────────────────────────────

    protected CardRecord() {}

    public CardRecord(String cardNum, Long cardAcctId, String cardCvvCd,
                      String cardEmbossedName, String expirationDate, String activeStatus) {
        this.cardNum = cardNum;
        this.cardAcctId = cardAcctId;
        this.cardCvvCd = cardCvvCd;
        this.cardEmbossedName = cardEmbossedName;
        this.expirationDate = expirationDate;
        this.activeStatus = activeStatus;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public String getCardNum()          { return cardNum; }
    public Long   getCardAcctId()       { return cardAcctId; }
    public String getCardCvvCd()        { return cardCvvCd; }
    public String getCardEmbossedName() { return cardEmbossedName; }
    public String getExpirationDate()   { return expirationDate; }
    public String getActiveStatus()     { return activeStatus; }
}
