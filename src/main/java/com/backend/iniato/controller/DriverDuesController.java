package com.backend.iniato.controller;

import com.backend.iniato.dto.DuesLedgerEntry;
import com.backend.iniato.dto.DuesOrderResponse;
import com.backend.iniato.dto.DuesStatusResponse;
import com.backend.iniato.dto.DuesVerifyRequest;
import com.backend.iniato.entity.User;
import com.backend.iniato.services.DriverDuesService;
import com.backend.iniato.services.DuesPaymentService;
import com.backend.iniato.services.UserLookupService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * What the driver owes Iniato, and how they settle it.
 *
 * <p>Every endpoint is explicitly authorised: {@code SecurityConfig} leaves
 * {@code /api/**} permitted at the filter chain, so {@code @PreAuthorize} is
 * the only thing standing between these and the open internet. The webhook is
 * the deliberate exception — Razorpay cannot present a JWT, so it is
 * authenticated by HMAC signature instead.
 */
@RestController
@RequestMapping("/api/driver/dues")
public class DriverDuesController {

    private final DriverDuesService driverDuesService;
    private final DuesPaymentService duesPaymentService;
    private final UserLookupService userLookupService;

    public DriverDuesController(DriverDuesService driverDuesService,
                                DuesPaymentService duesPaymentService,
                                UserLookupService userLookupService) {
        this.driverDuesService = driverDuesService;
        this.duesPaymentService = duesPaymentService;
        this.userLookupService = userLookupService;
    }

    /** The authenticated driver — shared cached lookup, one query at most. */
    private User currentDriver() {
        return userLookupService.currentUser();
    }

    /** Current balance, threshold, and whether the driver is blocked. */
    @GetMapping
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<DuesStatusResponse> getDues() {
        return ResponseEntity.ok(driverDuesService.statusFor(currentDriver()));
    }

    /**
     * Ledger history, newest first — every accrual and payment.
     *
     * <p>The rows are flattened by the service, inside its transaction. Doing
     * it here meant dereferencing the lazy {@code settlement} association after
     * the session had closed, which worked only while open-in-view was on.
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<List<DuesLedgerEntry>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(driverDuesService
                .ledgerEntriesFor(currentDriver(), PageRequest.of(page, Math.min(size, 100)))
                .getContent());
    }

    /**
     * Open a Razorpay order for the outstanding dues.
     * Body may carry {@code amount}; absent means pay the lot.
     */
    @PostMapping("/pay-order")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<DuesOrderResponse> createPayOrder(
            @RequestBody(required = false) Map<String, Object> body) {

        BigDecimal amount = null;
        if (body != null && body.get("amount") != null) {
            amount = new BigDecimal(body.get("amount").toString());
        }
        return ResponseEntity.ok(duesPaymentService.createOrder(currentDriver(), amount));
    }

    /** Confirm a completed checkout and clear the dues. */
    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<DuesStatusResponse> verifyPayment(
            @Valid @RequestBody DuesVerifyRequest request) {

        return ResponseEntity.ok(duesPaymentService.verifyAndSettle(
                currentDriver(),
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()));
    }

    /**
     * Razorpay's server-to-server confirmation — the safety net for a driver
     * who pays and then loses connectivity before the app can call verify.
     *
     * <p>Deliberately unauthenticated: authenticity comes from the HMAC
     * signature over the raw body, which is why the payload is taken as a
     * String rather than a bound object.
     *
     * <p>Always answers 200 so Razorpay does not retry events we ignore.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (signature == null || signature.isBlank()) {
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "missing signature"));
        }
        try {
            boolean handled = duesPaymentService.handleWebhook(payload, signature);
            return ResponseEntity.ok(Map.of("status", handled ? "processed" : "ignored"));
        } catch (SecurityException rejected) {
            // A bad signature is the one case worth telling the caller about.
            return ResponseEntity.status(401).body(Map.of("status", "invalid signature"));
        }
    }
}
