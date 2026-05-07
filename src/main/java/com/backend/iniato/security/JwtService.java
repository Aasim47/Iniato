package com.backend.iniato.security;

import com.backend.iniato.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    // Access token: 15 minutes
    private static final long ACCESS_TOKEN_EXPIRY_MS = 1000L * 60 * 15;
    // Refresh token: 7 days
    private static final long REFRESH_TOKEN_EXPIRY_MS = 1000L * 60 * 60 * 24 * 7;

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public JwtService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ─── Token Generation ───

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        if (userDetails.getUsername() != null) {
            claims.put("email", userDetails.getUsername());
        } else if (userDetails instanceof User u) {
            claims.put("phone", u.getPhoneNumber());
        }
        String subject = userDetails.getUsername() != null ? userDetails.getUsername()
                : (userDetails instanceof User u ? u.getPhoneNumber() : "unknown");
        return buildToken(claims, subject, ACCESS_TOKEN_EXPIRY_MS);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails.getUsername(), ACCESS_TOKEN_EXPIRY_MS);
    }

    /** Opaque refresh token stored in Redis mapping to the user's username. */
    public String generateRefreshToken(UserDetails userDetails) {
        String refreshToken = UUID.randomUUID().toString();
        String redisKey = "refresh:" + refreshToken;
        redisTemplate.opsForValue().set(redisKey, userDetails.getUsername(),
                REFRESH_TOKEN_EXPIRY_MS, TimeUnit.MILLISECONDS);
        return refreshToken;
    }

    /** Validates a refresh token and returns the associated username, or null if invalid/expired. */
    public String validateRefreshToken(String refreshToken) {
        String redisKey = "refresh:" + refreshToken;
        Object username = redisTemplate.opsForValue().get(redisKey);
        return username != null ? username.toString() : null;
    }

    /** Revoke a refresh token (logout). */
    public void revokeRefreshToken(String refreshToken) {
        redisTemplate.delete("refresh:" + refreshToken);
    }

    // ─── Access Token Blacklist (Redis) ───

    /** Blacklist an access token until its expiry. */
    public void revokeToken(String token) {
        long expiryMs = extractExpiration(token).getTime() - System.currentTimeMillis();
        if (expiryMs > 0) {
            redisTemplate.opsForValue().set("blacklist:" + token, "revoked",
                    expiryMs, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isTokenRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
    }

    // ─── Validation & Extraction ───

    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (isTokenRevoked(token)) return false;
        final String username = extractUsername(token);
        return username != null && username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        if (claims.getSubject() != null) return claims.getSubject();
        if (claims.get("email") != null) return claims.get("email", String.class);
        if (claims.get("phone") != null) return claims.get("phone", String.class);
        return null;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    // ─── Private helpers ───

    private String buildToken(Map<String, Object> claims, String subject, long expiryMs) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}