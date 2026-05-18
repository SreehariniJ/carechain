package com.carechain.appointment.model;

import com.carechain.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "doctors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 100)
    private String name;

    @Column(length = 100)
    private String specialization;

    @Column(name = "available_days", length = 100)
    private String availableDays;
}
