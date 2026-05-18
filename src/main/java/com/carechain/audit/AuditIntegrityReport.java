package com.carechain.audit;

public record AuditIntegrityReport(
        boolean verified,
        long checkedEntries,
        Long mismatchEntryId,
        String mismatchReason,
        String headHash
) {
}
