package com.carddemo.cbact02c;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the CBACT02C batch job.
 *
 * <p>COBOL equivalent: PROCEDURE DIVISION main flow in CBACT02C.cbl.
 * <pre>
 * DISPLAY 'START OF EXECUTION OF PROGRAM CBACT02C'
 * PERFORM 0000-CARDFILE-OPEN
 * PERFORM UNTIL END-OF-FILE = 'Y'
 *     PERFORM 1000-CARDFILE-GET-NEXT
 *     DISPLAY CARD-RECORD
 * END-PERFORM
 * PERFORM 9000-CARDFILE-CLOSE
 * DISPLAY 'END OF EXECUTION OF PROGRAM CBACT02C'
 * GOBACK.
 * </pre>
 *
 * <p>The job runs automatically on startup ({@code spring.batch.job.enabled=true})
 * and the application exits when the job completes (standard batch lifecycle).
 */
@SpringBootApplication
public class Cbact02cApplication {

    public static void main(String[] args) {
        SpringApplication.run(Cbact02cApplication.class, args);
    }
}
