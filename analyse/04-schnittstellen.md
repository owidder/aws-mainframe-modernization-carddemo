# CardDemo – External Interfaces

## Interface Overview

The CardDemo application communicates via various interfaces:

1. **CICS Transactions** – Online user interaction
2. **BMS Screens** – User interface
3. **Batch Jobs (JCL)** – Offline processing
4. **File-Based Interfaces** – Import/export
5. **DB2 SQL Interface** – Databases
6. **IMS Interface** – Authorization and authentication
7. **MQ Queues** – Message exchange

## CICS Transactions

CICS transactions are online entry points through which users control the application.

### Transaction Overview

| Trans-ID | Program | Function | Screen |
|---|---|---|---|
| **CC00** | COSGN00C | User sign-on | COSGN00 |
| **CM01** | COMEN01C | Main menu | COMEN01 |
| **CA01** | COADM01C | Admin menu | COADM01 |
| **CU00** | COUSR00C | User list | COUSR00 |
| **CU01** | COUSR01C | Create/edit user | COUSR01 |
| **CU02** | COUSR02C | Delete user | COUSR02 |
| **CU03** | COUSR03C | User permissions | COUSR03 |
| **CA02** | COACTUPC | Update account | COACTUP |
| **CA03** | COACTVWC | View account | COACTVW |
| **CC01** | COCRDSLC | Card list | COCRDSL |
| **CC02** | COCRDLIC | License card | COCRDLI |
| **CC03** | COCRDUPC | Update card | COCRDUP |
| **CT00** | COTRN00C | Transaction list | COTRN00 |
| **CT01** | COTRN01C | Transaction details | COTRN01 |
| **CT02** | COTRN02C | Transaction types | COTRN02 |
| **CR00** | CORPT00C | Reports | CORPT00 |
| **CB00** | COBIL00C | Billing | COBIL00 |
| **CW00** | COBSWAIT | System wait | – |

### Transaction Characteristics

**Screen-based transactions:**
- Require **COMMAREA** for state management between screens
- Use **XCTL** for program transitions (not on call stack)
- Implement **paging** for large datasets (COTRN00C: 10 records per screen)
- Support **PF keys**: PF3 (return), PF7 (page back), PF8 (page forward)
- Type-safe **field validation** with error messages

**Session management:**
- Terminal session starts with COSGN00C (Sign-On)
- User ID is maintained in COMMAREA
- User type: 'A' (Admin) or 'U' (User)
- Navigation via menu hierarchy

## BMS Screens (Basic Mapping Support)

BMS files define the user interface layouts.

### BMS Screen Overview

| BMS Name | MAP Set | Size | Program | Purpose |
|---|---|---|---|---|
| **COSGN00** | COSGN00 | 24×80 | COSGN00C | Sign-on screen |
| **COMEN01** | COMEN01 | 24×80 | COMEN01C | Main menu |
| **COADM01** | COADM01 | 24×80 | COADM01C | Admin menu |
| **COUSR00** | COUSR00 | 24×80 | COUSR00C | User list |
| **COUSR01** | COUSR01 | 24×80 | COUSR01C | User editing |
| **COUSR02** | COUSR02 | 24×80 | COUSR02C | Delete user |
| **COUSR03** | COUSR03 | 24×80 | COUSR03C | Permissions |
| **COACTUP** | COACTUP | 24×80 | COACTUPC | Update account |
| **COACTVW** | COACTVW | 24×80 | COACTVWC | View account |
| **COCRDSL** | COCRDSL | 24×80 | COCRDSLC | Card list |
| **COCRDLI** | COCRDLI | 24×80 | COCRDLIC | License card |
| **COCRDUP** | COCRDUP | 24×80 | COCRDUPC | Update card |
| **COTRN00** | COTRN00 | 24×80 | COTRN00C | Transaction list |
| **COTRN01** | COTRN01 | 24×80 | COTRN01C | Transaction details |
| **COTRN02** | COTRN02 | 24×80 | COTRN02C | Transaction types |
| **CORPT00** | CORPT00 | 24×80 | CORPT00C | Report display |
| **COBIL00** | COBIL00 | 24×80 | COBIL00C | Billing details |

### BMS Screen Features

**Standard elements on all screens:**
- **TITLE line**: Shows application name and transaction ID
- **Date/timestamp**: Current system time in header
- **Error message area**: Red error messages
- **Navigation hints**: PF3, PF7, PF8 keys displayed at top right
- **Data entry areas**: With field validation

**Paging for list views:**
- COTRN00C: Displays max. 10 transactions per page
- COCRDSL: Card lists with paging
- COUSR00: User lists with paging
- PF7 = Page back, PF8 = Page forward

## Batch Jobs (JCL)

Batch jobs are controlled via JCL (Job Control Language).

### JCL Overview – Transaction Processing

| JCL Name | Program | Purpose | Input | Output |
|---|---|---|---|---|
| **POSTTRAN** | CBTRN02C | Daily transaction posting | DALYTRAN | TRANFILE, DALYREJS |
| **TRANFILE** | CBTRN01C | Transaction file build | DALYTRAN | TRANFILE |
| **TRANREPT** | CBTRN03C | Transaction report | TRANFILE | Report |
| **TRANBKP** | – | Transaction backup | TRANFILE | Backup file |
| **TRANIDX** | – | Transaction file index | TRANFILE | Index |
| **TRANCATG** | – | Transaction category setup | – | TCATBALF |

### JCL Overview – Account/Customer Management

| JCL Name | Program | Purpose | Input | Output |
|---|---|---|---|---|
| **CUSTFILE** | – | Customer file setup | – | CUSTFILE |
| **READCUST** | CBCUS01C | Customer report | CUSTFILE | Report |
| **CARDFILE** | – | Card file setup | – | CARDFILE |
| **READCARD** | CBACT02C | Card report | CARDFILE | Report |
| **ACCTFILE** | – | Account file setup | – | ACCTFILE |
| **READACCT** | CBACT01C | Account report | ACCTFILE | Report |
| **XREFFILE** | – | Xref file setup | – | XREFFILE |
| **READXREF** | CBACT03C | Xref report | XREFFILE | Report |

### JCL Overview – Data Migration

| JCL Name | Program | Purpose | Input | Output |
|---|---|---|---|---|
| **CBEXPORT** | CBEXPORT | Customer export | CUSTFILE | Export file |
| **CBIMPORT** | CBIMPORT | Customer import | Import file | CUSTFILE |
| **FTPJCL** | – | FTP data transfer | Export file | Remote system |

### JCL Overview – Utilities and Reports

| JCL Name | Program | Purpose |
|---|---|---|
| **INTCALC** | CBACT04C | Interest calculation |
| **CREASTMT** | CBSTM03A | Statement generation |
| **TRANREPT** | CBTRN03C | Transaction report |
| **PRTCATBL** | – | Print category balance |
| **REPTFILE** | – | Report file setup |
| **DALYREJS** | – | Daily rejects file |
| **DALYREJS** | – | Daily rejects report |
| **WAITSTEP** | COBSWAIT | Dummy wait step |
| **CLOSEFIL** | – | File close/cleanup |
| **OPENFIL** | – | File open/setup |

### JCL Overview – Database and GDG

| JCL Name | Purpose |
|---|---|
| **CREADB21** | Create DB2 tablespaces |
| **MNTTRDB2** | Maintain DB2 transaction type table |
| **TRANEXTR** | Extract transaction data from DB2 |
| **DBUNLDGS** | Unload data from DB2 |
| **PAUDBLOD** | Load authorization data to DB2 |
| **PAUDBUNL** | Unload authorization data from DB2 |
| **DEFGDGB** | Define GDG (Generation Data Group) – base |
| **DEFGDGD** | Define GDG (Generation Data Group) – versions |
| **ESDSRRDS** | Extended sequential dataset setup |
| **DISCGRP** | Disconnect group |

### JCL Overview – Data Utilities

| JCL Name | Purpose |
|---|---|
| **CBADMCDJ** | Admin card definition job |
| **DUSRSECJ** | User security definition job |
| **TCATBALF** | Transaction category balance file |
| **COMBTRAN** | Combine transaction files |

### Typical Batch Job Flow (POSTTRAN)

```
//POSTTRAN JOB CARD
//STEP15 EXEC PGM=CBTRN02C
//STEPLIB  DD DISP=SHR,DSN=AWS.M2.CARDDEMO.LOADLIB
//DALYTRAN DD DISP=SHR,DSN=AWS.M2.CARDDEMO.DALYTRAN.PS
         [Input: Daily transactions from previous day]
//TRANFILE DD DISP=SHR,DSN=AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS
         [Output: Master transaction file]
//XREFFILE DD DISP=SHR,DSN=AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS
         [Lookup: Card-to-account mapping]
//ACCTFILE DD DISP=SHR,DSN=AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS
         [Update: Account balances]
//TCATBALF DD DISP=SHR,DSN=AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS
         [Update: Category balances]
//DALYREJS DD DISP=(NEW,CATLG,DELETE),DSN=AWS.M2.CARDDEMO.DALYREJS(+1)
         [Output: Rejected transactions – GDG]
```

## DB2 Interfaces

DB2 is used for transaction types and authorization data.

### DB2 Tables and Programs

| Table | Program | Access | Purpose |
|---|---|---|---|
| **TRANSACTION_TYPE** | COBTUPDT, COTRTLIC | INSERT/UPDATE/SELECT | Transaction type management |
| **TRANSACTION_CAT** | COBTUPDT, COTRTLIC | SELECT | Category lookup |
| **USER_ROLES** | COPAUA0C | SELECT | User authorization |
| **USER_PERMISSIONS** | COPAUS0C | SELECT | Permission check |
| **AUDIT_LOG** | PAUDBLOD/PAUDBUNL | INSERT/SELECT | Audit trailing |

### Embedded SQL in COBOL

Programs such as **COBTUPDT** and **COTRTLIC** use:
- **EXEC SQL...END-EXEC** for SQL statements
- **Cursor-based** row-by-row processing
- **Error handling** with SQLCODE

## IMS Interfaces (Authorization)

IMS (Information Management System) is used for authorization data.

### IMS Programs

| Program | DBD | PSB | Function |
|---|---|---|---|
| **CBPAUP0C** | AUTHDB | AUTHPSB | User authentication |
| **COPAUA0C** | USERDB | USERPSB | Authorization lookup |
| **COPAUS0C** | CERTDB | CERTPSB | Certificate management |
| **PAUDBLOD** | AUTHDB | AUTHPSB | Load authorization data |
| **PAUDBUNL** | AUTHDB | AUTHPSB | Unload authorization data |

## MQ Interfaces (Message Queue)

MQ is used for asynchronous processing and message queueing.

### MQ Queues

| Queue Name | Type | Producer | Consumer | Message Format |
|---|---|---|---|---|
| **CARDDEMO.AUTH.REQUEST** | Local | COSGN00C, COUSR00C | CBPAUP0C | XML/Fixed |
| **CARDDEMO.AUTH.RESPONSE** | Local | CBPAUP0C | COSGN00C | XML/Fixed |
| **CARDDEMO.TRANSACTION.INPUT** | Local | COTRN00C | CBTRN02C | Fixed |
| **CARDDEMO.TRANSACTION.OUTPUT** | Local | CBTRN02C | CORPT00C | Fixed |
| **CARDDEMO.ERROR.QUEUE** | Local/Remote | All | Error handler | Fixed |
| **CARDDEMO.AUDIT.LOG** | Local | CBTRN02C, CBCUS01C | Audit | XML |

### MQ Usage

- **Asynchronous authentication**: COSGN00C writes auth request, waits for response
- **Transaction broadcasting**: CBTRN02C sends transactions to other systems
- **Error queue**: Faulty messages are written to the error queue

## FTP Interfaces

The FTPJCL defines FTP connections for data exchange with branch systems.

### FTP Files

- **Import queues**: Customer data from branches (→ CBIMPORT)
- **Export queues**: Customer data to branches (← CBEXPORT)
- **Report delivery**: Daily reports to management
- **Archive**: Backup of master files

## Interface Type Summary

| Interface | Async | Batched | Format | Protocol |
|---|---|---|---|---|
| **CICS Online** | No | No | Screen (BMS) | TCP/IP + CICS |
| **Batch JCL** | Yes | Yes | Sequential files | JES |
| **DB2 SQL** | Yes | Yes | SQL statements | DB2 network |
| **IMS** | Yes | Yes | Hierarchical | IMS teleprocessing |
| **MQ** | Yes | Yes | Fixed/XML | MQ Series |
| **FTP** | Yes | Yes | Binary/text | TCP/IP FTP |
| **VSAM Files** | Yes | Yes | Record-oriented | CICS file control |

---
*Analysis date: 2025-04-30*
*All 38 JCL jobs and 17 BMS screens are documented*
