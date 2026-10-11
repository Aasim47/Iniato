package com.backend.iniato.dto;

import java.math.BigDecimal;

/**
 * What one passenger on a pooled ride pays, and how it splits.
 *
 * @param passengerId    the rider
 * @param roadKm         distance this rider travelled
 * @param share          total the rider pays — the number quoted to them
 * @param convenienceFee Iniato's cut, carved out of {@link #share}
 * @param netShare       {@code share - convenienceFee}, the ride portion
 */
public record RiderShare(
        Long passengerId,
        double roadKm,
        BigDecimal share,
        BigDecimal convenienceFee,
        BigDecimal netShare
) {}
