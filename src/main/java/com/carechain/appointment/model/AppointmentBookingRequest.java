package com.carechain.appointment.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentBookingRequest {

    @NotNull(message = "Doctor is required")
    private Long doctorId;

    @NotNull(message = "Date is required")
    @FutureOrPresent(message = "Appointment date must be today or later")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @NotBlank(message = "Slot is required")
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Slot must use HH:mm format")
    private String slot;
}
