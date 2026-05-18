package com.carechain.admin;

import com.carechain.bed.model.WardType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateWardRequest {

    @NotBlank(message = "Ward name is required")
    @Size(max = 50, message = "Ward name must be 50 characters or fewer")
    private String name;

    @NotNull(message = "Ward type is required")
    private WardType type;

    @NotNull(message = "Total beds is required")
    @Min(value = 1, message = "Total beds must be at least 1")
    @Max(value = 999, message = "Total beds must be 999 or fewer")
    private Integer totalBeds;

    @Pattern(
            regexp = "^$|^[A-Z0-9]{1,6}$",
            message = "Bed prefix must be 1 to 6 uppercase letters or numbers"
    )
    private String bedPrefix;
}
