package com.carechain.bed.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "wards",
        uniqueConstraints = @UniqueConstraint(name = "uk_wards_name", columnNames = "name")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WardType type;

    @Column(name = "total_beds", nullable = false)
    private Integer totalBeds;

    @Column(name = "available_beds", nullable = false)
    private Integer availableBeds;
}
