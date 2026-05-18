package com.carechain.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AuditTrailService {

    private static final String CHAIN_NAME = "main";
    private static final String DEFAULT_ACTOR_EMAIL = "anonymous";
    private static final String DEFAULT_ACTOR_ROLE = "ANONYMOUS";

    private final AuditLogRepository auditLogRepository;
    private final AuditChainStateRepository auditChainStateRepository;
    private final ObjectProvider<HttpServletRequest> requestProvider;
    private final ObjectMapper auditObjectMapper;

    public AuditTrailService(AuditLogRepository auditLogRepository,
                             AuditChainStateRepository auditChainStateRepository,
                             ObjectProvider<HttpServletRequest> requestProvider,
                             ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditChainStateRepository = auditChainStateRepository;
        this.requestProvider = requestProvider;
        this.auditObjectMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional
    public AuditLogEntry record(AuditEvent event) {
        AuditChainState chainState = auditChainStateRepository.findByChainNameForUpdate(CHAIN_NAME)
                .orElseGet(this::createInitialChainState);

        ResolvedActor actor = resolveActor(event);
        RequestMetadata requestMetadata = resolveRequestMetadata();
        LocalDateTime occurredAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        String detailsJson = serializeDetails(event.getDetails());
        String previousHash = chainState.getLatestHash();
        String actorEmail = trimToLength(actor.email(), 120);
        String actorRole = trimToLength(actor.role(), 30);
        String action = trimToLength(requireValue(event.getAction(), "action"), 80);
        String resourceType = trimToLength(requireValue(event.getResourceType(), "resourceType"), 80);
        String resourceId = trimToLength(event.getResourceId(), 120);
        AuditOutcome outcome = event.getOutcome() == null ? AuditOutcome.SUCCESS : event.getOutcome();
        String requestMethod = trimToLength(requestMetadata.method(), 10);
        String requestPath = trimToLength(requestMetadata.path(), 255);
        String ipAddress = trimToLength(requestMetadata.ipAddress(), 64);
        String userAgent = trimToLength(requestMetadata.userAgent(), 255);

        String entryHash = computeHash(
                previousHash,
                occurredAt,
                new ResolvedActor(actorEmail, actorRole),
                AuditEvent.builder()
                        .action(action)
                        .resourceType(resourceType)
                        .resourceId(resourceId)
                        .outcome(outcome)
                        .build(),
                new RequestMetadata(requestMethod, requestPath, ipAddress, userAgent),
                detailsJson);

        AuditLogEntry entry = auditLogRepository.save(AuditLogEntry.builder()
                .occurredAt(occurredAt)
                .actorEmail(actorEmail)
                .actorRole(actorRole)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .outcome(outcome)
                .requestMethod(requestMethod)
                .requestPath(requestPath)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .detailsJson(detailsJson)
                .previousHash(previousHash)
                .entryHash(entryHash)
                .build());

        chainState.setLatestHash(entryHash);
        chainState.setEntryCount(chainState.getEntryCount() + 1);
        chainState.setUpdatedAt(occurredAt);
        auditChainStateRepository.save(chainState);
        return entry;
    }

    @Transactional(readOnly = true)
    public AuditOverview getOverview(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<AuditLogEntry> entries = auditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, safeLimit)).getContent();
        return new AuditOverview(entries, verifyChain());
    }

    @Transactional(readOnly = true)
    public AuditIntegrityReport verifyChain() {
        List<AuditLogEntry> entries = auditLogRepository.findAllByOrderByIdAsc();
        AuditChainState chainState = auditChainStateRepository.findById(CHAIN_NAME)
                .orElse(null);

        String expectedPreviousHash = null;
        long checkedEntries = 0;

        for (AuditLogEntry entry : entries) {
            checkedEntries++;

            if (!Objects.equals(expectedPreviousHash, entry.getPreviousHash())) {
                return new AuditIntegrityReport(
                        false,
                        checkedEntries,
                        entry.getId(),
                        "Previous hash mismatch",
                        chainState != null ? chainState.getLatestHash() : null
                );
            }

            String expectedHash = computeHash(
                    expectedPreviousHash,
                    entry.getOccurredAt(),
                    new ResolvedActor(entry.getActorEmail(), entry.getActorRole()),
                    AuditEvent.builder()
                            .action(entry.getAction())
                            .resourceType(entry.getResourceType())
                            .resourceId(entry.getResourceId())
                            .outcome(entry.getOutcome())
                            .build(),
                    new RequestMetadata(entry.getRequestMethod(), entry.getRequestPath(), entry.getIpAddress(), entry.getUserAgent()),
                    entry.getDetailsJson()
            );

            if (!expectedHash.equals(entry.getEntryHash())) {
                return new AuditIntegrityReport(
                        false,
                        checkedEntries,
                        entry.getId(),
                        "Entry hash mismatch",
                        chainState != null ? chainState.getLatestHash() : null
                );
            }

            expectedPreviousHash = entry.getEntryHash();
        }

        if (chainState != null) {
            if (chainState.getEntryCount() != checkedEntries) {
                return new AuditIntegrityReport(
                        false,
                        checkedEntries,
                        null,
                        "Chain state entry count mismatch",
                        chainState.getLatestHash()
                );
            }
            if (!Objects.equals(chainState.getLatestHash(), expectedPreviousHash)) {
                return new AuditIntegrityReport(
                        false,
                        checkedEntries,
                        null,
                        "Chain head mismatch",
                        chainState.getLatestHash()
                );
            }
        }

        return new AuditIntegrityReport(true, checkedEntries, null, null, expectedPreviousHash);
    }

    private AuditChainState createInitialChainState() {
        return auditChainStateRepository.save(AuditChainState.builder()
                .chainName(CHAIN_NAME)
                .latestHash(null)
                .entryCount(0)
                .updatedAt(LocalDateTime.now())
                .build());
    }

    private ResolvedActor resolveActor(AuditEvent event) {
        if (hasText(event.getActorEmailOverride()) || hasText(event.getActorRoleOverride())) {
            return new ResolvedActor(
                    hasText(event.getActorEmailOverride()) ? event.getActorEmailOverride().trim().toLowerCase(Locale.ROOT) : DEFAULT_ACTOR_EMAIL,
                    hasText(event.getActorRoleOverride()) ? event.getActorRoleOverride().trim().toUpperCase(Locale.ROOT) : DEFAULT_ACTOR_ROLE
            );
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new ResolvedActor(DEFAULT_ACTOR_EMAIL, DEFAULT_ACTOR_ROLE);
        }

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .orElse(DEFAULT_ACTOR_ROLE);

        return new ResolvedActor(
                hasText(authentication.getName()) ? authentication.getName().trim().toLowerCase(Locale.ROOT) : DEFAULT_ACTOR_EMAIL,
                role
        );
    }

    private RequestMetadata resolveRequestMetadata() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return new RequestMetadata(null, null, null, null);
        }

        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            return new RequestMetadata(null, null, null, null);
        }

        try {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            String ipAddress = hasText(forwardedFor)
                    ? forwardedFor.split(",")[0].trim()
                    : request.getRemoteAddr();

            return new RequestMetadata(
                    request.getMethod(),
                    request.getRequestURI(),
                    ipAddress,
                    request.getHeader("User-Agent")
            );
        } catch (IllegalStateException exception) {
            return new RequestMetadata(null, null, null, null);
        }
    }

    private String serializeDetails(Map<String, Object> details) {
        Map<String, Object> safeDetails = new LinkedHashMap<>();
        if (details != null) {
            safeDetails.putAll(details);
        }

        try {
            return auditObjectMapper.writeValueAsString(safeDetails);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit details", exception);
        }
    }

    private String computeHash(String previousHash,
                               LocalDateTime occurredAt,
                               ResolvedActor actor,
                               AuditEvent event,
                               RequestMetadata requestMetadata,
                               String detailsJson) {
        String content = String.join("|",
                nullToEmpty(previousHash),
                nullToEmpty(occurredAt != null ? occurredAt.toString() : null),
                nullToEmpty(actor.email()),
                nullToEmpty(actor.role()),
                nullToEmpty(event.getAction()),
                nullToEmpty(event.getResourceType()),
                nullToEmpty(event.getResourceId()),
                nullToEmpty(event.getOutcome() != null ? event.getOutcome().name() : null),
                nullToEmpty(requestMetadata.method()),
                nullToEmpty(requestMetadata.path()),
                nullToEmpty(requestMetadata.ipAddress()),
                nullToEmpty(requestMetadata.userAgent()),
                nullToEmpty(detailsJson)
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String requireValue(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ResolvedActor(String email, String role) {
    }

    private record RequestMetadata(String method, String path, String ipAddress, String userAgent) {
    }
}
