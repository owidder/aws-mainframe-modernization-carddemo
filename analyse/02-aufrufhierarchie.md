# CardDemo – Call Hierarchy and Dependencies

## Overview of Call Relationships

The following diagrams show the dependencies between programs:

### Legend
- `-->` = Program A calls Program B with CALL
- `~>` = Program A transfers control to B with XCTL (CICS only)
- `*` = Called by other programs (not entry points)

## Utility and Service Programs

These programs are called by multiple other programs:

### System Utilities and Data Conversion

#### CSUTLDTC – Date Utilities
**Called by:**
- CORPT00C
- COTRN02C
- Various report programs

**Function:** Date functions and format conversion

#### CBSTM03B – Statement Generation Submodule
**Called by:**
- CBSTM03A (multiple times)

**Function:** Subroutine for statement creation and formatting

#### COBDATFT – Data Format Transfer
**Called by:**
- CBACT01C

**Function:** Converting date formats between COBOL and external systems

#### MVSWAIT – System Wait Function
**Called by:**
- COBSWAIT

**Function:** System calls for wait operations (Assembler)

## CICS Online Programs – Navigation Hierarchy

### Entry Point: COSGN00C (Sign-On)

```
COSGN00C (CC00 - Signon)
    ├─ valid user
    └─ XCTL -> COMEN01C (CM01 - Main Menu)
        ├─ User selected -> XCTL -> COUSR00C (CU00 - User List)
        │   ├─ selection -> XCTL -> COUSR01C (CU01 - User Edit)
        │   │   └─ save -> XCTL -> COUSR00C
        │   ├─ delete -> XCTL -> COUSR02C (CU02 - User Delete)
        │   └─ permissions -> XCTL -> COUSR03C (CU03 - User Perms)
        │
        ├─ Account selected -> XCTL -> COACTUPC (CA02 - Account Update)
        │   └─ view -> XCTL -> COACTVWC (CA03 - Account View)
        │
        ├─ Card selected -> XCTL -> COCRDSLC (CC01 - Card Select)
        │   ├─ view details -> XCTL -> COCRDLIC (CC02 - Card License)
        │   └─ update -> XCTL -> COCRDUPC (CC03 - Card Update)
        │
        ├─ Transaction selected -> XCTL -> COTRN00C (CT00 - Tran List)
        │   └─ select -> XCTL -> COTRN01C (CT01 - Tran Detail)
        │       └─ type mgmt -> XCTL -> COTRN02C (CT02 - Type Mgmt)
        │
        ├─ Report selected -> XCTL -> CORPT00C (CR00 - Reports)
        │
        ├─ Billing selected -> XCTL -> COBIL00C (CB00 - Billing)
        │
        └─ Admin selected -> XCTL -> COADM01C (CA01 - Admin Menu)
            └─ various admin functions
```

## Batch Programs – Job Flow

### Transaction Posting Job (POSTTRAN)

```
CBTRN02C - Post Transactions
    ├─ Read DALYTRAN file (daily transaction input)
    ├─ FOR EACH TRANSACTION:
    │   ├─ 1500-A-LOOKUP-XREF
    │   │   └─ Read XREFFILE (Card-to-Account mapping)
    │   ├─ 1500-B-LOOKUP-ACCT
    │   │   └─ Read ACCTFILE (Account details)
    │   ├─ 2000-POST-TRANSACTION
    │   │   ├─ CALL 'CSUTLDTC' (Date function)
    │   │   ├─ 2700-UPDATE-TCATBAL (Transaction Type Balance)
    │   │   ├─ 2800-UPDATE-ACCOUNT-REC (Account update)
    │   │   └─ 2900-WRITE-TRANSACTION-FILE (Write to TRANFILE)
    │   └─ 2500-WRITE-REJECT-REC (on validation error)
    └─ Generate summary report
```

### Account Processing Jobs

```
CBACT01C - Customer Data Export
    └─ Read CUSTFILE -> Print/Export

CBACT02C - Card Data Export
    └─ Read CARDFILE -> Print/Export

CBACT03C - Account Xref Export
    └─ Read XREFFILE -> Print/Export

CBACT04C - Interest Calculation
    ├─ Read ACCTFILE
    ├─ Calculate interest
    └─ Update balances
```

### Data Migration Jobs

```
CBEXPORT - Customer Export
    ├─ Read CUSTFILE
    ├─ Validate data
    └─ Write export file

CBIMPORT - Customer Import
    ├─ Read import file
    ├─ Validate data
    └─ Write to CUSTFILE
```

### Statement Generation

```
CBSTM03A - Statement Generator (Master)
    └─ CALL 'CBSTM03B' (10x for various parts)
        ├─ Part 1: Header
        ├─ Part 2: Account summary
        ├─ Part 3: Transaction details
        ├─ ... further parts ...
        └─ Part 10: Footer
```

## Program Call Matrix

**Programs called by others (via CALL):**

| Program | Called by | Call Type |
|---|---|---|
| **CBLTDLI** | DBUNLDGS, PAUDBLOD, PAUDBUNL | CALL |
| **CBSTM03B** | CBSTM03A | CALL |
| **CEEDAYS** | CSUTLDTC | CALL |
| **COBDATFT** | CBACT01C | CALL |
| **CSUTLDTC** | CORPT00C, COTRN02C | CALL |
| **MQCLOSE** | COACCT01, CODATE01, COPAUA0C | CALL |
| **MQGET** | COACCT01, CODATE01, COPAUA0C | CALL |
| **MQOPEN** | COACCT01, CODATE01, COPAUA0C | CALL |
| **MQPUT** | COACCT01, CODATE01 | CALL |
| **MQPUT1** | COPAUA0C | CALL |
| **MVSWAIT** | COBSWAIT | CALL |

## Entry Points (No Incoming Calls)

The following programs are entry points and are not called by other COBOL programs:

### CICS Online Entry Points
- **COSGN00C** – Sign-On (system entry point)
- **COMEN01C** – Main Menu (driven from Sign-On)
- **COADM01C** – Admin Menu (driven from Main Menu)
- **COUSR00C, COUSR01C, COUSR02C, COUSR03C** – User Management (driven from menu)
- **COACTUPC, COACTVWC** – Account Management (driven from menu)
- **COCRDSLC, COCRDLIC, COCRDUPC** – Card Management (driven from menu)
- **COTRN00C, COTRN01C, COTRN02C** – Transaction Management (driven from menu)
- **CORPT00C** – Reporting (driven from menu)
- **COBIL00C** – Billing (driven from menu)
- **COBSWAIT** – System utility

### Batch Job Entry Points
- **CBTRN01C** – Called by JCL job (directly)
- **CBTRN02C** – Called by JCL job (directly)
- **CBTRN03C** – Called by JCL job (directly)
- **CBACT01C** – Called by JCL job (directly)
- **CBACT02C** – Called by JCL job (directly)
- **CBACT03C** – Called by JCL job (directly)
- **CBACT04C** – Called by JCL job (directly)
- **CBCUS01C** – Called by JCL job (directly)
- **CBEXPORT** – Called by JCL job (directly)
- **CBIMPORT** – Called by JCL job (directly)
- **CBSTM03A** – Called by JCL job (directly)
- **CBPAUP0C** – Called by JCL job (directly)

### Authorization and DB2 Programs
- **COPAUA0C** – IMS/DB2 authorization (entry point)
- **DBUNLDGS** – Database unload
- **PAUDBLOD** – Database load
- **COBTUPDT** – Transaction type update (DB2)
- **COTRTLIC** – Transaction type list
- **COTRTUPC** – Transaction type update

---
*Analysis date: 2025-04-30*
