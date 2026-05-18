package com.carechain.patient.model;

import com.carechain.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "patients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 100)
    private String name;

    private Integer age;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(length = 15)
    private String phone;
}
