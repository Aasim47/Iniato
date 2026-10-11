package com.backend.iniato.dto;

import lombok.Data;

/**
 * A fare quote.
 *
 * <p>{@link #perPassengerFare} is what the rider pays, in full.
 * {@link #convenienceFee} is Iniato's share <em>of</em> that amount, not an
 * addition to it — the two must never be summed for display.
 */
@Data
public class FareEstimateResponseDTO {

    private double distanceKm;
    private double totalFare;
    private double perPassengerFare;

    /** Iniato's fee, already included in {@link #perPassengerFare}. */
    private double convenienceFee;

    /** {@code perPassengerFare - convenienceFee} — the ride portion. */
    private double netFare;

    public FareEstimateResponseDTO(double distanceKm, double totalFare, double perPassengerFare) {
        this(distanceKm, totalFare, perPassengerFare, 0.0);
    }

    public FareEstimateResponseDTO(double distanceKm, double totalFare,
                                   double perPassengerFare, double convenienceFee) {
        this.distanceKm = distanceKm;
        this.totalFare = totalFare;
        this.perPassengerFare = perPassengerFare;
        this.convenienceFee = convenienceFee;
        this.netFare = Math.max(0.0, perPassengerFare - convenienceFee);
    }
}
