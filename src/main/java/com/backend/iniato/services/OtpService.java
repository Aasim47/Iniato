package com.backend.iniato.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Self-contained OTP service.
 * <p>
 * OTPs are generated locally (6-digit, SecureRandom), stored in Redis,
 * and delivered via the Fast2SMS HTTP API — no Twilio dependency.
 * <p>
 * Set {@code otp.dev-mode=true} in application.properties to skip the
 * actual SMS send and just log the OTP (useful during local development).
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    // Redis key prefixes
    private static final String OTP_KEY       = "otp:code:";      // otp:code:<phone>    → 6-digit code
    private static final String COOLDOWN_KEY  = "otp:cooldown:";  // otp:cooldown:<phone> → rate-limit flag
    private static final String VERIFIED_KEY  = "otp:verified:";  // otp:verified:<phone> → post-verify flag

    // TTLs
    private static final long OTP_TTL_SECONDS      = 5  * 60;  // OTP valid for 5 min
    private static final long COOLDOWN_TTL_SECONDS = 60;        // 60-sec resend cooldown
    private static final long VERIFIED_TTL_SECONDS = 5  * 60;  // verified flag lasts 5 min

    @Value("${fast2sms.api-key}")
    private String fast2SmsApiKey;

    /** When true, OTP is only logged — no SMS is sent. Safe for local dev. */
    @Value("${otp.dev-mode:false}")
    private boolean devMode;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;

    @Autowired
    public OtpService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate  = new RestTemplate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate a 6-digit OTP, store it in Redis, and send it via SMS
     * (or log it if dev-mode is on).
     *
     * @throws IllegalStateException if the phone is still in cooldown
     */
    public void sendOtp(String phoneNumber) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY + phoneNumber))) {
            throw new IllegalStateException(
                    "Please wait a minute before requesting another OTP.");
        }

        String otp = generateOtp();

        // Store OTP in Redis
        redisTemplate.opsForValue().set(
                OTP_KEY + phoneNumber, otp, OTP_TTL_SECONDS, TimeUnit.SECONDS);

        // Set resend cooldown
        redisTemplate.opsForValue().set(
                COOLDOWN_KEY + phoneNumber, "1", COOLDOWN_TTL_SECONDS, TimeUnit.SECONDS);

        if (devMode) {
            // ── DEV MODE: just print — no SMS cost ──────────────────────────
            log.warn("⚡ [DEV MODE] OTP for {} → {}", phoneNumber, otp);
        } else {
            sendViaSms(phoneNumber, otp);
        }
    }

    /**
     * Verify the OTP submitted by the user.
     * Marks the phone as verified in Redis on success.
     */
    public boolean verifyOtp(String phoneNumber, String submittedOtp) {
        String stored = (String) redisTemplate.opsForValue().get(OTP_KEY + phoneNumber);

        if (stored == null) {
            log.debug("OTP expired or never issued for {}", phoneNumber);
            return false;
        }

        if (!stored.equals(submittedOtp.trim())) {
            log.debug("OTP mismatch for {}: expected {}, got {}", phoneNumber, stored, submittedOtp);
            return false;
        }

        // Consume the OTP immediately (single-use)
        redisTemplate.delete(OTP_KEY + phoneNumber);

        // Mark phone as verified for 5 min (registration window)
        redisTemplate.opsForValue().set(
                VERIFIED_KEY + phoneNumber, "true", VERIFIED_TTL_SECONDS, TimeUnit.SECONDS);

        return true;
    }

    /** Returns true if the phone passed OTP verification within the last 5 min. */
    public boolean isPhoneVerified(String phoneNumber) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(VERIFIED_KEY + phoneNumber));
    }

    /** Clears the verified flag after registration is complete. */
    public void clearVerification(String phoneNumber) {
        redisTemplate.delete(VERIFIED_KEY + phoneNumber);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Generates a cryptographically random 6-digit code (100000–999999). */
    private String generateOtp() {
        int code = 100_000 + new SecureRandom().nextInt(900_000);
        return String.valueOf(code);
    }

    /**
     * Send the OTP via Fast2SMS OTP route.
     *
     * Fast2SMS docs: https://docs.fast2sms.com
     *
     * Route  : "otp"  (DLT-free transactional template — lowest cost)
     * Method : GET with query params  OR  POST with JSON body (we use POST)
     *
     * To use your own DLT-registered sender and template instead, switch
     * route to "dlt" and supply sender_id + message params.
     */
    private void sendViaSms(String phoneNumber, String otp) {
        // Fast2SMS expects the number WITHOUT the country code prefix in the API field
        // but the number itself should be a 10-digit Indian mobile number.
        // Strip leading +91 or 91 if present.
        String mobile = phoneNumber.replaceAll("^\\+?91", "");

        String url = "https://www.fast2sms.com/dev/bulkV2";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", fast2SmsApiKey);

        // Fast2SMS OTP route body
        String body = String.format(
                "{\"route\":\"otp\",\"variables_values\":\"%s\",\"numbers\":\"%s\"}",
                otp, mobile);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("OTP sent to {} via Fast2SMS", phoneNumber);
            } else {
                log.error("Fast2SMS error {}: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to send OTP. Please try again.");
            }
        } catch (Exception e) {
            log.error("SMS send failed for {}: {}", phoneNumber, e.getMessage());
            throw new RuntimeException("Failed to send OTP. Please try again.");
        }
    }
}
