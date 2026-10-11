package com.backend.iniato.enums;

/**
 * Which table a settled ride lives in.
 * <p>
 * Pooled rides are {@code rides} rows, direct rides are {@code direct_rides}
 * rows, and their ids overlap — so a settlement is keyed by the pair
 * {@code (kind, rideId)} rather than by a foreign key to either table.
 */
public enum RideKind {
    POOLED,
    DIRECT
}
