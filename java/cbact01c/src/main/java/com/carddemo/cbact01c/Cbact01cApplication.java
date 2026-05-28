package com.carddemo.cbact01c;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the CBACT01C batch job.
 *
 * <p>COBOL equivalent: {@code PROCEDURE DIVISION} main flow in CBACT01C.cbl.
 * The job runs automatically on startup ({@code spring.batch.job.enabled=true})
 * and the application exits when the job completes.
 */
@SpringBootApplication
public class Cbact01cApplication {

    public static void main(String[] args) {
        SpringApplication.run(Cbact01cApplication.class, args);
    }
}
