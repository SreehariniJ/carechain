package com.carechain.bed;

import com.carechain.bed.model.Admission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {

    @Query("SELECT a FROM Admission a JOIN FETCH a.patient JOIN FETCH a.bed WHERE a.dischargedAt IS NULL")
    List<Admission> findActiveAdmissions();

    @Query("SELECT a FROM Admission a " +
            "JOIN FETCH a.patient p " +
            "JOIN FETCH p.user " +
            "JOIN FETCH a.bed b " +
            "JOIN FETCH b.ward " +
            "WHERE a.dischargedAt IS NOT NULL " +
            "ORDER BY a.dischargedAt DESC")
    List<Admission> findRecentDischargedAdmissions(Pageable pageable);

    Optional<Admission> findByPatientIdAndActiveRecordKey(Long patientId, String activeRecordKey);

    Optional<Admission> findByBedIdAndActiveRecordKey(Long bedId, String activeRecordKey);

    @Query("SELECT a FROM Admission a " +
            "JOIN FETCH a.patient p " +
            "JOIN FETCH p.user " +
            "JOIN FETCH a.bed b " +
            "JOIN FETCH b.ward " +
            "WHERE a.id = :id")
    Optional<Admission> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Admission a JOIN FETCH a.patient JOIN FETCH a.bed WHERE a.id = :id")
    Optional<Admission> findByIdForUpdate(@Param("id") Long id);
}
