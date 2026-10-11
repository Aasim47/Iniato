package com.backend.iniato.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassengerLocationUpdateDTO {
    private Long passengerId;
    private Long rideId;
    private Double latitude;
    private Double longitude;
}
