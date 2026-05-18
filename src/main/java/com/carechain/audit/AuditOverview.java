package com.carechain.audit;

import java.util.List;

public record AuditOverview(
        List<AuditLogEntry> entries,
        AuditIntegrityReport integrity
) {
}
