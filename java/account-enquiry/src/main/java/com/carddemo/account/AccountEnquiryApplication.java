package com.carddemo.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CardDemo Account Enquiry Service
 *
 * <p>Replaces COACCT01.cbl: a CICS-hosted IBM MQ listener that received account
 * enquiry requests ({@code INQA} function code + account ID) and returned account
 * details from the ACCTDAT VSAM file.
 *
 * <p>This service exposes the same logic as a REST API backed by an H2 (embedded)
 * or PostgreSQL database, using Spring Data JPA instead of EXEC CICS READ.
 */
@SpringBootApplication
public class AccountEnquiryApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountEnquiryApplication.class, args);
    }
}
