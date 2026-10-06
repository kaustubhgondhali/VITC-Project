package com.vitc.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Minimal Razorpay REST client built on the JDK HTTP client + Jackson, so no
 * new Maven dependency (and no dependency conflict) is introduced.
 *
 * <p>Credentials are always passed in by the caller — nothing is cached and
 * nothing is hard-coded.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayClient {

    private static final String BASE = "https://api.razorpay.com/v1";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Lightweight credential check used by "Test connection". */
    public void ping(String keyId, String keySecret) {
        JsonNode ignored = send("GET", "/payments?count=1", null, keyId, keySecret);
        log.debug("Razorpay ping ok ({} keys)", ignored == null ? 0 : ignored.size());
    }

    /** Creates a Razorpay order; amount is in major units (INR rupees). */
    public JsonNode createOrder(String keyId, String keySecret, String receipt, BigDecimal amount, String currency) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).longValueExact());
        body.put("currency", currency == null ? "INR" : currency);
        body.put("receipt", receipt);
        body.put("payment_capture", 1);
        return send("POST", "/orders", body, keyId, keySecret);
    }

    public JsonNode fetchPayment(String keyId, String keySecret, String paymentId) {
        return send("GET", "/payments/" + paymentId, null, keyId, keySecret);
    }

    public JsonNode refund(String keyId, String keySecret, String paymentId, BigDecimal amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (amount != null) {
            body.put("amount", amount.setScale(2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).longValueExact());
        }
        return send("POST", "/payments/" + paymentId + "/refund", body, keyId, keySecret);
    }

    /** HMAC-SHA256 hex digest — used for both checkout and webhook signatures. */
    public static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute the payment signature", e);
        }
    }

    /** Constant-time comparison to avoid leaking signature information. */
    public static boolean signaturesMatch(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != actual.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) {
            diff |= expected.charAt(i) ^ actual.charAt(i);
        }
        return diff == 0;
    }

    /* ------------------------------------------------------------------ */

    private JsonNode send(String method, String path, Object body, String keyId, String keySecret) {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new BadRequestException("Razorpay credentials are missing");
        }
        try {
            String auth = Base64.getEncoder().encodeToString(
                    (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .method(method, publisher)
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = response.body() == null || response.body().isBlank()
                    ? mapper.createObjectNode()
                    : mapper.readTree(response.body());
            if (response.statusCode() >= 400) {
                String message = json.path("error").path("description").asText("");
                if (response.statusCode() == 401) {
                    throw new BadRequestException("Razorpay rejected the credentials (401 Unauthorized)");
                }
                throw new BadRequestException(message.isBlank()
                        ? "Razorpay request failed (HTTP " + response.statusCode() + ")"
                        : message);
            }
            return json;
        } catch (BadRequestException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new BadRequestException("Razorpay did not respond in time. Please try again.");
        } catch (java.io.IOException e) {
            throw new BadRequestException("Razorpay is unreachable right now. Please try again later.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Razorpay request was interrupted");
        } catch (Exception e) {
            log.warn("Unexpected Razorpay error on {} {}: {}", method, path, e.toString());
            throw new BadRequestException("Unexpected payment gateway error");
        }
    }
}
