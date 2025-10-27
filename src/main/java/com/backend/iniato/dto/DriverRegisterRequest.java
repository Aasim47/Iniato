package com.backend.iniato.dto;


import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DriverRegisterRequest extends BaseRegisterRequest {
    @NotBlank(message = "Name is required")
    private String name;



    @NotBlank(message = "License number is required")
    private String licenseNumber;

    @NotBlank(message = "Vehicle details are required")
    private String vehicleDetails;

    private Double initialRating = 0.0;

}

