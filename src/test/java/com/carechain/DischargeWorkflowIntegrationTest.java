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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DischargeWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dischargedAdmissionShouldProduceBillingPreviewAndPdf() throws Exception {
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

        MvcResult admitResult = mockMvc.perform(post("/api/beds/admit/2")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn();

        long admissionId = objectMapper.readTree(admitResult.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(put("/api/beds/discharge/{admissionId}", admissionId)
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Patient discharged successfully"));

        MvcResult doctorLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"dr.smith@carechain.com",
                                  "password":"password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie doctorCookie = doctorLogin.getResponse().getCookie("jwt");

        mockMvc.perform(post("/api/discharges/{admissionId}/summary", admissionId)
                        .cookie(doctorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diagnosis":"Community acquired pneumonia",
                                  "treatmentSummary":"Received IV antibiotics, oxygen support, and fever control with stable improvement.",
                                  "dischargeInstructions":"Complete oral antibiotics, hydrate well, and return if breathing worsens.",
                                  "medications":"Amoxicillin 625 mg twice daily for 5 days; Paracetamol 650 mg as needed.",
                                  "followUpDate":"2099-12-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Discharge summary saved"));

        mockMvc.perform(get("/api/discharges/{admissionId}/billing", admissionId)
                        .cookie(doctorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayDays").value(1))
                .andExpect(jsonPath("$.lineItems[0].label", containsString("bed charges")))
                .andExpect(jsonPath("$.totalAmount").isNotEmpty());

        mockMvc.perform(get("/api/discharges/{admissionId}/pdf", admissionId)
                        .cookie(doctorCookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("carechain-discharge-" + admissionId + ".pdf")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void dischargeOverview_shouldReturnNotFoundForUnknownAdmission() throws Exception {
        MvcResult doctorLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"dr.smith@carechain.com",
                                  "password":"password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie doctorCookie = doctorLogin.getResponse().getCookie("jwt");

        mockMvc.perform(get("/api/discharges/{admissionId}", 99999L)
                        .cookie(doctorCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Admission not found"))
                .andExpect(jsonPath("$.path").value("/api/discharges/99999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void adminBedGridEndpoint_shouldOnlyMarkBedsDischargeableWhenActiveAdmissionExists() throws Exception {
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

        mockMvc.perform(post("/api/beds/admit/2")
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        MvcResult bedsResponse = mockMvc.perform(get("/api/admin/beds")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode beds = objectMapper.readTree(bedsResponse.getResponse().getContentAsString());
        JsonNode dischargeableBed = null;
        for (JsonNode bed : beds) {
            if (bed.path("canDischarge").asBoolean(false)) {
                dischargeableBed = bed;
                break;
            }
        }

        if (dischargeableBed == null) {
            throw new AssertionError("Expected at least one dischargeable bed after admitting a patient");
        }

        long bedId = dischargeableBed.path("id").asLong();

        mockMvc.perform(post("/api/admin/discharge/{bedId}", bedId)
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Patient discharged successfully"));
    }
}
