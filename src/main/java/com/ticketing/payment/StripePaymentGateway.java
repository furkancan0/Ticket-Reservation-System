package com.ticketing.payment;

import com.ticketing.domain.enums.PaymentProvider;
import com.ticketing.exception.PaymentDeclinedException;
import com.ticketing.exception.PaymentRetryableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentGateway implements PaymentGateway {

    private final RestTemplate restTemplate;

    @Value("${payment.stripe.api-key}")
    private String apiKey;

    @Value("${payment.stripe.base-url}")
    private String baseUrl;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    @Retry(name = "stripe")
    @CircuitBreaker(name = "stripe", fallbackMethod = "stripeFallback")
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing Stripe payment: order={} amount={}", request.getOrderId(), request.getAmount());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(apiKey);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("amount",            toCents(request.getAmount()));
            body.add("currency",          request.getCurrency() != null ? request.getCurrency() : "usd");
            body.add("source",            request.getPaymentToken());
            body.add("description",       request.getDescription());
            body.add("receipt_email",     request.getCustomerEmail());
            body.add("metadata[order_id]", request.getOrderId().toString());

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/charges", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> rb     = response.getBody();
            String chargeId  = rb != null ? (String) rb.get("id") : null;
            String status    = rb != null ? (String) rb.get("status") : "unknown";
            boolean ok       = "succeeded".equals(status);

            log.info("Stripe charge {} → status={}", chargeId, status);
            return PaymentResult.builder()
                    .success(ok)
                    .providerTransactionId(chargeId)
                    .status(ok ? "SUCCESS" : "FAILED")
                    .rawResponse(rb != null ? rb.toString() : null)
                    .build();

        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 402 || code == 422) {
                // Retry to skip
                throw new PaymentDeclinedException("Stripe declined: " + e.getMessage());
            }
            log.error("Stripe client error {}: {}", code, e.getMessage());
            return PaymentResult.builder()
                    .success(false).status("FAILED")
                    .errorMessage("Stripe error " + code + ": " + e.getMessage())
                    .rawResponse(e.getResponseBodyAsString())
                    .build();

        } catch (ResourceAccessException e) {
            // Network / timeout – Retry
            throw new PaymentRetryableException("Stripe network error: " + e.getMessage(), e);
        }
    }

    public PaymentResult stripeFallback(PaymentRequest request, Throwable t) {
        log.error("Stripe circuit OPEN for order={}: {}", request.getOrderId(), t.getMessage());
        return PaymentResult.builder()
                .success(false).status("CIRCUIT_OPEN")
                .errorMessage("Payment service temporarily unavailable. Please try again shortly.")
                .build();
    }

    private String toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString();
    }
}
