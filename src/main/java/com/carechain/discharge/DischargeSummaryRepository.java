package com.carechain.discharge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DischargeSummaryRepository extends JpaRepository<DischargeSummary, Long> {

    Optional<DischargeSummary> findByAdmissionId(Long admissionId);

    List<DischargeSummary> findByAdmissionIdIn(Collection<Long> admissionIds);

    @Query("SELECT ds FROM DischargeSummary ds " +
            "JOIN FETCH ds.admission a " +
            "JOIN FETCH a.patient p " +
            "JOIN FETCH p.user " +
            "JOIN FETCH a.bed b " +
            "JOIN FETCH b.ward " +
            "WHERE a.id = :admissionId")
    Optional<DischargeSummary> findDetailedByAdmissionId(@Param("admissionId") Long admissionId);
}
