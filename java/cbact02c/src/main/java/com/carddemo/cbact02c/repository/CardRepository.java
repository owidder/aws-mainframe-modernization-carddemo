package com.carddemo.cbact02c.repository;

import com.carddemo.cbact02c.domain.CardRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link CardRecord}.
 *
 * <p>Replaces the VSAM KSDS CARDFILE-FILE accessed in CBACT02C:
 * <pre>
 * COBOL: CBACT02C.cbl:28-33
 *   SELECT CARDFILE-FILE ASSIGN TO CARDFILE
 *          ORGANIZATION IS INDEXED
 *          ACCESS MODE  IS SEQUENTIAL
 *          RECORD KEY   IS FD-CARD-NUM
 * </pre>
 *
 * <p>{@code findAll(Sort.by("cardNum"))} replaces sequential READ CARDFILE-FILE
 * that advances through records ordered by the KSDS primary key (FD-CARD-NUM).
 */
@Repository
public interface CardRepository extends JpaRepository<CardRecord, String> {
}
