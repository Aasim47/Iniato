package com.backend.iniato.dto;

public class PassengerInfoDTO {
    public Long passengerId;
    public String name;
    public String phone;
    public String email;
    public String status;
    public Double fareShare;

    public PassengerInfoDTO(Long passengerId, String name, String phone,
                            String email, String status, Double fareShare) {
        this.passengerId = passengerId;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.status = status;
        this.fareShare = fareShare;
    }
}
