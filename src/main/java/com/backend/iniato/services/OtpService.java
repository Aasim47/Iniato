package com.backend.iniato.services;

import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.verify-service-sid}")
    private String verifyServiceSid;


    private final Map<String, Boolean> verifiedNumbers = new ConcurrentHashMap<>();

    public void sendOtp(String phoneNumber) {
        Twilio.init(accountSid, authToken);

        Verification verification = Verification.creator(
                        verifyServiceSid,
                        phoneNumber,
                        "sms")
                .create();

        System.out.println("OTP sent to " + phoneNumber + ", SID: " + verification.getSid());
    }

    public boolean verifyOtp(String phoneNumber, String otp) {
        Twilio.init(accountSid, authToken);

        VerificationCheck verificationCheck = VerificationCheck.creator(
                        verifyServiceSid,
                        otp
                ).setTo(phoneNumber)
                .create();

        boolean isValid = verificationCheck.getValid();
        if (isValid) {
            verifiedNumbers.put(phoneNumber, true);
        }
        return "approved".equals(verificationCheck.getStatus());
    }


    public boolean isPhoneVerified(String phoneNumber) {
        return verifiedNumbers.getOrDefault(phoneNumber, false);
    }

    public void clearVerification(String phoneNumber) {
        verifiedNumbers.remove(phoneNumber);
    }
}
