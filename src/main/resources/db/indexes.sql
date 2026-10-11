-- ─────────────────────────────────────────────────────────────────────────────
-- Spatial indexes, applied on every startup (idempotent).
--
-- WHY THIS FILE EXISTS, SEPARATELY FROM import.sql
-- ------------------------------------------------
-- Hibernate runs `hbm2ddl.import_files` (import.sql) ONLY when it exports the
-- schema — that is, when ddl-auto is `create` or `create-drop`. This app runs
-- with ddl-auto=update, so import.sql has almost certainly never executed, and
-- the GIST indexes declared in it do not exist in any long-lived database.
-- Every spatial query has therefore been sequentially scanning.
--
-- Spring's own SQL initialisation is independent of ddl-auto, so it runs here
-- whatever the DDL mode. Paired with
-- `spring.jpa.defer-datasource-initialization=true`, it executes AFTER
-- Hibernate has created or updated the tables, so the targets always exist.
--
-- Ordinary (B-tree) indexes are NOT here: they are declared as @Index on the
-- entities, and ddl-auto=update does create those. Only expression and GIST
-- indexes, which JPA cannot express, live in this file.
--
-- Everything below is CREATE ... IF NOT EXISTS, so re-running is free: on all
-- but the first startup these are no-ops.
-- ─────────────────────────────────────────────────────────────────────────────

-- PostGIS itself. The geometry columns are unusable without it.
CREATE EXTENSION IF NOT EXISTS postgis;

-- driver_locations.current_location — the PostGIS proximity search in
-- DriverLocationRepository.findNearbyDrivers, used by RideMatchingService.
CREATE INDEX IF NOT EXISTS idx_driver_locations_current_location
    ON driver_locations USING GIST (current_location);

-- routes origin — RouteRepository.findActiveRoutesNear. The indexed expression
-- must match the query's exactly (ST_MakePoint(lng, lat), SRID 4326, cast to
-- geography) or the planner will ignore the index and scan instead.
CREATE INDEX IF NOT EXISTS idx_routes_origin_location
    ON routes USING GIST (
        (CAST(ST_SetSRID(ST_MakePoint(origin_lng, origin_lat), 4326) AS geography))
    );

-- ride_requests.pickup_location — MatchingServiceImpl.fetchNearbyRiderRequests.
-- The column is already a geometry; the cast mirrors the query.
CREATE INDEX IF NOT EXISTS idx_ride_requests_pickup_location
    ON ride_requests USING GIST ((pickup_location::geography));
