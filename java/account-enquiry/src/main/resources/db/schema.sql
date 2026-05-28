-- Replaces VSAM ACCTDAT / copybook CVACT01Y.cpy
-- ACCOUNT-RECORD layout: 300 bytes, keyed on ACCT-ID PIC 9(11)

CREATE TABLE IF NOT EXISTS accounts (
    acct_id              BIGINT        NOT NULL PRIMARY KEY,   -- ACCT-ID           PIC 9(11)
    active_status        CHAR(1)       NOT NULL,               -- ACCT-ACTIVE-STATUS PIC X(01)
    curr_bal             DECIMAL(12,2) NOT NULL DEFAULT 0,     -- ACCT-CURR-BAL      PIC S9(10)V99
    credit_limit         DECIMAL(12,2) NOT NULL DEFAULT 0,     -- ACCT-CREDIT-LIMIT  PIC S9(10)V99
    cash_credit_limit    DECIMAL(12,2) NOT NULL DEFAULT 0,     -- ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99
    open_date            CHAR(10),                             -- ACCT-OPEN-DATE     PIC X(10)
    expiration_date      CHAR(10),                             -- ACCT-EXPIRAION-DATE PIC X(10) (typo from source)
    reissue_date         CHAR(10),                             -- ACCT-REISSUE-DATE  PIC X(10)
    curr_cyc_credit      DECIMAL(12,2) NOT NULL DEFAULT 0,     -- ACCT-CURR-CYC-CREDIT PIC S9(10)V99
    curr_cyc_debit       DECIMAL(12,2) NOT NULL DEFAULT 0,     -- ACCT-CURR-CYC-DEBIT  PIC S9(10)V99
    addr_zip             VARCHAR(10),                          -- ACCT-ADDR-ZIP      PIC X(10)
    group_id             VARCHAR(10)                           -- ACCT-GROUP-ID      PIC X(10)
    -- FILLER PIC X(178) not stored (no business content)
);
