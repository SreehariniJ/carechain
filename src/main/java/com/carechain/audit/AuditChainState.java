package com.carechain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_chain_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditChainState {

    @Id
    @Column(name = "chain_name", nullable = false, length = 50)
    private String chainName;

    @Column(name = "latest_hash", length = 64)
    private String latestHash;

    @Column(name = "entry_count", nullable = false)
    private long entryCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
