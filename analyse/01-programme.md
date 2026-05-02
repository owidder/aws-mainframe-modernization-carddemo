# CardDemo Program Inventory

## Overview

A total of **44 COBOL programs** are implemented in the CardDemo application:

| Category | Count | Description |
|---|---|---|
| **CICS Online** | 25 | Interactive screen programs for end users and administrators |
| **Batch Programs** | 17 | Background processing jobs for data management and reports |
| **DB2/Authorization** | 0 | Database and authorization programs |

## CICS Online Programs (Screen Programs)

These programs manage the user interface and online transactions via CICS terminals.

| Program ID | Transaction | Function |
|---|---|---|
| 00220000 | ???? | Accept and process TRANSACTION TYPE UPDATE        *00040000 |
| 002600 | ???? | List Transaction Type for updates and deletes    * |
| COACCT01 | ???? |  |
| COACTUPC | CA02 | Accept and process ACCOUNT UPDATE                * |
| COACTVWC | CA03 | Accept and process Account View request          * |
| COADM01C | CA01 | Admin Menu for Admin users |
| COBIL00C | CB00 | Bill Payment - Pay account balance in full and a |
| COCRDLIC | CC02 | List Credit Cards |
| COCRDSLC | CC01 | Accept and process credit card detail request    * |
| COCRDUPC | CC03 | Accept and process credit card detail request    * |
| CODATE01 | ???? |  |
| COMEN01C | CM01 | Main Menu for the Regular users |
| COPAUA0C | ???? | Card Authorization Decision Program |
| COPAUS0C | ???? | Summary View of Authorization Messages |
| COPAUS1C | ???? | Detail View of Authorization Message |
| COPAUS2C | ???? | Mark Authorization Message Fraud |
| CORPT00C | CR00 | Print Transaction reports by submitting batch |
| COSGN00C | CC00 | Signon Screen for the CardDemo Application |
| COTRN00C | CT00 | List Transactions from TRANSACT file |
| COTRN01C | CT01 | View a Transaction from TRANSACT file |
| COTRN02C | CT02 | Add a new Transaction to TRANSACT file |
| COUSR00C | CU00 | List all users from USRSEC file |
| COUSR01C | CU01 | Add a new Regular/Admin user to USRSEC file |
| COUSR02C | CU02 | Update a user in USRSEC file |
| COUSR03C | CU03 | Delete a user from USRSEC file |

### CICS Online Programs – Details

#### Sign-On and Navigation
- **COSGN00C (CC00)** – Sign-in screen with user authentication
- **COMEN01C (CM01)** – Main menu for regular users
- **COADM01C (CA01)** – Admin menu for administrative functions

#### User Management (COUSR family)
- **COUSR00C (CU00)** – User overview / list view
- **COUSR01C (CU01)** – Create/edit user
- **COUSR02C (CU02)** – Delete user / deactivation
- **COUSR03C (CU03)** – Manage user permissions

#### Account Management (COACTU/COACTVW)
- **COACTUPC** – Update account / modification
- **COACTVWC** – View account / read-only view

#### Card Management (COCRD family)
- **COCRDSLC** – Card list / selection
- **COCRDLIC** – Card licensing / activation
- **COCRDUPC** – Update card / changes

#### Transaction Management (COTRN family)
- **COTRN00C (CT00)** – Transaction list with paging
- **COTRN01C (CT01)** – Transaction details view
- **COTRN02C (CT02)** – Manage transaction types

#### Reporting and Utilities
- **CORPT00C (CR00)** – Report generation and display
- **COBIL00C (CB00)** – Billing details
- **COBSWAIT** – System wait function

## Batch Programs (Background Processing)

These programs process daily data volumes, perform calculations, and generate reports.

| Program ID | Type | Function |
|---|---|---|
| CBACT01C | Batch |  |
| CBACT02C | Batch | Read and print card data file. |
| CBACT03C | Batch | Read and print account cross reference data file. |
| CBACT04C | Batch | This is a interest calculator program. |
| CBCUS01C | Batch | Read and print customer data file. |
| CBEXPORT | Batch | Export Customer Data for Branch Migration |
| CBIMPORT | Batch | Import Customer Data from Branch Migration Export |
| CBPAUP0C | Batch | Delete Expired Pending Authorization Messages |
| CBSTM03A | Batch | Print Account Statements from Transaction data |
| CBSTM03B | Batch | Does file processing related to Transact Report |
| CBTRN01C | Batch | Post the records from daily transaction file. |
| CBTRN02C | Batch | Post the records from daily transaction file. |
| CBTRN03C | Batch | Print the transaction detail report. |
| COBTUPDT | Batch | Update Transaction type based on user input       *00040032 |
| DBUNLDGS | Batch |  |
| PAUDBLOD | Batch |  |
| PAUDBUNL | Batch |  |

### Batch Programs – Detailed Descriptions

#### Transaction Processing (CBTRN family)
- **CBTRN01C** – Processes daily transaction file, validates card numbers against XREF file
- **CBTRN02C** – Posts transactions, updates account balances and transaction category balances
- **CBTRN03C** – Extended transaction validation and error handling

#### Account Reporting (CBACT family)
- **CBACT01C** – Reads and prints customer file report
- **CBACT02C** – Reads and prints card file report
- **CBACT03C** – Reads and prints account cross-reference file
- **CBACT04C** – Interest calculation for accounts

#### Customer Management (CBCUS family)
- **CBCUS01C** – Reads and prints customer file with all customer data

#### Data Migration (CBEXPORT/CBIMPORT)
- **CBEXPORT** – Exports customer data for branch migration
- **CBIMPORT** – Imports customer data from branch migration

#### Billing and Statements (CBSTM family)
- **CBSTM03A** – Statement generation (master module)
- **CBSTM03B** – Statement generation (sub-module)

#### Authorization and Cleanup
- **CBPAUP0C** – Cleanup of expired authorization messages

## DB2 and Authorization Programs

| Program ID | Function |
|---|---|

### Authorization and Database Programs

#### Authorization Module (app-authorization-ims-db2-mq)
- **CBPAUP0C** – User authentication and authorization
- **COPAUA0C** – IMS/DB2 authorization lookup
- **COPAUS0C** – User security file access
- **COPAUS1C** – Additional security validation
- **COPAUS2C** – Permission management

#### Database and Data Exchange
- **DBUNLDGS** – Database unload (DB2)
- **PAUDBLOD** – Database load for authorization data
- **PAUDBUNL** – Database unload for authorization data

#### Transaction Type Management (app-transaction-type-db2)
- **COBTUPDT** – Transaction type update (DB2)
- **COTRTLIC** – Transaction type list (DB2 with licensing)
- **COTRTUPC** – Transaction type update with database access

#### Date Functions and Utilities
- **CODATE01** – Date management and calculation
- **COACCT01** – Account processing helpers
- **CSUTLDTC** – System utility for date functions

---
*Analysis date: 2025-04-30*
