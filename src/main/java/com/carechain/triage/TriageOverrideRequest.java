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
public class TriageOverrideRequest {

    @NotBlank(message = "Department is required")
    private String department;

    @NotNull(message = "Triage level is required")
    private TriageLevel triageLevel;

    @NotBlank(message = "Override note is required")
    @Size(max = 500, message = "Override note must be 500 characters or fewer")
    private String reviewNote;
}
