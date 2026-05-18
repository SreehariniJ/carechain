package com.carechain;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void patientAppointments_shouldReturnDoctorNameWhenOpenInViewIsDisabled() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"patient1@test.com",
                                  "password":"password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie authCookie = loginResult.getResponse().getCookie("jwt");

        mockMvc.perform(get("/api/appointments/me")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doctorName", not(isEmptyOrNullString())));
    }

    @Test
    void bookingAppointment_shouldRejectPastDatesWithValidationErrors() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"patient1@test.com",
                                  "password":"password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie authCookie = loginResult.getResponse().getCookie("jwt");

        mockMvc.perform(post("/api/appointments/book")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "doctorId": 1,
                                  "date": "%s",
                                  "slot": "10:00"
                                }
                                """.formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.date").value("Appointment date must be today or later"));
    }

    @Test
    void availableSlots_shouldReturnNotFoundForUnknownDoctor() throws Exception {
        LocalDate requestedDate = LocalDate.now().plusDays(1);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"patient1@test.com",
                                  "password":"password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie authCookie = loginResult.getResponse().getCookie("jwt");

        mockMvc.perform(get("/api/appointments/slots/{doctorId}/{date}", 9999L, requestedDate)
                        .cookie(authCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Doctor not found"))
                .andExpect(jsonPath("$.path").value("/api/appointments/slots/9999/" + requestedDate))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
