package com.backend.iniato.dto;


import com.backend.iniato.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;



@Getter
@Setter
public class RiderRegisterRequest extends BaseRegisterRequest {

    // Getters and setters
    private String name;

    private Gender gender;

    private String preferredPaymentMethod;

    // Optional: other rider-specific fields
    private String phoneNumber;

}
