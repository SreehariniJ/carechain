package com.carechain.auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
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

    @Min(value = 0, message = "Age cannot be negative")
    @Max(value = 150, message = "Age must be 150 or less")
    private Integer age;

    @Pattern(
            regexp = "^(|A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-)$",
            message = "Blood group must be a valid type"
    )
    private String bloodGroup;

    @Pattern(
            regexp = "^$|^[0-9+()\\-\\s]{7,15}$",
            message = "Phone number must be 7 to 15 valid characters"
    )
    private String phone;
}
