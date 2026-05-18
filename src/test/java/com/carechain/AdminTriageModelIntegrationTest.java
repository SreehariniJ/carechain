package com.carechain;

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
class AdminTriageModelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminShouldInspectAndRetrainTheCommittedTriageModel() throws Exception {
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

        mockMvc.perform(get("/api/admin/triage/model")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelVersion").value("local-triage-v2"))
                .andExpect(jsonPath("$.exampleCount").value(36))
                .andExpect(jsonPath("$.evaluation.sampleCount").value(36))
                .andExpect(jsonPath("$.departmentDistribution.Pediatrics").value(5));

        mockMvc.perform(post("/api/admin/triage/model/retrain")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelVersion").value("local-triage-v2"))
                .andExpect(jsonPath("$.corpusLabel").value("triage/training-corpus.csv"))
                .andExpect(jsonPath("$.evaluation.departmentAccuracy").isNotEmpty())
                .andExpect(jsonPath("$.evaluation.exactMatchAccuracy").isNotEmpty());
    }
}
