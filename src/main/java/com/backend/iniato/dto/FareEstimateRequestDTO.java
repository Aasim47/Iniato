package com.backend.iniato.dto;

import lombok.Data;

@Data
public class FareEstimateRequestDTO {
    private double pickupLat;
    private double pickupLng;
    private double destLat;
    private double destLng;
    private int passengers;
    /**
     * BIKE, AUTO or CAB. Optional — defaults to AUTO when absent so that app
     * builds predating vehicle types keep receiving sane estimates.
     */
    private String vehicleType;

    public double getPickupLat() {
        return pickupLat;
    }

    public void setPickupLat(double pickupLat) {
        this.pickupLat = pickupLat;
    }

    public double getPickupLng() {
        return pickupLng;
    }

    public void setPickupLng(double pickupLng) {
        this.pickupLng = pickupLng;
    }

    public double getDestLat() {
        return destLat;
    }

    public void setDestLat(double destLat) {
        this.destLat = destLat;
    }

    public double getDestLng() {
        return destLng;
    }

    public void setDestLng(double destLng) {
        this.destLng = destLng;
    }

    public int getPassengers() {
        return passengers;
    }

    public void setPassengers(int passengers) {
        this.passengers = passengers;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }
}