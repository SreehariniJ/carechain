package com.carechain.triage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TriageTrainingExampleRequest {

    @NotBlank(message = "Department is required")
    @Size(max = 100, message = "Department must be 100 characters or fewer")
    private String department;

    @NotNull(message = "Triage level is required")
    private TriageLevel triageLevel;

    @NotBlank(message = "Symptoms are required")
    @Size(min = 12, max = 2000, message = "Symptoms should be between 12 and 2000 characters")
    private String symptoms;
}
