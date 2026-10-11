# Caching & Database-Cost Optimization

## Overview

How Iniato keeps its database usage inside Neon's free plan, and which of the
optimizations below are actually wired in.

The free plan gives **100 CU-hours of compute and 0.5 GB of storage per month**,
and suspends the database after 5 minutes of inactivity. Compute is the binding
constraint, so the goal is not only "fewer queries" but **long idle gaps during
which the database can sleep**.

## ⚠️ Implementation Status (reconciled with code)

| Item | Actual status |
|------|---------------|
| **Hikari pool tuned for scale-to-zero** | **Active — the single most important item.** Hikari defaults `minimum-idle` to `maximum-pool-size`, so the pool held 10 open connections forever and Neon could never suspend. At 0.25 CU that is ~182 CU-hours/month against a 100 CU-hour cap: the quota was gone in about two weeks *with no traffic at all*. Now `minimum-idle=0`, `idle-timeout=30s`, `keepalive-time=0` (keepalive pings would reset the autosuspend timer). Verified: the pool drains to 0 connections while idle and reconnects in ~150 ms. |
| **Driver-location write throttle** | **Active.** Every WebSocket tick used to run 3 Postgres round trips. Redis Geo and the WebSocket broadcast now happen first and always; Postgres is written at most once per minute per driver (~98% fewer writes). Covered by `DriverLocationServiceThrottleTest`. |
| **Spring `@Cacheable` on `users` / `nearbyRoutes`** | **Active.** `CacheConfig` supplies `@EnableCaching` and a Redis `CacheManager` — this is what was missing before, which left the old annotations inert. The stale-data risk that got them removed is addressed directly: `users` is explicitly evicted on every write path (`UserLookupService.evict`) with a 10-minute TTL as backstop, and `nearbyRoutes` has an 8-second TTL plus eviction on route create/cancel/complete. Its `availableSeats` is re-checked under a lock when joining, so a stale seat yields a clean "no seats available", never an overbooking. |
| **Cache failures degrade to misses** | **Active.** `CacheConfig.errorHandler()` treats a Redis error as a miss. Without it a Redis outage turned `GET /api/routes/nearby` into an HTTP 500 — observed, not theoretical. |
| **Single-query user lookup** | **Active.** Six classes each had their own `findByEmail(id).or(() -> findByPhoneNumber(id))`. Since most accounts are phone-based the first finder always missed, so each was 2 queries — and the JWT filter had already done the same lookup. Now one OR'd query in `UserRepository.findByEmailOrPhoneNumber`, cached, and reusing the principal the filter already resolved. |
| **PostGIS extension + GIST indexes** | **Active — but was silently broken.** They lived in `import.sql`, which Hibernate runs **only** when it exports the schema (`ddl-auto=create`/`create-drop`). This app runs `ddl-auto=update`, so the file never executed and the indexes never existed: every spatial query was sequentially scanning. They now live in `db/indexes.sql`, applied through Spring's SQL init, which is independent of `ddl-auto`. Verified present in the database and matched by the planner via `EXPLAIN`. |
| **B-tree indexes on hot columns** | **Active.** There were none at all. `@Index` declarations added for `ride_passengers`, `routes` and `ride_requests`; `ddl-auto=update` creates these. |
| **`open-in-view` disabled** | **Active.** It was quietly turning one query into several per response. Turning it off exposed three endpoints that only worked because of it — now fixed by mapping to DTOs inside the transaction. |
| **`ride_requests` growth leak** | **Fixed.** The scheduled purge only deleted `PENDING` demand-pool rows; requests from the normal join flow are `REQUESTED` → `ACCEPTED`/`CANCELLED` and were **never** deleted — one row per join attempt, each with two PostGIS geometries. A nightly purge now removes settled rows older than 30 days, skipping any still referenced by `ride_passengers`. |
| **Dead tables removed** | **Fixed.** `wallets` (no code referenced it) and `fares` (injected but never read or written) were being created on every deploy. Both deleted. |
| `findActiveRoutesNear` spatial query | **Fixed** earlier. Was JPQL mis-flagged `nativeQuery=true` and always threw; now valid native SQL. |
| Optimistic locking (`@Version` on `Route`) | **Active.** Plus a `PESSIMISTIC_WRITE` lock on the accept path to close the seat-overbooking race. |
| Redis Geo driver discovery | **Now on the read path.** `DriverLocationService.findNearbyDrivers` previously read ids from Redis and then issued one Postgres query *per driver* to recover coordinates Redis already held — an N+1 on a request path. It now returns positions straight from Redis. Note `RideMatchingService` still queries PostGIS directly and is unchanged. |
| Redis Pub/Sub decoupling | **Still dead/disabled.** Listeners only `println`, nothing publishes, and the container is gated behind `spring.redis.enabled=false`. Real-time events go directly through `SimpMessagingTemplate`. Untouched. |

### Known gap

The Redis cache path has **not** been exercised against a real Redis server —
none was available in the development environment. What is verified: the cache
annotations are wired and a second identical call is served without a query
(`CacheWiringTest`, in-memory manager); the cached `CachedUser` snapshot
survives a JSON round trip with the same serializer config (`CachedUserTest`);
and with Redis unreachable the endpoints still answer 200 by falling through to
the database. Worth confirming cache hits against a real Redis before relying on
the reduction.

The sections below describe the original intended design and predate the table
above; where they disagree, the table is correct.

## 1. Redis Caching Implementation

### 1.1 Active Routes Caching

**Location:** `src/main/java/com/backend/iniato/repo/RouteRepository.java`

**Implementation:**
- Added `@Cacheable(value = "routes", key = "#lat + '_' + #lng + '_' + #delta")` annotation to `findActiveRoutesNear()` method
- Caches active routes within a specified geographic radius
- Cache key includes latitude, longitude, and delta (search radius) to ensure cache specificity

**Benefits:**
- Eliminates repeated database queries for the same geographic area
- Reduces load on PostgreSQL/PostGIS database
- Improves response time for nearby route searches

### 1.2 User Profile Caching

**Location:** `src/main/java/com/backend/iniato/repo/UserRepository.java`

**Implementation:**
- Added `@Cacheable(value = "users", key = "#id")` to `findById()` method
- Added `@Cacheable(value = "users", key = "#email")` to `findByEmail()` method
- Added `@Cacheable(value = "users", key = "#phoneNumber")` to `findByPhoneNumber()` method

**Benefits:**
- Reduces database hits for JWT validation and user lookups
- Significantly improves authentication performance
- Caches user data by multiple lookup keys for flexibility

## 2. Redis Geo Commands for Driver Locations

### 2.1 DriverLocationGeoRepository

**Location:** `src/main/java/com/backend/iniato/repo/DriverLocationGeoRepository.java`

**Implementation:**
- New repository class using Redis Geo commands for driver location management
- `addDriverLocation()`: Stores driver coordinates in Redis using GEOADD
- `findNearbyDrivers()`: Retrieves nearby drivers using GEORADIUS
- `removeDriverLocation()`: Removes driver location from Redis

**Benefits:**
- Dramatically reduces PostgreSQL queries for driver proximity searches
- Redis stores locations in-memory for ultra-fast access
- Supports radius-based searches with sub-meter precision

### 2.2 Integration with DriverLocationService

**Location:** `src/main/java/com/backend/iniato/services/DriverLocationService.java`

**Implementation:**
- Updated `updateDriverLocation()` to sync driver locations to Redis Geo
- Added `removeDriverLocation()` method for cleanup
- Added `findNearbyDrivers()` method that queries Redis Geo first, then falls back to PostgreSQL

**Benefits:**
- Near real-time driver location updates
- Hybrid approach ensures data consistency
- Massive performance improvement for driver discovery

## 3. Database Query Optimization

### 3.1 PostGIS ST_DWithin Optimization

**Location:** `src/main/java/com/backend/iniato/repo/RouteRepository.java`

**Before:**
```sql
ABS(r.originLat - :lat) < :delta AND ABS(r.originLng - :lng) < :delta
```

**After:**
```sql
ST_DWithin(
  CAST(r.originLat AS geography), 
  CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), 
  :delta)
```

**Benefits:**
- Uses PostGIS spatial indexing instead of full table scans
- Leverages GIST indexes for exponential performance improvement
- Accurate geographic distance calculations

### 3.2 GIST Index Creation

**Location:** `src/main/resources/import.sql`

**Indexes Created:**
1. `idx_driver_locations_current_location` - GIST index on `driver_locations.current_location`
2. `idx_routes_origin_location` - GIST index on `routes.origin_lat, origin_lng`

**Benefits:**
- Enables efficient spatial queries
- Dramatically reduces query execution time
- Scales better with large datasets

## 4. WebSocket Integration with Redis Pub/Sub

### 4.1 RedisPubSubConfig

**Location:** `src/main/java/com/backend/iniato/config/RedisPubSubConfig.java`

**Implementation:**
- Configured Redis message listener container
- Set up listeners for route, driver, and ride events
- Supports pattern-based message subscription

**Benefits:**
- Decouples WebSocket communication from business logic
- Enables real-time updates across microservices
- Reduces WebSocket connection overhead

## 5. Optimistic Locking Implementation

### 5.1 Route Entity Updates

**Location:** `src/main/java/com/backend/iniato/entity/Route.java`

**Changes:**
- Added `@Version` annotation to Route entity
- Replaced pessimistic locking with optimistic locking

**Location:** `src/main/java/com/backend/iniato/services/RouteService.java`

**Changes:**
- Updated `cancelRoute()` method with exception handling
- Updated `completeRoute()` method with exception handling
- Added proper error messages for concurrent modification scenarios

**Benefits:**
- Reduces database contention
- Improves throughput under load
- Better scalability for high-traffic scenarios

## 6. Performance Improvements Summary

### 6.1 Expected Performance Gains

| Component | Before | After | Improvement |
|-----------|--------|-------|-------------|
| Active Routes Search | Full table scan | Indexed spatial query | 10-100x faster |
| User Lookups | Database query | Redis cache | 95%+ faster |
| Driver Discovery | PostgreSQL JOIN | Redis Geo query | 50-100x faster |
| Route Updates | Pessimistic lock | Optimistic lock | 2-5x higher throughput |

### 6.2 Resource Utilization

- **Database Load**: Reduced by ~70%
- **Memory Usage**: Optimized with Redis in-memory storage
- **CPU Usage**: Lowered due to reduced database queries
- **Network I/O**: Minimized with local Redis caching

## 7. Cache Management

### 7.1 Cache Configuration

**Redis Configuration:**
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

**Cache Names:**
- `routes`: Active routes cache
- `users`: User profiles cache

### 7.2 Cache Eviction

**Automatic Eviction:**
- Redis automatically evicts old entries based on configured policies
- Spring Cache provides additional eviction capabilities
- Cache TTL can be configured as needed

## 8. Monitoring and Maintenance

### 8.1 Cache Hit Rate Monitoring

**Recommended Monitoring:**
- Track cache hit rates for routes and users
- Monitor Redis memory usage
- Alert on cache eviction events

### 8.2 Maintenance Tasks

**Regular Maintenance:**
- Monitor Redis memory usage
- Update cache keys as business logic evolves
- Periodically clear caches during deployments

## 9. Testing Considerations

### 9.1 Cache Testing

**Test Coverage:**
- Verify cache hits on repeated queries
- Test cache invalidation scenarios
- Validate cache consistency across services

### 9.2 Integration Testing

**Recommended Tests:**
- End-to-end caching performance tests
- Failover scenarios (Redis down)
- Cache consistency validation

## 10. Future Enhancements

### 10.1 Cache Warm-up

**Potential Improvements:**
- Pre-load popular routes into cache
- Cache frequently accessed user data
- Implement cache warming strategies

### 10.2 Advanced Caching

**Future Options:**
- Redis clustering for high availability
- Cache invalidation patterns
- Distributed caching across multiple nodes

## 11. Conclusion

These caching optimizations provide significant performance improvements while maintaining data consistency and reliability. The implementation follows best practices for Redis integration and leverages PostgreSQL/PostGIS spatial capabilities effectively.

The optimizations are backward compatible and can be deployed incrementally without disrupting existing functionality.