# CardDemo – Detailed Business Logic of Core Programs

This document describes the **exact business logic** of the key COBOL programs based on direct source code analysis. It is the primary input for the Java mapping in `07-java-mapping.md`.

All line references point into the source files under `app/`.

---

## 1. CBTRN02C – Daily Transaction Posting (Batch)

**Type:** Batch | **Source:** [`app/cbl/CBTRN02C.cbl`](../app/cbl/CBTRN02C.cbl) | **Function:** Post daily transactions, update account balances and category totals

### Paragraph Index

| Paragraph | Line | Purpose |
|-----------|------|---------|
| `PROCEDURE DIVISION` | [L193](../app/cbl/CBTRN02C.cbl) | Main loop: open files, read-validate-post, close |
| `0000-DALYTRAN-OPEN` | [L236](../app/cbl/CBTRN02C.cbl) | Open DALYTRAN for input |
| `0100-TRANFILE-OPEN` | [L254](../app/cbl/CBTRN02C.cbl) | Open TRANSACT-FILE for output |
| `0200-XREFFILE-OPEN` | [L272](../app/cbl/CBTRN02C.cbl) | Open XREF-FILE for input |
| `0300-DALYREJS-OPEN` | [L291](../app/cbl/CBTRN02C.cbl) | Open DALYREJS-FILE for output |
| `0400-ACCTFILE-OPEN` | [L309](../app/cbl/CBTRN02C.cbl) | Open ACCOUNT-FILE for I-O |
| `0500-TCATBALF-OPEN` | [L327](../app/cbl/CBTRN02C.cbl) | Open TCATBAL-FILE for I-O |
| `1000-DALYTRAN-GET-NEXT` | [L345](../app/cbl/CBTRN02C.cbl) | Read next sequential record from DALYTRAN |
| `1500-VALIDATE-TRAN` | [L370](../app/cbl/CBTRN02C.cbl) | Orchestrate validation sub-paragraphs |
| `1500-A-LOOKUP-XREF` | [L380](../app/cbl/CBTRN02C.cbl) | Validate card number via XREFFILE |
| `1500-B-LOOKUP-ACCT` | [L393](../app/cbl/CBTRN02C.cbl) | Validate account: existence, credit limit, expiry |
| `2000-POST-TRANSACTION` | [L424](../app/cbl/CBTRN02C.cbl) | Map DALYTRAN fields to TRAN-RECORD, call sub-paragraphs |
| `2500-WRITE-REJECT-REC` | [L446](../app/cbl/CBTRN02C.cbl) | Write rejected record + error trailer to DALYREJS |
| `2700-UPDATE-TCATBAL` | [L467](../app/cbl/CBTRN02C.cbl) | Read TCATBAL record; route to create or update |
| `2700-A-CREATE-TCATBAL-REC` | [L503](../app/cbl/CBTRN02C.cbl) | Write new TCATBAL record (first transaction of category) |
| `2700-B-UPDATE-TCATBAL-REC` | [L526](../app/cbl/CBTRN02C.cbl) | Rewrite existing TCATBAL record |
| `2800-UPDATE-ACCOUNT-REC` | [L545](../app/cbl/CBTRN02C.cbl) | Update ACCT-CURR-BAL and cycle credit/debit |
| `2900-WRITE-TRANSACTION-FILE` | [L562](../app/cbl/CBTRN02C.cbl) | Write TRAN-RECORD to TRANFILE |
| `Z-GET-DB2-FORMAT-TIMESTAMP` | [L692](../app/cbl/CBTRN02C.cbl) | Format current date/time as DB2 timestamp string |
| `9999-ABEND-PROGRAM` | [L707](../app/cbl/CBTRN02C.cbl) | Call CEE3ABD to terminate with abend code 999 |

### File Access

| DDName     | File         | Mode   | Purpose                         |
|------------|--------------|--------|---------------------------------|
| DALYTRAN   | DALYTRAN     | INPUT  | Daily transaction input (sequential) |
| TRANFILE   | TRANSACT     | OUTPUT | Transaction master (VSAM KSDS)  |
| XREFFILE   | XREF-FILE    | INPUT  | Card-to-account cross-reference |
| DALYREJS   | DALYREJS     | OUTPUT | Rejected transactions (GDG)     |
| ACCTFILE   | ACCOUNT-FILE | I-O    | Account master (VSAM KSDS)      |
| TCATBALF   | TCATBAL-FILE | I-O    | Transaction category balances   |

### Main Loop — [`app/cbl/CBTRN02C.cbl:193`](../app/cbl/CBTRN02C.cbl)

```
L193  PROCEDURE DIVISION.
L195-199  PERFORM 0000-DALYTRAN-OPEN ... 0500-TCATBALF-OPEN   ← open all six files

L202  PERFORM UNTIL END-OF-FILE = 'Y':
L204    PERFORM 1000-DALYTRAN-GET-NEXT
L206      ADD 1 TO WS-TRANSACTION-COUNT
L208      MOVE 0 TO WS-VALIDATION-FAIL-REASON
L209      MOVE SPACES TO WS-VALIDATION-FAIL-REASON-DESC
L210      PERFORM 1500-VALIDATE-TRAN
L211      IF WS-VALIDATION-FAIL-REASON = 0:
L212        PERFORM 2000-POST-TRANSACTION
L213-215  ELSE:
L214        ADD 1 TO WS-REJECT-COUNT
L215        PERFORM 2500-WRITE-REJECT-REC

L221-226  PERFORM 9000-DALYTRAN-CLOSE ... 9500-TCATBALF-CLOSE  ← close all six files
L229      IF WS-REJECT-COUNT > 0: MOVE 4 TO RETURN-CODE         ← non-zero RC signals partial failure
```

### Validation — [`app/cbl/CBTRN02C.cbl:370`](../app/cbl/CBTRN02C.cbl)

#### `1500-VALIDATE-TRAN` — [L370](../app/cbl/CBTRN02C.cbl)

```
L371  PERFORM 1500-A-LOOKUP-XREF
L372  IF WS-VALIDATION-FAIL-REASON = 0:
L373      PERFORM 1500-B-LOOKUP-ACCT
      ELSE: CONTINUE                   ← short-circuits only XREF→ACCT chain
```

#### `1500-A-LOOKUP-XREF` — [L380](../app/cbl/CBTRN02C.cbl)

```
L382  MOVE DALYTRAN-CARD-NUM TO FD-XREF-CARD-NUM
L383  READ XREF-FILE INTO CARD-XREF-RECORD
L385-387    INVALID KEY:
              MOVE 100 TO WS-VALIDATION-FAIL-REASON
              MOVE 'INVALID CARD NUMBER FOUND' TO WS-VALIDATION-FAIL-REASON-DESC
```

#### `1500-B-LOOKUP-ACCT` — [L393](../app/cbl/CBTRN02C.cbl)

```
L394  MOVE XREF-ACCT-ID TO FD-ACCT-ID
L395  READ ACCOUNT-FILE INTO ACCOUNT-RECORD
L397-399    INVALID KEY:
              MOVE 101 TO WS-VALIDATION-FAIL-REASON
              MOVE 'ACCOUNT RECORD NOT FOUND'

L403  COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT
                           - ACCT-CURR-CYC-DEBIT
                           + DALYTRAN-AMT

L407  IF ACCT-CREDIT-LIMIT < WS-TEMP-BAL:             ← credit limit check
L410      MOVE 102 TO WS-VALIDATION-FAIL-REASON
L411      MOVE 'OVERLIMIT TRANSACTION'

L414  IF ACCT-EXPIRAION-DATE < DALYTRAN-ORIG-TS(1:10): ← string compare YYYY-MM-DD
L417      MOVE 103 TO WS-VALIDATION-FAIL-REASON
L418      MOVE 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION'
```

> **Note:** Both checks at L407 and L414 run unconditionally. The **last failing check wins** (overwrites the error code). Java implementations must replicate this behavior.

### Posting — [`app/cbl/CBTRN02C.cbl:424`](../app/cbl/CBTRN02C.cbl)

#### `2000-POST-TRANSACTION` — [L424](../app/cbl/CBTRN02C.cbl)

```
L425-435  MOVE DALYTRAN-* TO TRAN-*          ← field-by-field copy of 11 fields
L437      PERFORM Z-GET-DB2-FORMAT-TIMESTAMP  ← proc timestamp = current time
L438      MOVE DB2-FORMAT-TS TO TRAN-PROC-TS

L440      PERFORM 2700-UPDATE-TCATBAL         ← step 1: category balance
L441      PERFORM 2800-UPDATE-ACCOUNT-REC     ← step 2: account balance
L442      PERFORM 2900-WRITE-TRANSACTION-FILE ← step 3: write transaction record
```

> **Note:** Order matters for Java `@Transactional`: category → account → transaction.

#### `2700-UPDATE-TCATBAL` — [L467](../app/cbl/CBTRN02C.cbl)

```
L469  MOVE XREF-ACCT-ID     TO FD-TRANCAT-ACCT-ID
L470  MOVE DALYTRAN-TYPE-CD TO FD-TRANCAT-TYPE-CD
L471  MOVE DALYTRAN-CAT-CD  TO FD-TRANCAT-CD

L473  MOVE 'N' TO WS-CREATE-TRANCAT-REC
L474  READ TCATBAL-FILE INTO TRAN-CAT-BAL-RECORD
L475-478    INVALID KEY:
              DISPLAY 'TCATBAL record not found ... Creating.'
              MOVE 'Y' TO WS-CREATE-TRANCAT-REC     ← flag: new record needed

L495  IF WS-CREATE-TRANCAT-REC = 'Y':
L496      PERFORM 2700-A-CREATE-TCATBAL-REC         ← WRITE new record
      ELSE:
L498      PERFORM 2700-B-UPDATE-TCATBAL-REC         ← REWRITE existing record
```

#### `2700-A-CREATE-TCATBAL-REC` — [L503](../app/cbl/CBTRN02C.cbl)

```
L504  INITIALIZE TRAN-CAT-BAL-RECORD
L505-507  MOVE key fields (ACCT-ID, TYPE-CD, CAT-CD)
L508      ADD DALYTRAN-AMT TO TRAN-CAT-BAL           ← balance starts at transaction amount
L510      WRITE FD-TRAN-CAT-BAL-RECORD FROM TRAN-CAT-BAL-RECORD
```

#### `2700-B-UPDATE-TCATBAL-REC` — [L526](../app/cbl/CBTRN02C.cbl)

```
L527  ADD DALYTRAN-AMT TO TRAN-CAT-BAL
L528  REWRITE FD-TRAN-CAT-BAL-RECORD FROM TRAN-CAT-BAL-RECORD
```

#### `2800-UPDATE-ACCOUNT-REC` — [L545](../app/cbl/CBTRN02C.cbl)

```
L547  ADD DALYTRAN-AMT TO ACCT-CURR-BAL              ← always update total balance
L548  IF DALYTRAN-AMT >= 0:
L549      ADD DALYTRAN-AMT TO ACCT-CURR-CYC-CREDIT   ← positive = credit
      ELSE:
L551      ADD DALYTRAN-AMT TO ACCT-CURR-CYC-DEBIT    ← negative = debit
L554  REWRITE FD-ACCTFILE-REC FROM ACCOUNT-RECORD
```

### Reject Record — [`app/cbl/CBTRN02C.cbl:446`](../app/cbl/CBTRN02C.cbl)

```
L447  MOVE DALYTRAN-RECORD TO REJECT-TRAN-DATA  ← 350 bytes: original transaction
L448  MOVE WS-VALIDATION-TRAILER TO VALIDATION-TRAILER
          ← 80 bytes: WS-VALIDATION-FAIL-REASON (PIC 9(04))
                    + WS-VALIDATION-FAIL-REASON-DESC (PIC X(76))
L451  WRITE FD-REJS-RECORD FROM REJECT-RECORD   ← 430 bytes total to DALYREJS
```

### DB2 Timestamp Format — [`app/cbl/CBTRN02C.cbl:692`](../app/cbl/CBTRN02C.cbl)

```
L693  MOVE FUNCTION CURRENT-DATE TO COBOL-TS
L694-702  Rearrange fields into DB2 format: YYYY-MM-DD-HH.MM.SS.mm0000
          Separators: '-' between date parts, '.' between time parts
          Result in DB2-FORMAT-TS PIC X(26)
```

### Validation Error Codes

| Code | Description | Paragraph |
|------|-------------|-----------|
| 100 | INVALID CARD NUMBER FOUND | [L385](../app/cbl/CBTRN02C.cbl) |
| 101 | ACCOUNT RECORD NOT FOUND | [L397](../app/cbl/CBTRN02C.cbl) |
| 102 | OVERLIMIT TRANSACTION | [L410](../app/cbl/CBTRN02C.cbl) |
| 103 | TRANSACTION RECEIVED AFTER ACCT EXPIRATION | [L417](../app/cbl/CBTRN02C.cbl) |
| 109 | ACCOUNT RECORD NOT FOUND (on REWRITE) | [L556](../app/cbl/CBTRN02C.cbl) |

### Java Migration Notes
- **Both checks run (L407 + L414)**: No early exit; last error code wins. Replicate in Java.
- **findOrCreate for TCATBAL**: L474-L498 → `repository.findById(key).orElse(new TranCatBalance(key, ZERO))`
- **Write order**: TCATBAL → ACCOUNT → TRANFILE; wrap all three in a single `@Transactional`
- **Return code 4**: L229 → Java Step `ExitStatus.FAILED` or custom `ExitStatus("COMPLETED_WITH_REJECTS")`
- **Timestamp format** (L692–702): Java `DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'")`

---

## 2. COSGN00C – User Authentication (CICS Online)

**Type:** CICS Online | **Source:** [`app/cbl/COSGN00C.cbl`](../app/cbl/COSGN00C.cbl) | **Transaction:** `CC00`

### Paragraph Index

| Paragraph | Line | Purpose |
|-----------|------|---------|
| `MAIN-PARA` | [L73](../app/cbl/COSGN00C.cbl) | Entry point; branch on EIBCALEN and EIBAID |
| `PROCESS-ENTER-KEY` | [L108](../app/cbl/COSGN00C.cbl) | Receive map, validate fields, call READ-USER-SEC-FILE |
| `SEND-SIGNON-SCREEN` | [L145](../app/cbl/COSGN00C.cbl) | Populate header then send BMS map COSGN0A |
| `SEND-PLAIN-TEXT` | [L162](../app/cbl/COSGN00C.cbl) | Send plain text message (PF3 logout path) |
| `POPULATE-HEADER-INFO` | [L177](../app/cbl/COSGN00C.cbl) | Fill date, time, APPLID, SYSID into map output area |
| `READ-USER-SEC-FILE` | [L209](../app/cbl/COSGN00C.cbl) | CICS READ of USRSEC; compare password; XCTL to menu |

### CICS Program Lifecycle

```
L73   MAIN-PARA:
L75       SET ERR-FLG-OFF TO TRUE
L77-78    MOVE SPACES TO WS-MESSAGE, ERRMSGO

L80       IF EIBCALEN = 0:                    ← first invocation (no COMMAREA)
L81-83        MOVE LOW-VALUES TO COSGN0AO
              MOVE -1 TO USERIDL              ← position cursor on User ID field
              PERFORM SEND-SIGNON-SCREEN      ← display empty sign-on screen
          ELSE:
L85           EVALUATE EIBAID:               ← which key did the user press?
L86-87            WHEN DFHENTER → PERFORM PROCESS-ENTER-KEY
L88-90            WHEN DFHPF3  → MOVE CCDA-MSG-THANK-YOU; PERFORM SEND-PLAIN-TEXT
L92-94            WHEN OTHER   → set error flag; 'Invalid key'; re-send screen

L98-102   EXEC CICS RETURN TRANSID('CC00') COMMAREA(CARDDEMO-COMMAREA)
          ← always return with same transid so next keystroke re-enters this program
```

### Authentication Logic — [`app/cbl/COSGN00C.cbl:108`](../app/cbl/COSGN00C.cbl)

#### `PROCESS-ENTER-KEY` — [L108](../app/cbl/COSGN00C.cbl)

```
L110-115  EXEC CICS RECEIVE MAP('COSGN0A') MAPSET('COSGN00')   ← get screen input

L117-130  EVALUATE TRUE:
L118          WHEN USERIDI = SPACES OR LOW-VALUES:
                  'Please enter User ID ...'
                  MOVE -1 TO USERIDL; PERFORM SEND-SIGNON-SCREEN; STOP
L123          WHEN PASSWDI = SPACES OR LOW-VALUES:
                  'Please enter Password ...'
                  MOVE -1 TO PASSWDL; PERFORM SEND-SIGNON-SCREEN; STOP

L132-135  MOVE FUNCTION UPPER-CASE(USERIDI) TO WS-USER-ID, CDEMO-USER-ID
L135-136  MOVE FUNCTION UPPER-CASE(PASSWDI) TO WS-USER-PWD   ← both forced to uppercase

L138-140  IF NOT ERR-FLG-ON: PERFORM READ-USER-SEC-FILE
```

#### `READ-USER-SEC-FILE` — [L209](../app/cbl/COSGN00C.cbl)

```
L211-218  EXEC CICS READ
              DATASET('USRSEC')
              INTO(SEC-USER-DATA)
              RIDFLD(WS-USER-ID)              ← primary key = User ID (8 chars)
              RESP(WS-RESP-CD)

L221      EVALUATE WS-RESP-CD:
L222          WHEN 0 (record found):
L223              IF SEC-USR-PWD = WS-USER-PWD:    ← PLAINTEXT password compare!
L224-228              populate COMMAREA (FROM-TRANID, FROM-PROGRAM, USER-ID, USER-TYPE)
L229                  MOVE ZEROS TO CDEMO-PGM-CONTEXT    ← 0 = first entry to next program
L230-233              IF CDEMO-USRTYP-ADMIN (SEC-USR-TYPE = 'A'):
L231                      EXEC CICS XCTL PROGRAM('COADM01C') COMMAREA(...)
L234-236              ELSE:
L235                      EXEC CICS XCTL PROGRAM('COMEN01C') COMMAREA(...)
L241-244          ELSE (wrong password):
                      'Wrong Password. Try again ...'
L247          WHEN 13 (record not found):
                  'User not found. Try again ...'
L253          WHEN OTHER:
                  'Unable to verify the User ...'
```

### COMMAREA Fields Passed to Next Program

| Field | Value | Source |
|-------|-------|--------|
| `CDEMO-FROM-TRANID` | `'CC00'` | Hard-coded in WS-TRANID |
| `CDEMO-FROM-PROGRAM` | `'COSGN00C'` | Hard-coded in WS-PGMNAME |
| `CDEMO-TO-TRANID` | (not set) | — |
| `CDEMO-USER-ID` | User ID (uppercase) | L134 |
| `CDEMO-USER-TYPE` | `'A'` or `'U'` | SEC-USR-TYPE from USRSEC |
| `CDEMO-PGM-CONTEXT` | `0` | L229 — signals "first entry" to target program |

### Java Migration Notes
- **Plaintext password** (L223): `SEC-USR-PWD = WS-USER-PWD` — migrate to BCrypt in Spring Security
- **USRSEC key**: 8-char User ID, uppercase only (L132) → `userId.toUpperCase()` before lookup
- **User type routing** (L230–235): `UserType.ADMIN` → admin endpoint; `UserType.USER` → standard menu
- **COMMAREA** → JWT claims: `sub=userId`, `role=ADMIN|USER`, `fromTranid=CC00`
- **EIBCALEN = 0**: no Java equivalent; REST is stateless — first request is always "fresh"

---

## 3. CBACT04C – Interest Calculation (Batch)

**Type:** Batch | **Source:** [`app/cbl/CBACT04C.cbl`](../app/cbl/CBACT04C.cbl) | **Function:** Compute monthly interest per account/category, write interest transactions, reset cycle balances

### Paragraph Index

| Paragraph | Line | Purpose |
|-----------|------|---------|
| `PROCEDURE DIVISION` | [L180](../app/cbl/CBACT04C.cbl) | Entry; receives PARM (date string) |
| `0000-TCATBALF-OPEN` | [L234](../app/cbl/CBACT04C.cbl) | Open TCATBALF for sequential input |
| `0100-XREFFILE-OPEN` | [L251](../app/cbl/CBACT04C.cbl) | Open XREFFILE for random input |
| `0200-DISCGRP-OPEN` | [L269](../app/cbl/CBACT04C.cbl) | Open DISCGRP (interest rates) for random input |
| `0300-ACCTFILE-OPEN` | [L288](../app/cbl/CBACT04C.cbl) | Open ACCTFILE for I-O |
| `0400-TRANFILE-OPEN` | [L306](../app/cbl/CBACT04C.cbl) | Open TRANSACT for sequential output |
| `1000-TCATBALF-GET-NEXT` | [L325](../app/cbl/CBACT04C.cbl) | Read next sequential TCATBAL record |
| `1050-UPDATE-ACCOUNT` | [L350](../app/cbl/CBACT04C.cbl) | Apply total interest, zero cycle balances, rewrite account |
| `1100-GET-ACCT-DATA` | [L372](../app/cbl/CBACT04C.cbl) | Read account by ACCT-ID (for GROUP-ID) |
| `1110-GET-XREF-DATA` | [L393](../app/cbl/CBACT04C.cbl) | Read XREF by ACCT-ID (alternate key) for CARD-NUM |
| `1200-GET-INTEREST-RATE` | [L415](../app/cbl/CBACT04C.cbl) | Lookup DISCGRP; fallback to 'DEFAULT' group |
| `1200-A-GET-DEFAULT-INT-RATE` | [L443](../app/cbl/CBACT04C.cbl) | Re-read DISCGRP with group = 'DEFAULT' |
| `1300-COMPUTE-INTEREST` | [L462](../app/cbl/CBACT04C.cbl) | Apply formula; accumulate; call WRITE-TX |
| `1300-B-WRITE-TX` | [L473](../app/cbl/CBACT04C.cbl) | Build and write interest transaction record |
| `1400-COMPUTE-FEES` | [L518](../app/cbl/CBACT04C.cbl) | **STUB — not implemented** (just EXIT) |

### File Access

| DDName   | File         | Mode       | Purpose                                    |
|----------|--------------|------------|--------------------------------------------|
| TCATBALF | TCATBAL-FILE | INPUT/SEQ  | All category balances, sorted by ACCT-ID   |
| XREFFILE | XREF-FILE    | INPUT/RAND | Alternate key: ACCT-ID → CARD-NUM          |
| DISCGRP  | DISCGRP-FILE | INPUT/RAND | Interest rates per group + type + category |
| ACCTFILE | ACCOUNT-FILE | I-O/RAND   | Read ACCT-GROUP-ID; rewrite updated balance |
| TRANSACT | TRANSACT-FILE| OUTPUT/SEQ | Interest transactions written sequentially |

### Program Parameter

```
L176-178  LINKAGE SECTION.
          01 EXTERNAL-PARMS.
             05 PARM-LENGTH  PIC S9(04) COMP.
             05 PARM-DATE    PIC X(10).          ← JCL PARM date, used as TRAN-ID prefix
L180  PROCEDURE DIVISION USING EXTERNAL-PARMS.
```

### Main Loop — [`app/cbl/CBACT04C.cbl:180`](../app/cbl/CBACT04C.cbl)

```
L188  PERFORM UNTIL END-OF-FILE = 'Y':
L190      PERFORM 1000-TCATBALF-GET-NEXT        ← sequential scan of TCATBALF

L194      IF TRANCAT-ACCT-ID NOT= WS-LAST-ACCT-NUM:  ← new account detected
L195-199      IF WS-FIRST-TIME NOT = 'Y':
                  PERFORM 1050-UPDATE-ACCOUNT   ← close out previous account
              ELSE:
                  MOVE 'N' TO WS-FIRST-TIME
L200          MOVE 0 TO WS-TOTAL-INT            ← reset interest accumulator
L201          MOVE TRANCAT-ACCT-ID TO WS-LAST-ACCT-NUM
L202-203      MOVE TRANCAT-ACCT-ID TO FD-ACCT-ID
              PERFORM 1100-GET-ACCT-DATA        ← fetch ACCT-GROUP-ID
L204-205      MOVE TRANCAT-ACCT-ID TO FD-XREF-ACCT-ID
              PERFORM 1110-GET-XREF-DATA        ← fetch CARD-NUM via alternate key

L210      PERFORM 1200-GET-INTEREST-RATE        ← lookup rate for this type+category
L214      IF DIS-INT-RATE NOT = 0:
L215          PERFORM 1300-COMPUTE-INTEREST
L216          PERFORM 1400-COMPUTE-FEES         ← STUB: does nothing

      ← after loop exits (EOF):
L220-221  PERFORM 1050-UPDATE-ACCOUNT           ← close out last account
```

### Interest Rate Lookup — [`app/cbl/CBACT04C.cbl:415`](../app/cbl/CBACT04C.cbl)

```
L415  1200-GET-INTEREST-RATE:
      Key = FD-DIS-ACCT-GROUP-ID (from ACCT-GROUP-ID)
          + FD-DIS-TRAN-TYPE-CD  (from TRANCAT-TYPE-CD)
          + FD-DIS-TRAN-CAT-CD   (from TRANCAT-CD)
L416  READ DISCGRP-FILE INTO DIS-GROUP-RECORD (INVALID KEY: log message)

L422  IF DISCGRP-STATUS = '00': use DIS-INT-RATE
L436  IF DISCGRP-STATUS = '23' (not found):
L437      MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID    ← fallback group
L438      PERFORM 1200-A-GET-DEFAULT-INT-RATE        ← retry read with 'DEFAULT'
```

### Interest Calculation — [`app/cbl/CBACT04C.cbl:462`](../app/cbl/CBACT04C.cbl)

```
L462  1300-COMPUTE-INTEREST:
L464-465  COMPUTE WS-MONTHLY-INT
           = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
          ← DIS-INT-RATE is annual rate × 100 (e.g. 18.5% stored as 1850)
          ← dividing by 1200 converts to monthly fraction

L467  ADD WS-MONTHLY-INT TO WS-TOTAL-INT      ← accumulate across all categories for account
L468  PERFORM 1300-B-WRITE-TX                 ← write one interest transaction per category
```

### Interest Transaction Record — [`app/cbl/CBACT04C.cbl:473`](../app/cbl/CBACT04C.cbl)

```
L474      ADD 1 TO WS-TRANID-SUFFIX
L476-480  STRING PARM-DATE, WS-TRANID-SUFFIX INTO TRAN-ID   ← e.g. '2026-05-02000001'
L482      MOVE '01' TO TRAN-TYPE-CD            ← Interest transaction type
L483      MOVE '05' TO TRAN-CAT-CD             ← Interest category code (note: moves to PIC 9(04))
L484      MOVE 'System' TO TRAN-SOURCE
L485-488  STRING 'Int. for a/c ', ACCT-ID INTO TRAN-DESC
L490      MOVE WS-MONTHLY-INT TO TRAN-AMT
L491-493  MOVE 0/SPACES to TRAN-MERCHANT-ID/NAME/CITY/ZIP   ← system transaction, no merchant
L495      MOVE XREF-CARD-NUM TO TRAN-CARD-NUM
L496-498  PERFORM Z-GET-DB2-FORMAT-TIMESTAMP; set both TRAN-ORIG-TS and TRAN-PROC-TS
L500      WRITE FD-TRANFILE-REC FROM TRAN-RECORD
```

### Account Cycle Close — [`app/cbl/CBACT04C.cbl:350`](../app/cbl/CBACT04C.cbl)

```
L352  ADD WS-TOTAL-INT TO ACCT-CURR-BAL           ← apply total accumulated interest
L353  MOVE 0 TO ACCT-CURR-CYC-CREDIT              ← reset billing cycle credit to zero
L354  MOVE 0 TO ACCT-CURR-CYC-DEBIT               ← reset billing cycle debit to zero
L356  REWRITE FD-ACCTFILE-REC FROM ACCOUNT-RECORD
```

### Fee Calculation — [`app/cbl/CBACT04C.cbl:518`](../app/cbl/CBACT04C.cbl)

```
L518  1400-COMPUTE-FEES.     ← STUB: body is just EXIT
L519      EXIT.
```

> **Do not implement fees** in Java — no business logic exists here. Use a placeholder comment only.

### Java Migration Notes
- **Interest formula** (L464): `balance.multiply(rate).divide(BigDecimal.valueOf(1200), 2, HALF_UP)`
- **Alternate key** (L204–205): `xrefRepository.findByAcctId(acctId)` — JPA `@NaturalId` or `findBy` query
- **Sequential scan grouped by account**: replace with `SELECT ... ORDER BY acct_id` + Java grouping
- **DISCGRP 'DEFAULT' fallback** (L436–438): two-step lookup; implement as `findByKeyOrDefault(key, "DEFAULT")`
- **1400-COMPUTE-FEES is a stub**: implement as empty method with `// TODO: fee calculation not in source`
- **WS-TOTAL-INT accumulator** (L200, L467): accumulate per account across all category rows before writing

---

## 4. CBSTM03A – Account Statement Generation (Batch)

**Type:** Batch | **Source:** [`app/cbl/CBSTM03A.cbl`](../app/cbl/CBSTM03A.cbl) | **Function:** Generate account statements in plain text (80 chars) and HTML format

### Paragraph Index

| Paragraph | Line | Purpose |
|-----------|------|---------|
| (main loop) | [L1–end](../app/cbl/CBSTM03A.cbl) | Iterate accounts; call CBSTM03B for file I/O |

### Notable COBOL Constructs (Modernization-Relevant)

| Construct | Approx. Line | Java Equivalent |
|-----------|------|-----------------|
| `ALTER ... GOTO` | varies | Replace with Strategy or State pattern |
| `COMP` variables (binary) | [L59-63](../app/cbl/CBSTM03A.cbl) | `int` / `short` |
| `COMP-3` variables (packed decimal) | [L64-65](../app/cbl/CBSTM03A.cbl) | `BigDecimal` |
| 2D array in WORKING-STORAGE | varies | `List<List<T>>` or `Map<Key, List<T>>` |
| `CALL 'CBSTM03B'` subroutine | varies | Inject `StatementFileService` as Spring bean |

### Subroutine Interface to CBSTM03B — [`app/cbl/CBSTM03A.cbl:71`](../app/cbl/CBSTM03A.cbl)

```
L71-83  WS-M03B-AREA (passed by reference to CBSTM03B):
  L72  WS-M03B-DD      PIC X(08)    ← DDName of the file to operate on
  L73  WS-M03B-OPER    PIC X(01)    ← Operation:
                                        'O'=Open, 'C'=Close, 'R'=Read-sequential,
                                        'K'=Read-by-key, 'W'=Write, 'Z'=Rewrite
  L80  WS-M03B-RC      PIC X(02)    ← Return code from CBSTM03B
  L81  WS-M03B-KEY     PIC X(25)    ← Key for 'K' reads
  L82  WS-M03B-KEY-LN  PIC S9(4)    ← Key length
  L83  WS-M03B-FLDT    PIC X(1000)  ← Record data buffer (read/write payload)
```

### Plain Text Output Format — [`app/cbl/CBSTM03A.cbl:85`](../app/cbl/CBSTM03A.cbl)

```
L87-88    ST-LINE0:  *** START OF STATEMENT *** (80 chars)
L91-92    ST-LINE1:  {Customer full name, 75 chars}
L94-95    ST-LINE2:  {Address line 1, 50 chars}
L97-98    ST-LINE3:  {Address line 2, 50 chars}
L100      ST-LINE4:  {City, State ZIP, 80 chars}
L102      ST-LINE5:  dashes (80 chars)
L104-106  ST-LINE6:  'Basic Details' centered
L107-110  ST-LINE7:  'Account ID         : ' + ACCT-ID
L111-115  ST-LINE8:  'Current Balance    : ' + ACCT-CURR-BAL (PIC 9(9).99-)
L116-119  ST-LINE9:  'FICO Score         : ' + CUST-FICO-CREDIT-SCORE
L120      ST-LINE10: dashes (80 chars)
L121-124  ST-LINE11: 'TRANSACTION SUMMARY' centered
L129-131  ST-LINE13: column headers (Tran ID, Tran Details, Tran Amount)
L133-137  ST-LINE14: {TRAN-ID} {TRAN-DESC} ${TRAN-AMT}  (per transaction)
L139-142  ST-LINE14A:'Total EXP:' + ${total}
L144-146  ST-LINE15: *** END OF STATEMENT *** (80 chars)
```

---

## 5. CBTRN01C – Transaction Validation (Batch)

**Type:** Batch | **Source:** [`app/cbl/CBTRN01C.cbl`](../app/cbl/CBTRN01C.cbl) | **Function:** Read DALYTRAN and validate against CUSTFILE, XREFFILE, CARDFILE, ACCTFILE

### Paragraph Index

| Paragraph | Line | Purpose |
|-----------|------|---------|
| `PROCEDURE DIVISION` | [L~130](../app/cbl/CBTRN01C.cbl) | Main sequential loop over DALYTRAN |

### File Access

| DDName   | File          | Mode   | Purpose |
|----------|---------------|--------|---------|
| DALYTRAN | DALYTRAN-FILE | INPUT  | Daily transaction input (sequential) |
| CUSTFILE | CUSTOMER-FILE | INPUT  | Customer data validation |
| XREFFILE | XREF-FILE     | INPUT  | Card-to-account mapping |
| CARDFILE | CARD-FILE     | INPUT  | Card record validation |
| ACCTFILE | ACCOUNT-FILE  | I-O    | Account validation |
| TRANFILE | TRANSACT-FILE | I-O    | Transaction master |

> **Difference from CBTRN02C**: CBTRN01C additionally reads CUSTFILE (L34–38) and CARDFILE (L43–50), providing deeper validation including customer existence and card record checks.

---

## 6. User Security Record (USRSEC / CSUSR01Y)

**Source:** [`app/cpy/CSUSR01Y.cpy`](../app/cpy/CSUSR01Y.cpy) | Referenced by COSGN00C at [L55](../app/cbl/COSGN00C.cbl)

```cobol
01 SEC-USER-DATA.
   05 SEC-USR-ID         PIC X(08).  ← primary key (= WS-USER-ID on login)
   05 SEC-USR-FNAME      PIC X(20).
   05 SEC-USR-LNAME      PIC X(20).
   05 SEC-USR-PWD        PIC X(08).  ← plaintext password — replace with BCrypt hash
   05 SEC-USR-TYPE       PIC X(01).  ← 'A' = Admin, 'U' = User
   05 SEC-USR-ACTIVE-FLG PIC X(01).
```

---

## Summary: Business-Critical Migration Notes

| Topic | COBOL Behavior | Java Recommendation |
|-------|---------------|---------------------|
| Dual validation (L407+L414) | Both checks always run; last code wins | Run both checks; collect all errors |
| Plaintext passwords (L223) | Direct string compare to USRSEC field | BCrypt + Spring Security |
| Interest formula (L464-465) | `(BAL * RATE) / 1200` | `BigDecimal.divide(..., HALF_UP)` |
| TCATBAL create-or-update (L474-498) | READ → INVALID KEY → WRITE else REWRITE | `findById().orElse(new Entity())` |
| Fee calculation (L518-519) | STUB — EXIT only | Empty method + `// TODO` comment |
| DB2 timestamp (L692-702) | `YYYY-MM-DD-HH.MM.SS.mm0000` | `DateTimeFormatter` with literal dashes/dots |
| XREF alternate key (L204-205) | `READ XREF-FILE KEY IS FD-XREF-ACCT-ID` | `xrefRepo.findByAcctId(acctId)` |
| CICS routing (L230-235) | XCTL to COADM01C or COMEN01C by type | Spring Security role-based redirect |
| Cycle reset (L353-354) | Zero CURR-CYC-CREDIT and CURR-CYC-DEBIT | Set fields to `BigDecimal.ZERO` |

---

*Analysis date: 2026-05-02*
*Based on direct source read of: CBTRN02C.cbl, COSGN00C.cbl, CBACT04C.cbl, CBSTM03A.cbl, CBTRN01C.cbl*
