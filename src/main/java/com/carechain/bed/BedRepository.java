package com.carechain.bed;

import com.carechain.bed.model.Bed;
import com.carechain.bed.model.BedStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BedRepository extends JpaRepository<Bed, Long> {

    List<Bed> findByWardId(Long wardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "ward")
    Optional<Bed> findFirstByStatusOrderByIdAsc(BedStatus status);

    Optional<Bed> findFirstByWardIdAndStatusOrderByIdAsc(Long wardId, BedStatus status);

    long countByStatus(BedStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "ward")
    @Query("SELECT b FROM Bed b WHERE b.id = :id")
    Optional<Bed> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT b FROM Bed b JOIN FETCH b.ward ORDER BY b.ward.name, b.bedNumber")
    List<Bed> findAllWithWard();
}
