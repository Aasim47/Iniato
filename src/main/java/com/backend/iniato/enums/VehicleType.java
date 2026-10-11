package com.backend.iniato.enums;

/**
 * Vehicle classes Iniato operates, with their occupancy limit and Sambalpur
 * fare parameters.
 * <p>
 * Capacity and pricing deliberately live together: a route's seat count is
 * derived from {@link #getCapacity()} and never accepted from the client, so
 * the two can never drift apart.
 * <p>
 * <b>Rate basis (Sambalpur, Odisha).</b> Odisha has not notified per-km
 * app-cab tariffs — the only auto-rickshaw notification is No. 359/2008 and
 * the STA publishes stage-carriage rates only. These figures are benchmarked
 * against Rapido's Sambalpur launch pricing (₹15 base + ₹3/km, 2019) adjusted
 * to 2026, and against the ₹10–20 per head locals pay on shared tempo routes.
 * They must be confirmed with RTO Sambalpur before going live: the MV
 * Aggregator Guidelines 2020 cap aggregator pricing between 0.5× and 1.5× the
 * state-notified base fare.
 */
public enum VehicleType {

    /** Bike taxi — single pillion rider. Pooling is not possible. */
    BIKE(1, 20.0, 7.0, 25.0),

    /** Three-wheeler auto rickshaw — the default Iniato vehicle. */
    AUTO(3, 25.0, 11.0, 30.0),

    /** Four-seater cab. */
    CAB(4, 50.0, 15.0, 80.0);

    /** Distance covered by the base fare before per-km charging starts. */
    public static final double BASE_DISTANCE_KM = 2.0;

    /**
     * Multiplier converting straight-line (haversine) distance into an
     * approximate road distance. Sambalpur road routes run roughly 30% longer
     * than the crow-flies distance.
     */
    public static final double ROAD_FACTOR = 1.3;

    /** Discount applied to the total fare when 2+ riders share a vehicle. */
    public static final double POOL_DISCOUNT = 0.15;

    private final int capacity;
    private final double baseFare;
    private final double perKm;
    private final double minimumFare;

    VehicleType(int capacity, double baseFare, double perKm, double minimumFare) {
        this.capacity = capacity;
        this.baseFare = baseFare;
        this.perKm = perKm;
        this.minimumFare = minimumFare;
    }

    /** Maximum simultaneous passengers, excluding the driver. */
    public int getCapacity() {
        return capacity;
    }

    /** Fare covering the first {@link #BASE_DISTANCE_KM} km. */
    public double getBaseFare() {
        return baseFare;
    }

    /** Charge per km beyond {@link #BASE_DISTANCE_KM}. */
    public double getPerKm() {
        return perKm;
    }

    /** Floor applied to the computed total fare. */
    public double getMinimumFare() {
        return minimumFare;
    }

    /** True when this vehicle can carry more than one passenger at a time. */
    public boolean isPoolable() {
        return capacity > 1;
    }

    /**
     * Resolve a stored vehicle-type string, tolerating the legacy free-text
     * values that predate this enum ({@code AUTO_RICKSHAW}, {@code CAR}, …).
     *
     * @return the matching type, or {@link #AUTO} when the value is null,
     *         blank, or unrecognised.
     */
    public static VehicleType fromLegacy(String raw) {
        if (raw == null || raw.isBlank()) return AUTO;
        switch (raw.trim().toUpperCase()) {
            case "BIKE":
            case "MOTORCYCLE":
            case "SCOOTER":
                return BIKE;
            case "CAB":
            case "CAR":
            case "TAXI":
            case "SEDAN":
            case "HATCHBACK":
                return CAB;
            default:
                return AUTO;
        }
    }
}
