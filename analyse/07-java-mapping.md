# CardDemo – COBOL to Java Mapping

This document defines the Java class model for modernizing the CardDemo application. It builds on the architecture documents (00–05) and the detailed business logic analysis in `06-geschaeftslogik.md`.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│  REST API (Spring Boot)                                 │
│  Controller → Service → Repository (JPA/Hibernate)     │
├─────────────────────────────────────────────────────────┤
│  Batch (Spring Batch)                                   │
│  JobLauncher → Step → ItemReader/Processor/Writer       │
├─────────────────────────────────────────────────────────┤
│  Security (Spring Security)                             │
│  JWT / Session → UserDetailsService → BCrypt            │
├─────────────────────────────────────────────────────────┤
│  Persistence                                            │
│  PostgreSQL (replaces VSAM/DB2) + JPA Entities          │
└─────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.carddemo
├── config/              # Spring configurations
├── security/            # Auth, JWT, UserDetails
├── domain/              # JPA entities (= VSAM records)
│   ├── Customer.java
│   ├── Account.java
│   ├── Card.java
│   ├── CardXref.java
│   ├── Transaction.java
│   └── TransactionCategoryBalance.java
├── repository/          # JPA Repositories (= VSAM file access)
├── service/             # Business logic (= COBOL PROCEDURE DIVISION)
├── web/                 # REST controllers (= CICS transactions)
│   ├── dto/             # Request/Response DTOs
│   └── *Controller.java
└── batch/               # Spring Batch (= JCL + Batch COBOL)
    ├── transaction/
    ├── interest/
    ├── statement/
    └── report/
```

---

## Domain Entities (VSAM → JPA)

### Customer.java — replaces [`app/cpy/CVCUS01Y.cpy`](../app/cpy/CVCUS01Y.cpy)

```java
@Entity @Table(name = "customers")
public class Customer {
    @Id
    @Column(name = "cust_id", length = 9)
    private Long custId;                       // CUST-ID PIC 9(09)

    @Column(name = "first_name",  length = 25) private String firstName;   // CUST-FIRST-NAME
    @Column(name = "middle_name", length = 25) private String middleName;  // CUST-MIDDLE-NAME
    @Column(name = "last_name",   length = 25) private String lastName;    // CUST-LAST-NAME
    @Column(name = "addr_line1",  length = 50) private String addrLine1;   // CUST-ADDR-LINE-1
    @Column(name = "addr_line2",  length = 50) private String addrLine2;   // CUST-ADDR-LINE-2
    @Column(name = "addr_line3",  length = 50) private String addrLine3;   // CUST-ADDR-LINE-3
    @Column(name = "addr_state",   length = 2) private String addrState;   // CUST-ADDR-STATE-CD
    @Column(name = "addr_country", length = 3) private String addrCountry; // CUST-ADDR-COUNTRY-CD
    @Column(name = "addr_zip",    length = 10) private String addrZip;     // CUST-ADDR-ZIP
    @Column(name = "phone1",      length = 15) private String phone1;      // CUST-PHONE-NUM-1
    @Column(name = "phone2",      length = 15) private String phone2;      // CUST-PHONE-NUM-2
    @Column(name = "ssn",          length = 9) private String ssn;         // CUST-SSN PIC 9(09)
    @Column(name = "govt_id",     length = 20) private String govtIssuedId;// CUST-GOVT-ISSUED-ID
    @Column(name = "dob")                      private LocalDate dateOfBirth; // CUST-DOB-YYYY-MM-DD
    @Column(name = "eft_account_id", length = 10) private String eftAccountId; // CUST-EFT-ACCOUNT-ID
    @Column(name = "primary_card_holder") private boolean primaryCardHolder;   // CUST-PRI-CARD-HOLDER-IND
    @Column(name = "fico_score")          private Integer ficoScore;           // CUST-FICO-CREDIT-SCORE
}
```

### Account.java — replaces [`app/cpy/CVACT01Y.cpy`](../app/cpy/CVACT01Y.cpy)

```java
@Entity @Table(name = "accounts")
public class Account {
    @Id
    @Column(name = "acct_id", length = 11)
    private Long acctId;                                   // ACCT-ID PIC 9(11)

    @Column(name = "active_status", length = 1)  private String activeStatus;    // ACCT-ACTIVE-STATUS
    @Column(name = "curr_bal",      precision = 12, scale = 2) private BigDecimal currBal;
    @Column(name = "credit_limit",  precision = 12, scale = 2) private BigDecimal creditLimit;
    @Column(name = "cash_credit_limit", precision = 12, scale = 2) private BigDecimal cashCreditLimit;
    @Column(name = "open_date")       private LocalDate openDate;
    @Column(name = "expiration_date") private LocalDate expirationDate; // ACCT-EXPIRAION-DATE (sic — keep original spelling in comment)
    @Column(name = "reissue_date")    private LocalDate reissueDate;
    @Column(name = "curr_cyc_credit", precision = 12, scale = 2) private BigDecimal currCycCredit; // ACCT-CURR-CYC-CREDIT
    @Column(name = "curr_cyc_debit",  precision = 12, scale = 2) private BigDecimal currCycDebit;  // ACCT-CURR-CYC-DEBIT
    @Column(name = "addr_zip",   length = 10) private String addrZip;
    @Column(name = "group_id",   length = 10) private String groupId;  // ACCT-GROUP-ID — used for interest rate lookup
}
```

### Card.java — replaces [`app/cpy/CVACT02Y.cpy`](../app/cpy/CVACT02Y.cpy)

```java
@Entity @Table(name = "cards")
public class Card {
    @Id
    @Column(name = "card_num", length = 16)
    private String cardNum;                               // CARD-NUM PIC X(16)

    @ManyToOne @JoinColumn(name = "acct_id") private Account account;
    @ManyToOne @JoinColumn(name = "cust_id") private Customer customer;
    // additional fields from CVACT02Y
}
```

### CardXref.java — replaces [`app/cpy/CVACT03Y.cpy`](../app/cpy/CVACT03Y.cpy)

```java
@Entity @Table(name = "card_xref")
public class CardXref {
    @Id
    @Column(name = "card_num", length = 16)
    private String cardNum;                               // XREF-CARD-NUM PIC X(16)

    @Column(name = "cust_id",  length = 9)  private Long custId;  // XREF-CUST-ID PIC 9(09)
    @Column(name = "acct_id",  length = 11) private Long acctId;  // XREF-ACCT-ID PIC 9(11)
}
```

### Transaction.java — replaces [`app/cpy/CVTRA05Y.cpy`](../app/cpy/CVTRA05Y.cpy)

```java
@Entity @Table(name = "transactions")
public class Transaction {
    @Id
    @Column(name = "tran_id", length = 16)
    private String tranId;                                // TRAN-ID PIC X(16)

    @Column(name = "type_cd",     length = 2)  private String typeCd;      // TRAN-TYPE-CD
    @Column(name = "cat_cd")                   private Integer catCd;      // TRAN-CAT-CD PIC 9(04)
    @Column(name = "source",      length = 10) private String source;      // TRAN-SOURCE
    @Column(name = "description", length = 100) private String description; // TRAN-DESC
    @Column(name = "amount",      precision = 11, scale = 2) private BigDecimal amount; // TRAN-AMT S9(09)V99
    @Column(name = "merchant_id")              private Long merchantId;    // TRAN-MERCHANT-ID
    @Column(name = "merchant_name", length = 50) private String merchantName; // TRAN-MERCHANT-NAME
    @Column(name = "merchant_city", length = 50) private String merchantCity; // TRAN-MERCHANT-CITY
    @Column(name = "merchant_zip",  length = 10) private String merchantZip;  // TRAN-MERCHANT-ZIP
    @Column(name = "card_num",    length = 16) private String cardNum;     // TRAN-CARD-NUM → FK to card_xref
    @Column(name = "orig_timestamp")           private LocalDateTime origTimestamp; // TRAN-ORIG-TS X(26)
    @Column(name = "proc_timestamp")           private LocalDateTime procTimestamp; // TRAN-PROC-TS X(26)
}
```

### TransactionCategoryBalance.java — replaces [`app/cpy/CVTRA01Y.cpy`](../app/cpy/CVTRA01Y.cpy)

```java
@Entity @Table(name = "tran_cat_balance")
public class TransactionCategoryBalance {

    @EmbeddedId
    private TransactionCategoryKey id;                    // TCATBALF composite key

    @Column(name = "balance", precision = 11, scale = 2)
    private BigDecimal balance;                           // TRAN-CAT-BAL S9(09)V99

    @Embeddable
    public static class TransactionCategoryKey implements Serializable {
        @Column(name = "acct_id")  private Long acctId;    // TRANCAT-ACCT-ID PIC 9(11)
        @Column(name = "type_cd")  private String typeCd;  // TRANCAT-TYPE-CD PIC X(02)
        @Column(name = "cat_cd")   private Integer catCd;  // TRANCAT-CD PIC 9(04)
    }
}
```

---

## Repositories (VSAM file access → JPA)

```java
// CUSTFILE — Customer.java
public interface CustomerRepository extends JpaRepository<Customer, Long> {}

// ACCTFILE — Account.java
public interface AccountRepository extends JpaRepository<Account, Long> {}

// XREFFILE — CardXref.java
// Primary key: cardNum; alternate key: acctId (see CBACT04C:L204)
public interface CardXrefRepository extends JpaRepository<CardXref, String> {
    Optional<CardXref> findByAcctId(Long acctId);   // replaces alternate-key READ in CBACT04C
}

// TRANFILE — Transaction.java
public interface TransactionRepository extends JpaRepository<Transaction, String> {}

// TCATBALF — TransactionCategoryBalance.java (findOrCreate pattern from CBTRN02C:L474-498)
public interface TranCatBalanceRepository
        extends JpaRepository<TransactionCategoryBalance,
                              TransactionCategoryBalance.TransactionCategoryKey> {}
```

---

## COBOL Programs → Java Services

### CBTRN02C → TransactionPostingService

Source reference: [`app/cbl/CBTRN02C.cbl`](../app/cbl/CBTRN02C.cbl)

```java
@Service
public class TransactionPostingService {

    // Replicates 1500-VALIDATE-TRAN (CBTRN02C:L370)
    // NOTE: both checks always run — last error code wins (see CBTRN02C:L407+L414)
    public List<ValidationError> validate(DailyTransaction tran) {
        List<ValidationError> errors = new ArrayList<>();

        // 1500-A-LOOKUP-XREF (CBTRN02C:L380)
        CardXref xref = cardXrefRepository.findById(tran.getCardNum())
            .orElseThrow(() -> new ValidationException(100, "INVALID CARD NUMBER FOUND"));

        // 1500-B-LOOKUP-ACCT (CBTRN02C:L393)
        Account account = accountRepository.findById(xref.getAcctId())
            .orElseThrow(() -> new ValidationException(101, "ACCOUNT RECORD NOT FOUND"));

        // Credit limit check (CBTRN02C:L403-411)
        BigDecimal tempBal = account.getCurrCycCredit()
                                    .subtract(account.getCurrCycDebit())
                                    .add(tran.getAmount());
        if (account.getCreditLimit().compareTo(tempBal) < 0) {
            errors.add(new ValidationError(102, "OVERLIMIT TRANSACTION"));
        }

        // Expiration check (CBTRN02C:L414-419)
        if (account.getExpirationDate().isBefore(tran.getOrigTimestamp().toLocalDate())) {
            errors.add(new ValidationError(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION"));
        }

        return errors;
    }

    // Replicates 2000-POST-TRANSACTION (CBTRN02C:L424)
    // Order: TCATBAL → ACCOUNT → TRANSACTION  (same as CBTRN02C:L440-442)
    @Transactional
    public void postTransaction(DailyTransaction dalytran, CardXref xref, Account account) {

        // 2700-UPDATE-TCATBAL (CBTRN02C:L467) — findOrCreate pattern
        var key = new TransactionCategoryBalance.TransactionCategoryKey(
                      xref.getAcctId(), dalytran.getTypeCd(), dalytran.getCatCd());
        TransactionCategoryBalance catBal = tranCatBalanceRepository.findById(key)
            .orElse(new TransactionCategoryBalance(key, BigDecimal.ZERO));
        catBal.setBalance(catBal.getBalance().add(dalytran.getAmount()));
        tranCatBalanceRepository.save(catBal);

        // 2800-UPDATE-ACCOUNT-REC (CBTRN02C:L545)
        account.setCurrBal(account.getCurrBal().add(dalytran.getAmount()));
        if (dalytran.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
            account.setCurrCycCredit(account.getCurrCycCredit().add(dalytran.getAmount()));
        } else {
            account.setCurrCycDebit(account.getCurrCycDebit().add(dalytran.getAmount()));
        }
        accountRepository.save(account);

        // 2900-WRITE-TRANSACTION-FILE (CBTRN02C:L562)
        Transaction tran = mapToTransaction(dalytran);
        tran.setProcTimestamp(LocalDateTime.now());
        transactionRepository.save(tran);
    }
}
```

### CBACT04C → InterestCalculationService

Source reference: [`app/cbl/CBACT04C.cbl`](../app/cbl/CBACT04C.cbl)

```java
@Service
public class InterestCalculationService {

    // Replicates 1300-COMPUTE-INTEREST (CBACT04C:L462-465)
    // Formula: (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
    public BigDecimal computeMonthlyInterest(BigDecimal balance, BigDecimal annualRate) {
        return balance.multiply(annualRate)
                      .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
    }

    // Replicates 1050-UPDATE-ACCOUNT (CBACT04C:L350-354)
    @Transactional
    public void closeBillingCycle(Account account, BigDecimal totalInterest) {
        account.setCurrBal(account.getCurrBal().add(totalInterest));
        account.setCurrCycCredit(BigDecimal.ZERO);   // CBACT04C:L353
        account.setCurrCycDebit(BigDecimal.ZERO);    // CBACT04C:L354
        accountRepository.save(account);
    }

    // Replicates 1400-COMPUTE-FEES (CBACT04C:L518-519) — stub in original
    public void computeFees(Account account) {
        // TODO: fee calculation not implemented in source (CBACT04C:L518 is EXIT only)
    }
}
```

### COSGN00C → AuthController + AuthenticationService

Source reference: [`app/cbl/COSGN00C.cbl`](../app/cbl/COSGN00C.cbl)

```java
@Service
public class AuthenticationService implements UserDetailsService {

    // Replaces READ-USER-SEC-FILE (COSGN00C:L209)
    // BCrypt replaces plaintext compare (COSGN00C:L223: SEC-USR-PWD = WS-USER-PWD)
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        return userRepository.findByUserId(userId.toUpperCase())  // COSGN00C:L132 — uppercase
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}

@RestController @RequestMapping("/api/auth")
public class AuthController {

    // Replaces PROCESS-ENTER-KEY (COSGN00C:L108)
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getUserId().toUpperCase(),   // COSGN00C:L132
                request.getPassword()));
        // routing by user type (COSGN00C:L230-235) is expressed via JWT role claim
        String jwt = jwtService.generateToken(auth);
        return ResponseEntity.ok(new LoginResponse(jwt));
    }
}
```

---

## REST Endpoints (CICS Transactions → Spring MVC)

| COBOL Program | Transaction ID | HTTP Method | Endpoint |
|---------------|---------------|-------------|----------|
| `COSGN00C` | `CC00` | POST | `/api/auth/login` |
| `COMEN01C` | `CM01` | GET  | `/api/menu` |
| `COADM01C` | `CA01` | GET  | `/api/admin/menu` |
| `COACTUPC` | `CA02` | PUT  | `/api/accounts/{id}` |
| `COACTVWC` | `CA03` | GET  | `/api/accounts/{id}` |
| `COBIL00C` | `CB00` | POST | `/api/billing/pay` |
| `COCRDSLC` | `CC01` | GET  | `/api/cards?page=&size=` |
| `COCRDLIC` | `CC02` | POST | `/api/cards/{num}/activate` |
| `COCRDUPC` | `CC03` | PUT  | `/api/cards/{num}` |
| `COTRN00C` | `CT00` | GET  | `/api/transactions?page=` |
| `COTRN01C` | `CT01` | GET  | `/api/transactions/{id}` |
| `COTRN02C` | `CT02` | POST | `/api/transactions` |
| `COUSR00C` | `CU00` | GET  | `/api/users?page=` |
| `COUSR01C` | `CU01` | POST | `/api/users` |
| `COUSR02C` | `CU02` | PUT  | `/api/users/{id}` |
| `COUSR03C` | `CU03` | DELETE | `/api/users/{id}` |
| `CORPT00C` | `CR00` | POST | `/api/reports/generate` |

---

## Spring Batch Jobs (JCL → Spring Batch)

### POSTTRAN Job → TransactionPostingJob

Replaces JCL job that runs CBTRN01C + CBTRN02C.

```java
@Configuration
public class TransactionPostingJobConfig {

    @Bean
    public Job transactionPostingJob(Step validateStep, Step postStep) {
        return jobBuilder.get("POSTTRAN")
            .start(validateStep)
            .next(postStep)
            .build();
    }

    // Replaces CBTRN01C — validation with CUSTFILE + CARDFILE
    @Bean
    public Step validateStep() {
        return stepBuilder.<DailyTransactionRecord, ValidationResult>chunk(100)
            .reader(dalytranReader())         // sequential read of DALYTRAN
            .processor(validationProcessor()) // CBTRN01C logic
            .writer(rejectWriter())           // write to DALYREJS equivalent
            .build();
    }

    // Replaces CBTRN02C — posting to TRANFILE, ACCTFILE, TCATBALF
    @Bean
    public Step postStep() {
        return stepBuilder.<DailyTransactionRecord, Transaction>chunk(100)
            .reader(validatedTranReader())
            .processor(postingProcessor())    // CBTRN02C logic (TransactionPostingService)
            .writer(compositeWriter())        // writes to transaction + account + catbalance tables
            .build();
    }
}
```

### INTCALC Job → InterestCalculationJob

Replaces JCL job running CBACT04C.

```java
@Configuration
public class InterestCalculationJobConfig {

    @Bean
    public Job interestJob() {
        return jobBuilder.get("INTCALC")
            .start(interestStep())
            .build();
    }

    @Bean
    public Step interestStep() {
        // Replaces sequential scan of TCATBALF grouped by ACCT-ID (CBACT04C:L188-222)
        return stepBuilder.<TransactionCategoryBalance, InterestTransaction>chunk(50)
            .reader(tcatbalReader())
            .processor(interestProcessor())  // formula: (balance * rate) / 1200
            .writer(interestWriter())        // writes interest transaction + updates account
            .build();
    }
}
```

### STATEMENT Job → StatementGenerationJob

Replaces JCL job running CBSTM03A + CBSTM03B.

```java
@Configuration
public class StatementJobConfig {

    @Bean
    public Job statementJob() {
        return jobBuilder.get("STATEMENT")
            .start(statementStep())
            .build();
    }

    // Replaces CBSTM03A: text (80-char) and HTML output via Strategy pattern
    // (replaces ALTER/GOTO construct in CBSTM03A)
    @Bean
    public Step statementStep() {
        return stepBuilder.<Account, StatementResult>chunk(10)
            .reader(accountReader())
            .processor(statementProcessor())
            .writer(statementFileWriter())   // plain text + HTML output
            .build();
    }
}
```

---

## Security Configuration

### AppUser.java — replaces USRSEC file / [`app/cpy/CSUSR01Y.cpy`](../app/cpy/CSUSR01Y.cpy)

```java
@Entity @Table(name = "users")
public class AppUser implements UserDetails {
    @Id @Column(name = "user_id", length = 8)
    private String userId;                    // SEC-USR-ID PIC X(08)

    @Column(name = "first_name", length = 20) private String firstName;   // SEC-USR-FNAME
    @Column(name = "last_name",  length = 20) private String lastName;    // SEC-USR-LNAME
    @Column(name = "password")                private String passwordHash; // BCrypt — replaces SEC-USR-PWD plaintext
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 1)   private UserType userType;  // SEC-USR-TYPE: 'A' or 'U'
    @Column(name = "active")                  private boolean active;     // SEC-USR-ACTIVE-FLG

    public enum UserType { A, U }  // A = CDEMO-USRTYP-ADMIN, U = CDEMO-USRTYP-USER
}
```

---

## COBOL Date/Time Constructs → Java

| COBOL | Source Line | Java |
|-------|-------------|------|
| `FUNCTION CURRENT-DATE` | [CBTRN02C:L693](../app/cbl/CBTRN02C.cbl) | `LocalDateTime.now()` |
| DB2 timestamp `YYYY-MM-DD-HH.MM.SS.mm0000` | [CBTRN02C:L692-702](../app/cbl/CBTRN02C.cbl) | `DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'")` |
| `ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10)` | [CBTRN02C:L414](../app/cbl/CBTRN02C.cbl) | `!expDate.isBefore(origDate)` |
| `STRING PARM-DATE, SUFFIX INTO TRAN-ID` | [CBACT04C:L476-480](../app/cbl/CBACT04C.cbl) | `String.format("%s%06d", date, suffix)` |

---

## Migration Sequence (6 Phases)

| Phase | Content | Weeks |
|-------|---------|-------|
| **1 – Database Schema** | DDL from `03-datenstrukturen.md` → PostgreSQL; JPA entities; Flyway migrations | 1–2 |
| **2 – Authentication** | COSGN00C → AuthController + JWT; USRSEC migration with BCrypt | 3 |
| **3 – Core Batch** | CBTRN02C → TransactionPostingJob; CBACT04C → InterestCalculationJob | 4–6 |
| **4 – Online Programs** | COMEN01C, COUSR0x, COACTU*, COCRD*, COTRN* → REST controllers | 7–10 |
| **5 – Reporting** | CBSTM03A/B → StatementJob (JasperReports); CBACT01-03C reports; CORPT00C controller | 11–12 |
| **6 – Authorization/IMS** | COPAUA0C, COPAUS* → Spring Security roles; CBPAUP0C → scheduled cleanup job | 13–14 |

---

*Analysis date: 2026-05-02*
*Based on: COBOL source analysis (documents 00–06) and architecture documents*
