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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueueWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void queueShouldMoveFromWaitingToInProgressToDoneAcrossPatientAndAdminFlows() throws Exception {
        String patientEmail = "queue-flow@test.com";

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"password123",
                                  "name":"Queue Flow Patient",
                                  "age":33,
                                  "bloodGroup":"B+",
                                  "phone":"9876543222"
                                }
                                """.formatted(patientEmail)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie patientCookie = registerResult.getResponse().getCookie("jwt");

        MvcResult joinResult = mockMvc.perform(post("/api/queue/join/Dermatology")
                        .cookie(patientCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Dermatology"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andReturn();

        JsonNode joinPayload = objectMapper.readTree(joinResult.getResponse().getContentAsString());
        long queueId = joinPayload.get("id").asLong();
        int tokenNumber = joinPayload.get("tokenNumber").asInt();

        mockMvc.perform(get("/api/queue/me/active")
                        .cookie(patientCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.department").value("Dermatology"))
                .andExpect(jsonPath("$.tokenNumber").value(tokenNumber))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.peopleAhead").value(0));

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

        mockMvc.perform(post("/api/admin/queue/%d/start".formatted(queueId))
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/queue/me/active")
                        .cookie(patientCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentlyServing").value(tokenNumber));

        mockMvc.perform(post("/api/admin/queue/%d/complete".formatted(queueId))
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        mockMvc.perform(get("/api/queue/me/active")
                        .cookie(patientCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
