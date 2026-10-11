package com.backend.iniato.dto;

/**
 * One passenger's journey on a pooled ride — the input to the
 * distance-weighted fare split.
 *
 * @param passengerId the rider
 * @param roadKm      road-adjusted distance this rider personally travels
 */
public record RiderLeg(Long passengerId, double roadKm) {}
