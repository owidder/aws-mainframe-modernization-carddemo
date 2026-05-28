package com.carddemo.cbact01c.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * JPA entity that replaces the VSAM ACCTDAT file (ACCTFILE-FILE in CBACT01C).
 *
 * <p>Field layout follows copybook {@code app/cpy/CVACT01Y.cpy}
 * (ACCOUNT-RECORD, 300 bytes, keyed on ACCT-ID PIC 9(11)).
 * All financial fields use {@link BigDecimal} with scale 2.
 *
 * <p>COBOL: CBACT01C.cbl:29-33 (SELECT ACCTFILE-FILE) / CBACT01C.cbl:52-55 (FD ACCTFILE-FILE)
 */
@Entity
@Table(name = "accounts")
public class Account {

    /** ACCT-ID PIC 9(11) — primary key */
    @Id
    @Column(name = "acct_id")
    private Long acctId;

    /** ACCT-ACTIVE-STATUS PIC X(01) — 'Y' or 'N' */
    @Column(name = "active_status", length = 1, nullable = false)
    private String activeStatus;

    /** ACCT-CURR-BAL PIC S9(10)V99 */
    @Column(name = "curr_bal", precision = 12, scale = 2, nullable = false)
    private BigDecimal currBal;

    /** ACCT-CREDIT-LIMIT PIC S9(10)V99 */
    @Column(name = "credit_limit", precision = 12, scale = 2, nullable = false)
    private BigDecimal creditLimit;

    /** ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 */
    @Column(name = "cash_credit_limit", precision = 12, scale = 2, nullable = false)
    private BigDecimal cashCreditLimit;

    /** ACCT-OPEN-DATE PIC X(10) format YYYY-MM-DD */
    @Column(name = "open_date", length = 10)
    private String openDate;

    /**
     * ACCT-EXPIRAION-DATE PIC X(10) format YYYY-MM-DD
     * <p>Spelling "EXPIRAION" (missing 'T') is retained from COBOL source.
     */
    @Column(name = "expiration_date", length = 10)
    private String expirationDate;

    /** ACCT-REISSUE-DATE PIC X(10) format YYYY-MM-DD */
    @Column(name = "reissue_date", length = 10)
    private String reissueDate;

    /** ACCT-CURR-CYC-CREDIT PIC S9(10)V99 */
    @Column(name = "curr_cyc_credit", precision = 12, scale = 2, nullable = false)
    private BigDecimal currCycCredit;

    /** ACCT-CURR-CYC-DEBIT PIC S9(10)V99 COMP-3 */
    @Column(name = "curr_cyc_debit", precision = 12, scale = 2, nullable = false)
    private BigDecimal currCycDebit;

    /** ACCT-ADDR-ZIP PIC X(10) */
    @Column(name = "addr_zip", length = 10)
    private String addrZip;

    /** ACCT-GROUP-ID PIC X(10) */
    @Column(name = "group_id", length = 10)
    private String groupId;

    public Long getAcctId()                { return acctId; }
    public String getActiveStatus()        { return activeStatus; }
    public BigDecimal getCurrBal()         { return currBal; }
    public BigDecimal getCreditLimit()     { return creditLimit; }
    public BigDecimal getCashCreditLimit() { return cashCreditLimit; }
    public String getOpenDate()            { return openDate; }
    public String getExpirationDate()      { return expirationDate; }
    public String getReissueDate()         { return reissueDate; }
    public BigDecimal getCurrCycCredit()   { return currCycCredit; }
    public BigDecimal getCurrCycDebit()    { return currCycDebit; }
    public String getAddrZip()             { return addrZip; }
    public String getGroupId()             { return groupId; }

    public void setAcctId(Long acctId)                           { this.acctId = acctId; }
    public void setActiveStatus(String activeStatus)             { this.activeStatus = activeStatus; }
    public void setCurrBal(BigDecimal currBal)                   { this.currBal = currBal; }
    public void setCreditLimit(BigDecimal creditLimit)           { this.creditLimit = creditLimit; }
    public void setCashCreditLimit(BigDecimal cashCreditLimit)   { this.cashCreditLimit = cashCreditLimit; }
    public void setOpenDate(String openDate)                     { this.openDate = openDate; }
    public void setExpirationDate(String expirationDate)         { this.expirationDate = expirationDate; }
    public void setReissueDate(String reissueDate)               { this.reissueDate = reissueDate; }
    public void setCurrCycCredit(BigDecimal currCycCredit)       { this.currCycCredit = currCycCredit; }
    public void setCurrCycDebit(BigDecimal currCycDebit)         { this.currCycDebit = currCycDebit; }
    public void setAddrZip(String addrZip)                       { this.addrZip = addrZip; }
    public void setGroupId(String groupId)                       { this.groupId = groupId; }
}
