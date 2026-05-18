package com.carechain.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateDoctorRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be 100 characters or fewer")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String name;

    @NotBlank(message = "Specialization is required")
    @Size(max = 100, message = "Specialization must be 100 characters or fewer")
    private String specialization;

    @NotBlank(message = "Available days are required")
    @Pattern(
            regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
            message = "Available days must be comma-separated weekday abbreviations"
    )
    private String availableDays;
}
