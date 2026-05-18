package com.carechain;

import com.carechain.audit.AuditEvent;
import com.carechain.audit.AuditTrailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditTrailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditTrailService auditTrailService;

    @Test
    void adminAuditEndpoint_shouldReturnVerifiedChainWithRecentClinicalEvents() throws Exception {
        String patientEmail = "audit-flow@test.com";

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"password123",
                                  "name":"Audit Flow Patient",
                                  "age":29,
                                  "bloodGroup":"AB+",
                                  "phone":"9876543299"
                                }
                                """.formatted(patientEmail)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie patientCookie = registerResult.getResponse().getCookie("jwt");

        mockMvc.perform(post("/api/queue/join/Cardiology")
                        .cookie(patientCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"admin@carechain.com",
                                  "password":"password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie adminCookie = adminLogin.getResponse().getCookie("jwt");

        MvcResult auditResult = mockMvc.perform(get("/api/admin/audit")
                        .param("limit", "10")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integrity.verified").value(true))
                .andExpect(jsonPath("$.integrity.headHash").isNotEmpty())
                .andReturn();

        JsonNode payload = objectMapper.readTree(auditResult.getResponse().getContentAsString());
        JsonNode entries = payload.get("entries");

        boolean hasPatientRegistered = StreamSupport.stream(entries.spliterator(), false)
                .anyMatch(entry -> "PATIENT_REGISTERED".equals(entry.path("action").asText()));
        boolean hasQueueJoined = StreamSupport.stream(entries.spliterator(), false)
                .anyMatch(entry -> "QUEUE_JOINED".equals(entry.path("action").asText()));

        assertTrue(hasPatientRegistered, "Expected PATIENT_REGISTERED to appear in audit trail");
        assertTrue(hasQueueJoined, "Expected QUEUE_JOINED to appear in audit trail");
    }

    @Test
    void auditTrail_shouldRecordEventsWithoutHttpRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        var entry = auditTrailService.record(AuditEvent.builder()
                .action("BOOTSTRAP_ADMIN_CREATED")
                .resourceType("USER")
                .resourceId("bootstrap-admin")
                .actorEmailOverride("system@carechain")
                .actorRoleOverride("SYSTEM")
                .build());

        assertNull(entry.getRequestMethod());
        assertNull(entry.getRequestPath());
        assertNull(entry.getIpAddress());
        assertNull(entry.getUserAgent());

        RequestContextHolder.resetRequestAttributes();
    }
}
