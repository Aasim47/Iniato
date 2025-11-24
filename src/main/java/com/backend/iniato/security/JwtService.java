package com.backend.iniato.security;

import com.backend.iniato.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
public class JwtService {

    // 1. Get secret key from application.properties
    // You MUST set this in application.properties!
    // E.g.: jwt.secret=aVeryLongAndSecureSecretKeyForIniatoAppThatIsAtLeast256Bits
    @Value("${jwt.secret}")
    private String SECRET_KEY;

    private static final long JWT_EXPIRATION_MS = 1000 * 60 * 60 * 24;

    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    private static final long DEFAULT_EXPIRY_MS = 1000L * 60 * 60 * 24 * 7;


    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);

        if (claims.getSubject() != null) {
            return claims.getSubject();
        }

        if (claims.get("email") != null) {
            return claims.get("email", String.class);
        }
        if (claims.get("phone") != null) {
            return claims.get("phone", String.class);
        }

        return null;
    }


    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();

        // put email or phone in claims
        if (userDetails.getUsername() != null) {
            claims.put("email", userDetails.getUsername());
        } else if (userDetails instanceof User u) {
            claims.put("phone", u.getPhoneNumber());
        }

        String subject = (userDetails.getUsername() != null) ? userDetails.getUsername() :
                (userDetails instanceof User u ? u.getPhoneNumber() : "unknown");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }


    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername()) // user's email
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    // --- Private Helper Methods ---

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
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


    public void revokeToken(String token) {
        blacklistedTokens.add(token);
    }
}