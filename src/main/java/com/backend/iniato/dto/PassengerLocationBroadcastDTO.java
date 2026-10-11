package com.backend.iniato.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassengerLocationBroadcastDTO {
    private Long passengerId;
    private Double latitude;
    private Double longitude;
}
