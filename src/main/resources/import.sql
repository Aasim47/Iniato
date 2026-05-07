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

