package com.carechain.triage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SymptomAssessmentRequest {

    @NotBlank(message = "Symptoms are required")
    @Size(min = 20, max = 2000, message = "Symptoms should be between 20 and 2000 characters")
    private String symptoms;
}
