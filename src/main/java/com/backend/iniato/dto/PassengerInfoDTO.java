package com.backend.iniato.dto;
public class PassengerInfoDTO {
    public Long passengerId;
    public String name;
    public String phone;
    public String email;
    public String status;
    public Double fareShare;
    public Double pickupLat;
    public Double pickupLng;
    public Double dropLat;
    public Double dropLng;
    public PassengerInfoDTO(Long passengerId, String name, String phone,
                            String email, String status, Double fareShare,
                            Double pickupLat, Double pickupLng,
                            Double dropLat, Double dropLng) {
        this.passengerId = passengerId;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.status = status;
        this.fareShare = fareShare;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropLat = dropLat;
        this.dropLng = dropLng;
    }
}
