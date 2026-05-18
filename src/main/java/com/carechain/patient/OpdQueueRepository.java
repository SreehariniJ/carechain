package com.carechain.patient;

import com.carechain.patient.model.OpdQueue;
import com.carechain.patient.model.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OpdQueueRepository extends JpaRepository<OpdQueue, Long> {

    @Query("SELECT COALESCE(MAX(q.tokenNumber), 0) FROM OpdQueue q WHERE q.department = :dept AND q.queueDate = :queueDate")
    int findMaxTokenForDepartmentOnDate(@Param("dept") String department, @Param("queueDate") LocalDate queueDate);

    List<OpdQueue> findByStatusIn(List<QueueStatus> statuses);

    List<OpdQueue> findByPatientIdOrderByJoinedAtDesc(Long patientId);

    List<OpdQueue> findByPatientIdAndQueueDateAndStatusInOrderByJoinedAtDesc(
            Long patientId, LocalDate queueDate, List<QueueStatus> statuses);

    Optional<OpdQueue> findFirstByPatientIdAndQueueDateAndStatusInOrderByJoinedAtDesc(
            Long patientId, LocalDate queueDate, List<QueueStatus> statuses);

    Optional<OpdQueue> findFirstByDepartmentAndQueueDateAndStatusOrderByTokenNumberAsc(
            String department, LocalDate queueDate, QueueStatus status);

    @Query("""
            SELECT q FROM OpdQueue q
            JOIN FETCH q.patient
            WHERE q.status IN :statuses AND q.queueDate = :queueDate
            ORDER BY q.department, q.tokenNumber
            """)
    List<OpdQueue> findActiveQueueWithPatients(@Param("statuses") List<QueueStatus> statuses,
                                               @Param("queueDate") LocalDate queueDate);
}
