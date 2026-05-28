-- Replaces VSAM CARDFILE KSDS (CARDFILE-FILE in CBACT02C)
-- Record layout follows copybook app/cpy/CVACT02Y.cpy (CARD-RECORD, 150 bytes)
-- Keyed on CARD-NUM PIC X(16) — matches SELECT CARDFILE-FILE ... RECORD KEY IS FD-CARD-NUM

CREATE TABLE IF NOT EXISTS card_records (
    card_num            CHAR(16)     NOT NULL PRIMARY KEY,  -- CARD-NUM           PIC X(16)
    card_acct_id        BIGINT       NOT NULL,              -- CARD-ACCT-ID       PIC 9(11)
    card_cvv_cd         CHAR(3)      NOT NULL,              -- CARD-CVV-CD        PIC 9(03) — stored as CHAR to preserve leading zeros
    card_embossed_name  VARCHAR(50)  NOT NULL,              -- CARD-EMBOSSED-NAME PIC X(50)
    expiration_date     CHAR(10),                           -- CARD-EXPIRAION-DATE PIC X(10) (typo preserved from CVACT02Y)
    active_status       CHAR(1)      NOT NULL               -- CARD-ACTIVE-STATUS PIC X(01)
    -- FILLER PIC X(59) — not stored (padding only, no business content)
);
