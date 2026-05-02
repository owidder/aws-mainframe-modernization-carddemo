# CardDemo – Business Modules and Functions

## Module Overview

The CardDemo application can be divided into 7 main business modules, each implementing separate business functions:

```
┌─────────────────────────────────────────┐
│    1. Authentication & Authorization    │
├─────────────────────────────────────────┤
│    2. Customer Management               │
├─────────────────────────────────────────┤
│    3. Account Management                │
├─────────────────────────────────────────┤
│    4. Card Management                   │
├─────────────────────────────────────────┤
│    5. Transaction Management & Posting  │
├─────────────────────────────────────────┤
│    6. Reporting & Analytics             │
├─────────────────────────────────────────┤
│    7. User & Admin Management           │
└─────────────────────────────────────────┘
```

## Module 1: Authentication & Authorization

**Business function:** User authentication and permission management.

**Programs:** COSGN00C, CBPAUP0C, COPAUA0C, COPAUS0C-2C, PAUDBLOD, PAUDBUNL, DBUNLDGS

**Data sources:** USRSEC, DB2, IMS AUTHDB

**Flow:** User login → COSGN00C → validation against USRSEC/DB2 → COMEN01C (on success) or error

---

## Module 2: Customer Management

**Business function:** Management of customer data, KYC data, addresses, and contact information.

**Programs:** COUSR00C (CU00), COUSR01C (CU01), COUSR02C (CU02), COUSR03C (CU03), CBCUS01C, CBEXPORT, CBIMPORT

**Data sources:** CUSTFILE (VSAM, 500 bytes/record), import/export files

**Online navigation:** COMEN01C → COUSR00C (List) → COUSR01C (Edit) → COUSR00C

**Batch jobs:** CBCUS01C (report), CBEXPORT (migration export), CBIMPORT (migration import)

**Master file:** CUSTFILE (500 bytes per customer, ~100,000 customers)

---

## Module 3: Account Management

**Business function:** Management of credit card accounts, limits, balances, and expiration dates.

**Programs:** COACTUPC (CA02), COACTVWC (CA03), CBACT04C (interest calculation), CBSTM03A/B (statements)

**Data sources:** ACCTFILE (VSAM, 300 bytes/record)

**Online navigation:** Menu → COACTUPC (Update) ↔ COACTVWC (View)

**Batch jobs:**
- CBACT04C: Interest calculation (daily/monthly)
- CBSTM03A: Statement generation with CBSTM03B

**Master file:** ACCTFILE (300 bytes per account, ~500,000 accounts)

**Key fields:** ACCT-ID, ACCT-CURR-BAL, ACCT-CREDIT-LIMIT, ACCT-EXPIRAION-DATE

---

## Module 4: Card Management

**Business function:** Management of credit cards, card numbers, card status, and licensing.

**Programs:** COCRDSLC (CC01), COCRDLIC (CC02), COCRDUPC (CC03)

**Data sources:** CARDFILE (150 bytes), XREFFILE (50 bytes – card number to account mapping)

**Online flow:**
1. COCRDSLC (CC01): Card list with paging (PF7/PF8)
2. Select card → COCRDLIC (CC02 – license) or COCRDUPC (CC03 – update)
3. Return to COCRDSLC

**Master files:**
- CARDFILE (150 bytes per card, ~500,000 cards)
- XREFFILE (50 bytes per mapping, key = CARD-NUM, FK to ACCT-ID + CUST-ID)

**Validation:** Each transaction reads XREFFILE, then validates ACCTFILE

---

## Module 5: Transaction Management & Posting

**Business function:** Capturing, validating, and posting daily credit card transactions.

**Programs:**
- Online: COTRN00C (CT00), COTRN01C (CT01), COTRN02C (CT02)
- Batch: CBTRN01C, CBTRN02C, CBTRN03C
- Utilities: CSUTLDTC (date functions), COBTUPDT, COTRTLIC, COTRTUPC (DB2 transaction type management)

**Data sources:** TRANFILE, DALYTRAN (input), DALYREJS (rejects), TCATBALF (category balance)

**Batch posting logic (CBTRN02C):**
1. Read DALYTRAN (daily input)
2. FOR EACH transaction:
   - Lookup XREFFILE (card-to-account)
   - Lookup ACCTFILE (account validation)
   - Validate: credit limit, expiration date
   - Write to TRANFILE (master)
   - Update ACCTFILE (balance)
   - Update TCATBALF (category balance)
   - On error: write to DALYREJS

**Master files:**
- TRANFILE (350 bytes, key = TRAN-ID)
- TCATBALF (45 bytes, composite key = ACCT-ID + TYPE-CD + CAT-CD)
- DALYTRAN (sequential input, 350 bytes)
- DALYREJS (sequential output rejects, GDG)

---

## Module 6: Reporting & Analytics

**Business function:** Generating reports on customers, accounts, transactions, and billing.

**Programs:**
- Online: CORPT00C (CR00 – report menu)
- Batch: CBACT01C, CBACT02C, CBACT03C, CBACT04C, CBSTM03A/B

**Report types:**
1. Customer report (CBCUS01C)
2. Card report (CBACT02C)
3. Account Xref report (CBACT03C)
4. Interest calculation (CBACT04C)
5. Account statements (CBSTM03A)

**Online report flow (CORPT00C):**
1. Select report type
2. Input parameters (date range, filter)
3. CALL CSUTLDTC for date functions
4. Generate report
5. Output to SYSOUT or file

**Statements (CBSTM03A):**
- FOR EACH ACCOUNT: CALL CBSTM03B (10× for various parts)
- Parts: header, summary, detailed transactions, fees, footer

---

## Module 7: User & Admin Management

**Business function:** Management of system user accounts, permissions, and admin functions.

**Programs:**
- Online: COADM01C (CA01), COUSR00C (CU00), COUSR01C (CU01), COUSR02C (CU02), COUSR03C (CU03)

**Admin menu (COADM01C):**
- Manage users
- System configuration
- Data maintenance
- Reports

**User management workflow:**
1. COUSR00C (List): Display all users, paging
2. Select user:
   - Create new → COUSR01C
   - Edit → COUSR01C
   - Delete → COUSR02C
   - Permissions → COUSR03C
3. Return to COUSR00C

**User permissions (COUSR03C):**
- Customer management (view/edit)
- Account management (view/edit)
- Card management (view/edit)
- Transaction management (view/edit/post)
- Reporting (view/export)
- User management (view/edit/delete)
- System admin
- Audit log

---

## Module Integration and Data Flow

```
Sign-On (COSGN00C)
    ↓
Main Menu (COMEN01C)
    ├─ Customer → COUSR00C → COUSR01/02/03C
    ├─ Account → COACTUPC ↔ COACTVWC
    ├─ Card → COCRDSLC → COCRDLIC/COCRDUPC
    ├─ Transaction → COTRN00C → COTRN01/02C
    ├─ Report → CORPT00C
    ├─ Billing → COBIL00C
    └─ Admin (Admin only) → COADM01C

Batch Processing (Nightly):
    POSTTRAN (JCL)
    ├─ CBTRN02C (Post Transactions)
    │   ├─ Read: DALYTRAN
    │   ├─ Lookup: XREFFILE, ACCTFILE
    │   ├─ Write: TRANFILE, DALYREJS
    │   └─ Update: ACCTFILE, TCATBALF
    └─ Report Generation:
        ├─ CBCUS01C (Customer Report)
        ├─ CBACT01/02/03C (Account/Card Reports)
        ├─ CBACT04C (Interest Calculation)
        └─ CBSTM03A (Statement Generation)
```

---

## Master Data Governance

| File | Type | Owner | Update | Read | Backup |
|---|---|---|---|---|---|
| CUSTFILE | VSAM | Admin | COUSR01/02C, CBIMPORT | Online/Batch | CBEXPORT |
| ACCTFILE | VSAM | Admin | COACTUPC, CBTRN02C | Online/Batch | ACCTFILE |
| CARDFILE | VSAM | Admin | COCRDUPC | Online/Batch | CARDFILE |
| XREFFILE | VSAM | Admin | System | CBTRN02C | XREFFILE |
| TRANFILE | VSAM | System | CBTRN02C | Online/Batch | TRANFILE |
| TCATBALF | VSAM | System | CBTRN02C | Online/Batch | TCATBALF |
| USRSEC | File | Admin | COUSR01/02C | COSGN00C | USRSEC |
| DB2 Tables | Database | DBA | COBTUPDT | COTRTUPC | DB2 Backup |
| IMS DB | Hierarchical | DBA | PAUDBLOD | COPAUA0C | PAUDBUNL |

---

*Analysis date: 2025-04-30*
*All 44 programs are organized into these 7 business modules*
