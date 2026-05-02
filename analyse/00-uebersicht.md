# CardDemo Application: Overview

## Summary

**CardDemo** is a legacy mainframe application for **credit card management and transaction processing**
with comprehensive customer management, account management, and reporting capabilities.
The application is built on classic mainframe technologies and is being migrated to a modern Java architecture.

## What Does the Application Do?

The CardDemo application manages:

- **Customer Management**: Recording and managing customer data (personal information, addresses, contact details, KYC data)
- **Account Management**: Managing credit card accounts with limits, balances, and transaction history
- **Card Management**: Managing credit cards, card numbers, and their assignment to accounts
- **Transaction Processing**: Capturing, validating, and settling daily transactions (online and batch)
- **Transaction Types**: Classifying and managing various transaction types via DB2
- **Users and Authorization**: Authenticating users, permission management via IMS/DB2/MQ
- **Reporting**: Daily, monthly, and ad-hoc reports on transactions, accounts, and customers

## Technology Stack

### Legacy Technologies (to be replaced)
| Technology | Description | Usage |
|---|---|---|
| **CICS** | Customer Information Control System | Online transactions, interactive screens |
| **COBOL** | Common Business-Oriented Language | All business logic, 44 programs |
| **VSAM** | Virtual Storage Access Method | Indexed files for master data (Customer, Account, Card, Transaction) |
| **JCL** | Job Control Language | Batch job orchestration, 38 jobs |
| **BMS** | Basic Mapping Support | Screen maps, 17 maps |
| **IMS** | Information Management System | Database for authorization data, PSB/DBD |
| **DB2** | IBM Database 2 | SQL queries for transaction types, reporting |
| **MQ** | Message Queue | Message queues for asynchronous processing |
| **Assembler** | Low-level language | System calls (MVSWAIT, COBDATFT) |

## High-Level Architecture

### Layers and Modules

```
┌─────────────────────────────────────────────┐
│     CICS ONLINE LAYER (Screens)            │
│  - COSGN00C (Sign-On)                      │
│  - COTRN00C-02C (Transaction List)         │
│  - COACTUPC/COACTVWC (Account Update/View)│
│  - COCRDSLC/COCRDLIC/COCRDUPC (Card Mgmt) │
│  - COUSR00C-03C (User Management)         │
│  - COADM01C (Admin Menu)                   │
│  - COMEN01C (Main Menu)                    │
└─────────────────────────────────────────────┘
           ↓ XCTL/LINK
┌─────────────────────────────────────────────┐
│    BATCH PROCESSING LAYER                  │
│  - CBTRN01C-03C (Transaction Posting)      │
│  - CBACT01C-04C (Account Reports)          │
│  - CBCUS01C (Customer Reports)             │
│  - CBEXPORT/CBIMPORT (Data Migration)      │
│  - CBPAUP0C (Auth Message Cleanup)         │
│  - CBSTM03A/B (Statement Generation)       │
└─────────────────────────────────────────────┘
           ↓ File I/O
┌─────────────────────────────────────────────┐
│    DATA ACCESS LAYER                       │
│  - VSAM Files (Customer, Account, Card)    │
│  - DB2 Tables (TransactionType)            │
│  - IMS Databases (Authorization)           │
│  - CICS File Control                       │
└─────────────────────────────────────────────┘
```

## Program Statistics

| Category | Count |
|---|---|
| **COBOL Programs (total)** | 44 |
| CICS Online | ~18 |
| Batch | ~22 |
| DB2/Authorization | ~8 |
| **JCL Jobs** | 38 |
| **Copybooks (data structures)** | 37 |
| **BMS Maps (screens)** | 17 |
| **VSAM Files** | 14+ |
| **Assembler Modules** | 2 (MVSWAIT, COBDATFT) |

## Key Files and Data Structures

### Primary Files (VSAM KSDS)
1. **CUSTFILE** – Customer management
2. **ACCTFILE** – Account management
3. **CARDFILE** – Card management
4. **XREFFILE** – Card-number-to-account cross-reference
5. **TRANFILE** – Transaction history
6. **TCATBALF** – Transaction category balance

### Copybooks (Data Structures)
- **COCOM01Y** – Common communication area
- **CVTRA05Y** – Transaction record
- **CVCUS01Y** – Customer record
- **CVACT01Y** – Account record
- **CVACT03Y** – Card number cross-reference
- **COSGN00** – Sign-On screen (BMS)
- **COTRN00-02** – Transaction list screens
- **COACTUP, COACTVW** – Account update/view screens
- **COUSR00-03** – User management screens
- **COCRDSL, COCRDLI, COCRDUP** – Card management screens

## Technology Challenges for Modernization

1. **Online layer**: CICS screens must be converted to a Web UI (Spring MVC/Thymeleaf)
2. **Batch layer**: Job orchestration must be migrated to Spring Batch or Quartz
3. **Data access**: VSAM to relational DB2/PostgreSQL
4. **Asynchronous processing**: CICS XCTL to service calls (REST/gRPC)
5. **Authentication**: IMS/CICS-based auth to Spring Security
6. **Reporting**: Batch reports to JasperReports/Pentaho

---
*Analysis date: 2025-04-30*
*Target platform: Java/Spring Boot, PostgreSQL, Docker, Kubernetes*
