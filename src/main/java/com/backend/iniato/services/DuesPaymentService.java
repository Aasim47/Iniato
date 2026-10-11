package com.backend.iniato.services;

import com.backend.iniato.config.RazorpayConfig;
import com.backend.iniato.dto.DuesOrderResponse;
import com.backend.iniato.dto.DuesStatusResponse;
import com.backend.iniato.entity.DuesPayment;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.DuesPaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Takes the driver's payment against their outstanding dues.
 *
 * <p>Confirmation arrives twice by design — once when the app calls
 * {@code verify} after checkout, once from Razorpay's webhook — because either
 * can be lost: the app can be killed mid-payment, and webhooks can be delayed.
 * Both routes funnel into {@link #settle}, which is idempotent on the Razorpay
 * payment id, so whichever lands first clears the dues and the other is a
 * no-op.
 */
@Service
public class DuesPaymentService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100);

    private final DuesPaymentRepository duesPaymentRepository;
    private final DriverDuesService driverDuesService;
    private final RazorpayConfig razorpayConfig;
    private final RazorpayClient razorpayClient;

    public DuesPaymentService(DuesPaymentRepository duesPaymentRepository,
                              DriverDuesService driverDuesService,
                              RazorpayConfig razorpayConfig,
                              @Autowired(required = false) RazorpayClient razorpayClient) {
        this.duesPaymentRepository = duesPaymentRepository;
        this.driverDuesService = driverDuesService;
        this.razorpayConfig = razorpayConfig;
        this.razorpayClient = razorpayClient;
    }

    /**
     * Open a Razorpay order for what the driver owes.
     *
     * @param amount how much to pay, or {@code null} to clear the full balance
     */
    @Transactional
    public DuesOrderResponse createOrder(User driver, BigDecimal amount) {
        requireGateway();

        BigDecimal balance = driverDuesService.balanceFor(driver);
        if (balance.signum() <= 0) {
            throw new IllegalStateException("You have no pending dues to pay.");
        }

        // Never let a driver pay more than they owe — an overpayment would sit
        // as an unusable credit.
        BigDecimal toPay = money(amount == null ? balance : amount.min(balance));
        if (toPay.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        long paise = toPay.multiply(PAISE_PER_RUPEE).longValueExact();

        JSONObject request = new JSONObject();
        request.put("amount", paise);
        request.put("currency", razorpayConfig.getCurrency());
        request.put("receipt", "dues_" + driver.getId() + "_" + System.currentTimeMillis());

        final Order order;
        try {
            order = razorpayClient.orders.create(request);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not reach the payment gateway. Please try again.", e);
        }

        String orderId = order.get("id");
        duesPaymentRepository.save(DuesPayment.builder()
                .driver(driver)
                .amount(toPay)
                .balanceBefore(balance)
                .razorpayOrderId(orderId)
                .status(DuesPayment.Status.CREATED)
                .build());

        return DuesOrderResponse.builder()
                .orderId(orderId)
                .amountPaise(paise)
                .amount(toPay)
                .currency(razorpayConfig.getCurrency())
                .razorpayKeyId(razorpayConfig.getKeyId())
                .build();
    }

    /**
     * Confirm a payment the driver app completed, after checking Razorpay's
     * signature over {@code orderId|paymentId}.
     */
    @Transactional
    public DuesStatusResponse verifyAndSettle(User driver, String orderId,
                                              String paymentId, String signature) {
        requireGateway();

        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_order_id", orderId);
        attributes.put("razorpay_payment_id", paymentId);
        attributes.put("razorpay_signature", signature);

        boolean valid;
        try {
            valid = Utils.verifyPaymentSignature(attributes, razorpayConfig.getKeySecret());
        } catch (Exception e) {
            valid = false;
        }
        if (!valid) {
            throw new SecurityException("Payment signature could not be verified.");
        }

        settle(orderId, paymentId, signature);
        return driverDuesService.statusFor(driver);
    }

    /**
     * Handle Razorpay's server-to-server notification.
     *
     * <p>The signature is an HMAC over the <em>raw</em> body, so the payload
     * must be the exact bytes received — never a re-serialised object.
     *
     * @return true when the event was accepted; unknown events are ignored
     */
    @Transactional
    public boolean handleWebhook(String rawPayload, String signature) {
        String secret = razorpayConfig.getWebhookSecret();
        if (secret == null || secret.isBlank()) return false;

        try {
            if (!Utils.verifyWebhookSignature(rawPayload, signature, secret)) {
                throw new SecurityException("Webhook signature could not be verified.");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Webhook signature could not be verified.", e);
        }

        JSONObject payload = new JSONObject(rawPayload);
        String event = payload.optString("event");
        if (!"payment.captured".equals(event) && !"order.paid".equals(event)) {
            // Razorpay sends many event types; acknowledge and ignore the rest
            // so it does not retry.
            return false;
        }

        JSONObject entity = payload
                .optJSONObject("payload");
        if (entity == null) return false;
        JSONObject payment = entity.optJSONObject("payment");
        if (payment == null) return false;
        JSONObject paymentEntity = payment.optJSONObject("entity");
        if (paymentEntity == null) return false;

        String orderId = paymentEntity.optString("order_id", null);
        String paymentId = paymentEntity.optString("id", null);
        if (orderId == null || paymentId == null) return false;

        settle(orderId, paymentId, null);
        return true;
    }

    /**
     * Mark the order paid and clear the driver's dues.
     * <p>
     * Safe to call repeatedly: a payment already marked {@code PAID} returns
     * immediately, and the ledger's dedupe key is the backstop.
     */
    private void settle(String orderId, String paymentId, String signature) {
        Optional<DuesPayment> found = duesPaymentRepository.findByRazorpayOrderId(orderId);
        if (found.isEmpty()) return;

        DuesPayment payment = found.get();
        if (payment.getStatus() == DuesPayment.Status.PAID) return;

        payment.setRazorpayPaymentId(paymentId);
        if (signature != null) payment.setRazorpaySignature(signature);
        payment.setStatus(DuesPayment.Status.PAID);
        payment.setPaidAt(LocalDateTime.now());
        duesPaymentRepository.save(payment);

        driverDuesService.recordPayment(
                payment.getDriver(), payment.getAmount(), orderId, paymentId);
    }

    private void requireGateway() {
        if (razorpayClient == null) {
            throw new IllegalStateException(
                    "Online payments are not available right now. Please try again later.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
