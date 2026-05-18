package com.carechain.bed;

import com.carechain.bed.model.Ward;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface WardRepository extends JpaRepository<Ward, Long> {
    List<Ward> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Ward w WHERE w.id = :id")
    Optional<Ward> findByIdForUpdate(@Param("id") Long id);
}
