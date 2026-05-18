package com.carechain.discharge;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DischargeSummaryRequest {

    @NotBlank(message = "Diagnosis is required")
    @Size(max = 2000, message = "Diagnosis must be 2000 characters or fewer")
    private String diagnosis;

    @Size(max = 2000, message = "Treatment summary must be 2000 characters or fewer")
    private String treatmentSummary;

    @Size(max = 2000, message = "Discharge instructions must be 2000 characters or fewer")
    private String dischargeInstructions;

    @Size(max = 2000, message = "Medications must be 2000 characters or fewer")
    private String medications;

    @FutureOrPresent(message = "Follow-up date must be today or later")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate followUpDate;
}
