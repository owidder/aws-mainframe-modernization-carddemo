# CardDemo – Data Structures and Files

## Overview of Key Files and Structures

CardDemo uses the following data categories:
- **VSAM KSDS** – Indexed Sequential Datasets for master data
- **DB2 Tables** – Relational data for transaction types and authorization
- **IMS Databases** – Hierarchical databases for authentication
- **Copybooks** – COBOL data structure definitions

## VSAM Files (Virtual Storage Access Method)

VSAM files are Indexed Sequential Datasets (KSDS) and serve as the primary persistent storage for all master data.

### VSAM KSDS Files

| File DDName | Dataset Name (DSN) | Record Length | Key | Copybook | Purpose |
|---|---|---|---|---|---|
| CUSTFILE | AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS | 500 bytes | CUST-ID (9 digits) | CVCUS01Y | Customer management |
| ACCTFILE | AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS | 300 bytes | ACCT-ID (11 digits) | CVACT01Y | Account master |
| CARDFILE | AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS | 150 bytes | CARD-NUM (16 chars) | CVCRD01Y | Card data |
| XREFFILE | AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS | 50 bytes | XREF-CARD-NUM (16) | CVACT03Y | Card-to-account cross-reference |
| TRANFILE | AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS | 350 bytes | TRAN-ID (16 chars) | CVTRA05Y | Transaction history |
| TCATBALF | AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS | 45 bytes | Composite key | CVTRA01Y | Transaction category balance |

### VSAM File Structure – Details

#### CUSTFILE – Customer Data (CVCUS01Y)

**Key:** CUST-ID (9 digits)
**Record Length:** 500 bytes
**LRECL:** 500

**Structure:**
```cobol
01  CUSTOMER-RECORD.
    05  CUST-ID                     PIC 9(09).  [Key]
    05  CUST-FIRST-NAME             PIC X(25).
    05  CUST-MIDDLE-NAME            PIC X(25).
    05  CUST-LAST-NAME              PIC X(25).
    05  CUST-ADDR-LINE-1            PIC X(50).
    05  CUST-ADDR-LINE-2            PIC X(50).
    05  CUST-ADDR-LINE-3            PIC X(50).
    05  CUST-ADDR-STATE-CD          PIC X(02).
    05  CUST-ADDR-COUNTRY-CD        PIC X(03).
    05  CUST-ADDR-ZIP               PIC X(10).
    05  CUST-PHONE-NUM-1            PIC X(15).
    05  CUST-PHONE-NUM-2            PIC X(15).
    05  CUST-SSN                    PIC 9(09).
    05  CUST-GOVT-ISSUED-ID         PIC X(20).
    05  CUST-DOB-YYYY-MM-DD         PIC X(10).
    05  CUST-EFT-ACCOUNT-ID         PIC X(10).
    05  CUST-PRI-CARD-HOLDER-IND    PIC X(01).
    05  CUST-FICO-CREDIT-SCORE      PIC 9(03).
    05  FILLER                      PIC X(168).
```

#### ACCTFILE – Account Data (CVACT01Y)

**Key:** ACCT-ID (11 digits)
**Record Length:** 300 bytes

**Structure:**
```cobol
01  ACCOUNT-RECORD.
    05  ACCT-ID                     PIC 9(11).  [Key]
    05  ACCT-ACTIVE-STATUS          PIC X(01).
    05  ACCT-CURR-BAL               PIC S9(10)V99.
    05  ACCT-CREDIT-LIMIT           PIC S9(10)V99.
    05  ACCT-CASH-CREDIT-LIMIT      PIC S9(10)V99.
    05  ACCT-OPEN-DATE              PIC X(10).
    05  ACCT-EXPIRAION-DATE         PIC X(10).
    05  ACCT-REISSUE-DATE           PIC X(10).
    05  ACCT-CURR-CYC-CREDIT        PIC S9(10)V99.
    05  ACCT-CURR-CYC-DEBIT         PIC S9(10)V99.
    05  ACCT-ADDR-ZIP               PIC X(10).
    05  ACCT-GROUP-ID               PIC X(10).
    05  FILLER                      PIC X(178).
```

#### XREFFILE – Card-to-Account Cross-Reference (CVACT03Y)

**Key:** XREF-CARD-NUM (16 characters)
**Record Length:** 50 bytes

**Structure:**
```cobol
01 CARD-XREF-RECORD.
    05  XREF-CARD-NUM              PIC X(16).   [Key]
    05  XREF-CUST-ID               PIC 9(09).   [FK to CUSTFILE]
    05  XREF-ACCT-ID               PIC 9(11).   [FK to ACCTFILE]
    05  FILLER                     PIC X(14).
```

#### TRANFILE – Transaction History (CVTRA05Y)

**Key:** TRAN-ID (16 characters)
**Record Length:** 350 bytes

**Structure:**
```cobol
01  TRAN-RECORD.
    05  TRAN-ID                    PIC X(16).   [Key - Transaction ID]
    05  TRAN-TYPE-CD               PIC X(02).   [Transaction Type Code]
    05  TRAN-CAT-CD                PIC 9(04).   [Transaction Category Code]
    05  TRAN-SOURCE                PIC X(10).   [Transaction Source]
    05  TRAN-DESC                  PIC X(100).  [Transaction Description]
    05  TRAN-AMT                   PIC S9(09)V99. [Transaction Amount]
    05  TRAN-MERCHANT-ID           PIC 9(09).   [Merchant ID]
    05  TRAN-MERCHANT-NAME         PIC X(50).   [Merchant Name]
    05  TRAN-MERCHANT-CITY         PIC X(50).   [Merchant City]
    05  TRAN-MERCHANT-ZIP          PIC X(10).   [Merchant ZIP]
    05  TRAN-CARD-NUM              PIC X(16).   [Card (FK to XREF)]
    05  TRAN-ORIG-TS               PIC X(26).   [Original Timestamp]
    05  TRAN-PROC-TS               PIC X(26).   [Processing Timestamp]
    05  FILLER                     PIC X(20).
```

#### TCATBALF – Transaction Category Balance (CVTRA01Y)

**Key:** Composite Key (ACCT-ID + TYPE-CD + CAT-CD)
**Record Length:** 45 bytes

**Structure:**
```
Composite Key:
  - TRANCAT-ACCT-ID    PIC 9(11)
  - TRANCAT-TYPE-CD    PIC X(02)
  - TRANCAT-CD         PIC 9(04)

Data:
  - TRAN-CAT-BAL       PIC S9(09)V99  [Balance for this category]
```

## Copybooks (COBOL Data Structures)

### General Copybooks

| Copybook | Purpose | Usage |
|---|---|---|
| **COCOM01Y** | Common communication area between programs | All online programs (CICS) |
| **COSGN00** | Sign-On screen (BMS MAP) | COSGN00C |
| **COTTL01Y** | Title and header information | Reports and screens |
| **CSDAT01Y** | Date variables and constants | Date handling |
| **CSMSG01Y** | Message components and text | Error messages |
| **CSMSG02Y** | Additional message components | Error messages |
| **CSUSR01Y** | User security data | User management |
| **CSSETATY** | Attribute settings for screens | Screen handling |
| **CSUTLDWY** | Utility date functions | CSUTLDTC calls |
| **CSSTRPFY** | String processing functions | String handling |

### File Structure Copybooks

| Copybook | Record Type | Size | Purpose |
|---|---|---|---|
| **CVCUS01Y** | CUSTOMER-RECORD | 500 | Customer data (CUSTFILE) |
| **CVACT01Y** | ACCOUNT-RECORD | 300 | Account data (ACCTFILE) |
| **CVACT02Y** | CARDFILE-RECORD | 150 | Card data (CARDFILE) |
| **CVACT03Y** | CARD-XREF-RECORD | 50 | Card cross-reference (XREFFILE) |
| **CVTRA01Y** | TRAN-CAT-BAL-REC | 45 | Transaction category balance |
| **CVTRA02Y** | – | – | Transaction supplemental structure |
| **CVTRA03Y** | – | – | Transaction validation |
| **CVTRA05Y** | TRAN-RECORD | 350 | Transaction record |
| **CVTRA06Y** | DALYTRAN-RECORD | 350 | Daily transaction input |
| **CVTRA07Y** | – | – | Transaction reports |

### BMS Screen Copybooks

| Copybook | Screen | Type | Program |
|---|---|---|---|
| **COSGN00** | Sign-On | MAP/MAPSET | COSGN00C |
| **COTRN00** | Transaction List | MAP/MAPSET | COTRN00C |
| **COTRN01** | Transaction Detail | MAP/MAPSET | COTRN01C |
| **COTRN02** | Transaction Type Mgmt | MAP/MAPSET | COTRN02C |
| **COACTUP** | Account Update | MAP/MAPSET | COACTUPC |
| **COACTVW** | Account View | MAP/MAPSET | COACTVWC |
| **COCRDSL** | Card Select List | MAP/MAPSET | COCRDSLC |
| **COCRDLI** | Card License | MAP/MAPSET | COCRDLIC |
| **COCRDUP** | Card Update | MAP/MAPSET | COCRDUPC |
| **COUSR00** | User List | MAP/MAPSET | COUSR00C |
| **COUSR01** | User Create/Edit | MAP/MAPSET | COUSR01C |
| **COUSR02** | User Delete | MAP/MAPSET | COUSR02C |
| **COUSR03** | User Permissions | MAP/MAPSET | COUSR03C |
| **COADM01** | Admin Menu | MAP/MAPSET | COADM01C |
| **COMEN01** | Main Menu | MAP/MAPSET | COMEN01C |
| **CORPT00** | Report Menu | MAP/MAPSET | CORPT00C |
| **COBIL00** | Billing Details | MAP/MAPSET | COBIL00C |

## Sequential Files (Batch Input/Output)

| DDName | Dataset Name | Type | LRECL | Usage |
|---|---|---|---|---|
| **DALYTRAN** | AWS.M2.CARDDEMO.DALYTRAN.PS | Sequential | 350 | Daily transaction input |
| **DALYREJS** | AWS.M2.CARDDEMO.DALYREJS(+1) | Sequential | 430 | Daily transaction rejects |
| **XREFFILE** | AWS.M2.CARDDEMO.CARDXREF.VSAM | VSAM | 50 | Card-to-account cross-reference |
| **OUTFILE** | AWS.M2.CARDDEMO.RPTOUTPUT | Sequential | 1024 | Report output |

## DB2 Tables

The following tables are managed via DB2 and SQL:

### Transaction Type Management (app-transaction-type-db2)

| Table | Columns | Purpose |
|---|---|---|
| **TRANSACTION_TYPE** | TYPE_CD, DESC, ACTIVE_IND, CREATE_DATE | Transaction type directory |
| **TRANSACTION_CAT** | CAT_CD, DESC, TYPE_ID | Transaction category classification |

**Copybooks:**
- **CSDB2RWY** – Request workarea for DB2
- **CSDB2RPY** – Response workarea for DB2

### Authorization and Security (app-authorization-ims-db2-mq)

Various DB2 tables and IMS databases for:
- User authentication
- Role management
- Authorization certificates
- Pending authorization messages

## IMS Databases

| DBD Name | Description | Programs |
|---|---|---|
| **AUTHDB** | Authorization database | COPAUA0C, COPAUS0C, COPAUS1C, COPAUS2C |
| **USERDB** | User security database | CBPAUP0C, COSGN00C |
| **CERTDB** | Certificate database | Authorization programs |

PSB/DBD files are organized in the `app-authorization-ims-db2-mq/` directory.

## Communication Area (COMMAREA) for CICS

The COMMAREA is passed between CICS programs via XCTL/LINK:

**Copybook: COCOM01Y**

```cobol
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID        PIC X(04).
      10 CDEMO-FROM-PROGRAM       PIC X(08).
      10 CDEMO-TO-TRANID          PIC X(04).
      10 CDEMO-TO-PROGRAM         PIC X(08).
      10 CDEMO-USER-ID            PIC X(08).
      10 CDEMO-USER-TYPE          PIC X(01).
         88 CDEMO-USRTYP-ADMIN    VALUE 'A'.
         88 CDEMO-USRTYP-USER     VALUE 'U'.
      10 CDEMO-PGM-CONTEXT        PIC 9(01).
         88 CDEMO-PGM-ENTER       VALUE 0.
         88 CDEMO-PGM-REENTER     VALUE 1.
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID            PIC 9(09).
      10 CDEMO-CUST-FNAME         PIC X(25).
      10 CDEMO-CUST-MNAME         PIC X(25).
      10 CDEMO-CUST-LNAME         PIC X(25).
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID            PIC 9(11).
      10 CDEMO-ACCT-STATUS        PIC X(01).
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM           PIC 9(16).
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP           PIC X(7).
      10 CDEMO-LAST-MAPSET        PIC X(7).
```

---
*Analysis date: 2025-04-30*
*Total data size: ~140 KB per transaction cycle*
