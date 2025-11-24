package com.backend.iniato.services;

import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.verify-service-sid}")
    private String verifyServiceSid;

    private final Map<String, OtpVerification> verifiedNumbers = new ConcurrentHashMap<>();

    private static final long OTP_VALIDITY_DURATION_MS = 5 * 60 * 1000;

    private final Map<String, Instant> otpRequestTimestamps = new ConcurrentHashMap<>();


    private static final long OTP_REQUEST_COOLDOWN_MS = 60 * 1000;


    public void sendOtp(String phoneNumber) {
        Instant lastRequestTime = otpRequestTimestamps.get(phoneNumber);
        if (lastRequestTime != null &&
                Instant.now().isBefore(lastRequestTime.plusMillis(OTP_REQUEST_COOLDOWN_MS))) {
            throw new IllegalStateException("Please wait a minute before requesting another OTP.");
        }

        Twilio.init(accountSid, authToken);
        Verification verification = Verification.creator(
                verifyServiceSid,
                phoneNumber,
                "sms"
        ).create();

        otpRequestTimestamps.put(phoneNumber, Instant.now());
        System.out.println("OTP sent to " + phoneNumber + ", SID: " + verification.getSid());
    }

    public boolean verifyOtp(String phoneNumber, String otp) {
        Twilio.init(accountSid, authToken);

        VerificationCheck verificationCheck = VerificationCheck.creator(
                verifyServiceSid,
                otp
        ).setTo(phoneNumber).create();

        boolean approved = "approved".equals(verificationCheck.getStatus());

        if (approved) {
            verifiedNumbers.put(phoneNumber, new OtpVerification(true, Instant.now()));
        }

        return approved;
    }

    public boolean isPhoneVerified(String phoneNumber) {
        OtpVerification verification = verifiedNumbers.get(phoneNumber);

        if (verification == null) return false;

        boolean expired = Instant.now().isAfter(verification.timestamp.plusMillis(OTP_VALIDITY_DURATION_MS));

        if (expired) {
            verifiedNumbers.remove(phoneNumber);
            return false;
        }

        return verification.verified;
    }

    public void clearVerification(String phoneNumber) {
        verifiedNumbers.remove(phoneNumber);
    }

    private record OtpVerification(boolean verified, Instant timestamp) {}
}
