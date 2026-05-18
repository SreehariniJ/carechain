package com.carechain.bed.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "beds",
        uniqueConstraints = @UniqueConstraint(name = "uk_beds_bed_number", columnNames = "bed_number")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id", nullable = false)
    private Ward ward;

    @Column(name = "bed_number", length = 20, nullable = false)
    private String bedNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BedStatus status;
}
