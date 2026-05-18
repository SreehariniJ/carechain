package com.carechain.appointment;

import com.carechain.appointment.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByUserId(Long userId);
    List<Doctor> findAllByOrderByNameAsc();
}
