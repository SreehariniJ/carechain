package com.carechain.triage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SymptomAssessmentRepository extends JpaRepository<SymptomAssessment, Long> {

    @Query("SELECT sa FROM SymptomAssessment sa " +
            "JOIN FETCH sa.patient p " +
            "JOIN FETCH p.user " +
            "WHERE p.id = :patientId " +
            "ORDER BY sa.submittedAt DESC")
    List<SymptomAssessment> findHistoryForPatient(@Param("patientId") Long patientId);

    @Query(
            value = "SELECT sa FROM SymptomAssessment sa " +
                    "JOIN FETCH sa.patient p " +
                    "JOIN FETCH p.user " +
                    "ORDER BY sa.submittedAt DESC",
            countQuery = "SELECT COUNT(sa) FROM SymptomAssessment sa"
    )
    Page<SymptomAssessment> findRecentAssessments(Pageable pageable);

    @Query("SELECT sa FROM SymptomAssessment sa " +
            "JOIN FETCH sa.patient p " +
            "JOIN FETCH p.user " +
            "WHERE sa.id = :id")
    Optional<SymptomAssessment> findDetailedById(@Param("id") Long id);
}
