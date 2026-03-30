package com.backend.iniato.dto;

import com.backend.iniato.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RiderRegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private Gender gender;

    private String preferredPaymentMethod;
}
