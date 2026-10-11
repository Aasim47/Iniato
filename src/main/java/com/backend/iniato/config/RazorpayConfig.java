package com.backend.iniato.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Razorpay wiring for driver dues payments.
 *
 * <p>The client bean only exists when {@code razorpay.enabled=true}, so local
 * development and CI boot without credentials; {@code DuesPaymentService}
 * reports a clear error instead of failing at startup.
 *
 * <p>Keys come from the environment — see {@code application.properties}. They
 * must never be committed.
 */
@Configuration
@Getter
public class RazorpayConfig {

    @Value("${razorpay.enabled:false}")
    private boolean enabled;

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    /**
     * @return a live client, or {@code null} when Razorpay is switched off or
     *         unconfigured — callers must handle absence
     */
    @Bean
    public RazorpayClient razorpayClient() {
        if (!enabled || keyId.isBlank() || keySecret.isBlank()) {
            return null;
        }
        try {
            return new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new IllegalStateException(
                    "razorpay.enabled=true but the credentials were rejected", e);
        }
    }
}
