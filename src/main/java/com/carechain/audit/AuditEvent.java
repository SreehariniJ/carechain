package com.carechain.audit;

import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Builder
public class AuditEvent {

    private String action;
    private String resourceType;
    private String resourceId;
    @Builder.Default
    private AuditOutcome outcome = AuditOutcome.SUCCESS;
    @Builder.Default
    private Map<String, Object> details = new LinkedHashMap<>();
    private String actorEmailOverride;
    private String actorRoleOverride;
}
