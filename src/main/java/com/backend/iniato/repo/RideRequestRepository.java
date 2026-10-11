package com.backend.iniato.repo;

import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.RideRequest;
import com.backend.iniato.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    List<RideRequest> findByPassenger(User passenger);

    List<RideRequest> findByMatchedRideAndStatus(Ride ride, String status);

    /**
     * Find PENDING ride requests whose pickup is within {@code radiusMeters} of
     * a given point and were created within the last {@code since} minutes (not stale).
     */
    @Query(value = """
            SELECT rr.* FROM ride_requests rr
            WHERE rr.status = 'PENDING'
              AND rr.pickup_time >= :since
              AND ST_DWithin(
                    rr.pickup_location::geography,
                    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                    :radiusMeters)
            """, nativeQuery = true)
    List<RideRequest> findPendingNear(@Param("lat") double lat,
                                      @Param("lng") double lng,
                                      @Param("radiusMeters") double radiusMeters,
                                      @Param("since") LocalDateTime since);

    /** Purge PENDING requests older than {@code expiryTime}. */
    @Modifying
    @Query("DELETE FROM RideRequest r WHERE r.status = 'PENDING' AND r.pickupTime < :expiryTime")
    int deleteExpiredPendingRequests(@Param("expiryTime") LocalDateTime expiryTime);

    /**
     * Purge long-settled requests — the ones the PENDING sweep never touched.
     *
     * <p>{@code deleteExpiredPendingRequests} only removes demand-pool entries.
     * Requests created by {@code RideService.requestSharedRide} are born
     * REQUESTED and move to ACCEPTED or CANCELLED, so they fell outside that
     * sweep and accumulated permanently: one row per join attempt, for the life
     * of the deployment. On Neon's free plan storage is capped at 0.5 GB per
     * project, and these rows carry two PostGIS geometries each.
     *
     * <p>Rows are deleted only once they are well past any operational use.
     * {@code ride_passengers.ride_request_id} references these rows, so
     * anything still referenced is excluded — the audit trail behind a ride a
     * user can still see stays intact, and the FK cannot be violated.
     */
    @Modifying
    @Query("""
            DELETE FROM RideRequest r
            WHERE r.status IN ('ACCEPTED', 'CANCELLED', 'REJECTED', 'COMPLETED')
              AND r.pickupTime < :expiryTime
              AND NOT EXISTS (
                    SELECT 1 FROM RidePassenger rp WHERE rp.rideRequest = r)
            """)
    int deleteSettledRequestsBefore(@Param("expiryTime") LocalDateTime expiryTime);
}
