-- Ensure PostGIS extension is enabled (required for geometry columns)
CREATE EXTENSION IF NOT EXISTS postgis;

-- Backfill NULL rating columns in driver_profile so NOT NULL constraint can be applied
UPDATE driver_profile SET average_rating = 0.0 WHERE average_rating IS NULL;
UPDATE driver_profile SET rating_count = 0 WHERE rating_count IS NULL;

-- Fix any existing driver_locations rows stored with SRID 0 → set to 4326
UPDATE driver_locations SET current_location = ST_SetSRID(current_location, 4326) WHERE ST_SRID(current_location) = 0;

-- Fix any existing ride_requests rows stored with SRID 0 → set to 4326
UPDATE ride_requests SET pickup_location = ST_SetSRID(pickup_location, 4326) WHERE pickup_location IS NOT NULL AND ST_SRID(pickup_location) = 0;
UPDATE ride_requests SET destination_location = ST_SetSRID(destination_location, 4326) WHERE destination_location IS NOT NULL AND ST_SRID(destination_location) = 0;

-- Fix any existing ride_passengers rows stored with SRID 0 → set to 4326
UPDATE ride_passengers SET pickup_location = ST_SetSRID(pickup_location, 4326) WHERE pickup_location IS NOT NULL AND ST_SRID(pickup_location) = 0;
UPDATE ride_passengers SET destination_location = ST_SetSRID(destination_location, 4326) WHERE destination_location IS NOT NULL AND ST_SRID(destination_location) = 0;

-- ── VehicleType migration ───────────────────────────────────────────────────
-- driver_profile.vehicle_type used to be free text ('AUTO_RICKSHAW', 'CAR',
-- 'MINI_TRUCK'); it is now the VehicleType enum (BIKE / AUTO / CAB) and JPA
-- throws on any other value when reading a row. Map the legacy values, then
-- sweep anything unrecognised to AUTO.
UPDATE driver_profile SET vehicle_type = 'BIKE'
    WHERE upper(coalesce(vehicle_type, '')) IN ('MOTORCYCLE', 'SCOOTER');
UPDATE driver_profile SET vehicle_type = 'CAB'
    WHERE upper(coalesce(vehicle_type, '')) IN ('CAR', 'TAXI', 'SEDAN', 'HATCHBACK');
UPDATE driver_profile SET vehicle_type = 'AUTO'
    WHERE vehicle_type IS NULL OR vehicle_type NOT IN ('BIKE', 'AUTO', 'CAB');

-- Backfill routes created before vehicle types existed, from their driver.
UPDATE routes r SET vehicle_type = coalesce(
        (SELECT dp.vehicle_type FROM driver_profile dp WHERE dp.user_id = r.driver_id),
        'AUTO')
    WHERE r.vehicle_type IS NULL;

-- The old seat-release path had no upper clamp, so a route could end up
-- advertising more free seats than it has. RideService.releaseSeat now clamps;
-- this repairs any row that already drifted.
UPDATE routes SET available_seats = total_seats
    WHERE available_seats IS NOT NULL AND total_seats IS NOT NULL
      AND available_seats > total_seats;

-- Create GIST index on driver_locations.current_location for faster spatial queries
CREATE INDEX IF NOT EXISTS idx_driver_locations_current_location ON driver_locations USING GIST (current_location);

-- Create GIST index on routes' origin for faster spatial queries. The expression
-- must match RouteRepository.findActiveRoutesNear exactly (SRID 4326, cast to
-- geography) or the planner will not use the index.
CREATE INDEX IF NOT EXISTS idx_routes_origin_location ON routes USING GIST (
    (CAST(ST_SetSRID(ST_MakePoint(origin_lng, origin_lat), 4326) AS geography))
);

-- GIST index on ride_requests pickup, for MatchingServiceImpl.fetchNearbyRiderRequests.
-- The column is already a geometry, so the expression mirrors the query's
-- ::geography cast.
CREATE INDEX IF NOT EXISTS idx_ride_requests_pickup_location ON ride_requests USING GIST (
    (pickup_location::geography)
);

-- NOTE: no data cleanup lives in this file. The ride_requests backlog is
-- cleared by MatchingServiceImpl.purgeSettledRideRequests, whose first nightly
-- run deletes everything already past the retention window. A destructive
-- statement here would run on every schema export instead of once, which is
-- the wrong shape for a one-off — and this file's execution is conditional
-- (see the header note), so it is the wrong place to rely on either.

