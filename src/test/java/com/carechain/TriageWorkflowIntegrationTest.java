package com.carechain;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TriageWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void patientTriageShouldSupportAdminOverrideAndPersistFinalRecommendation() throws Exception {
        String patientEmail = "triage-flow@test.com";

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"password123",
                                  "name":"Triage Flow Patient",
                                  "age":10,
                                  "bloodGroup":"O+",
                                  "phone":"9876543233"
                                }
                                """.formatted(patientEmail)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie patientCookie = registerResult.getResponse().getCookie("jwt");

        MvcResult triageResult = mockMvc.perform(post("/api/triage/assess")
                        .cookie(patientCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symptoms":"My child has fever, cough, sore throat, and fatigue for the last two days."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedDepartment").value("Pediatrics"))
                .andReturn();

        long assessmentId = objectMapper.readTree(triageResult.getResponse().getContentAsString()).path("id").asLong();

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

        mockMvc.perform(put("/api/admin/triage/{id}/override", assessmentId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "department":"General Medicine",
                                  "triageLevel":"YELLOW",
                                  "reviewNote":"Primary care physician requested adult-medicine intake review."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalDepartment").value("General Medicine"))
                .andExpect(jsonPath("$.reviewStatus").value("OVERRIDDEN"));

        MvcResult patientHistory = mockMvc.perform(get("/api/triage/me")
                        .cookie(patientCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].finalDepartment").value("General Medicine"))
                .andExpect(jsonPath("$[0].reviewStatus").value("OVERRIDDEN"))
                .andReturn();

        JsonNode payload = objectMapper.readTree(patientHistory.getResponse().getContentAsString());
        JsonNode latest = payload.get(0);

        org.junit.jupiter.api.Assertions.assertEquals("Pediatrics", latest.path("suggestedDepartment").asText());
        org.junit.jupiter.api.Assertions.assertEquals("General Medicine", latest.path("finalDepartment").asText());
    }
}
