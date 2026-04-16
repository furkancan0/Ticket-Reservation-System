package com.ticketing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaypalPaymentGateway implements PaymentGateway {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.paypal.client-id}")
    private String clientId;

    @Value("${payment.paypal.client-secret}")
    private String clientSecret;

    @Value("${payment.paypal.base-url}")
    private String baseUrl;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYPAL;
    }

    @Override
    @Retry(name = "paypal")
    @CircuitBreaker(name = "paypal", fallbackMethod = "paypalFallback")
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing PayPal payment: order={} amount={}", request.getOrderId(), request.getAmount());

        try {
            String accessToken = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            headers.set("PayPal-Request-Id", request.getOrderId().toString());

            Map<String, Object> orderBody = Map.of(
                "intent", "CAPTURE",
                "purchase_units", new Object[]{
                    Map.of(
                        "reference_id", request.getOrderId().toString(),
                        "amount", Map.of(
                            "currency_code", request.getCurrency() != null ? request.getCurrency() : "USD",
                            "value", request.getAmount().toPlainString()
                        ),
                        "description", request.getDescription()
                    )
                },
                "payment_source", Map.of(
                    "token", Map.of(
                        "id",   request.getPaymentToken(),
                        "type", "BILLING_AGREEMENT"
                    )
                )
            );

            ResponseEntity<String> createResp = restTemplate.exchange(
                    baseUrl + "/v2/checkout/orders", HttpMethod.POST,
                    new HttpEntity<>(orderBody, headers), String.class);

            JsonNode createNode = objectMapper.readTree(createResp.getBody());
            String paypalOrderId = createNode.path("id").asText();

            // Capture the order
            ResponseEntity<String> captureResp = restTemplate.exchange(
                    baseUrl + "/v2/checkout/orders/" + paypalOrderId + "/capture",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(), headers),
                    String.class);

            JsonNode captureNode = objectMapper.readTree(captureResp.getBody());
            String captureStatus = captureNode.path("status").asText();
            boolean ok = "COMPLETED".equals(captureStatus);

            String captureId = captureNode
                    .path("purchase_units").get(0)
                    .path("payments").path("captures").get(0)
                    .path("id").asText(null);

            log.info("PayPal capture {} → {}", captureId, captureStatus);
            return PaymentResult.builder()
                    .success(ok)
                    .providerTransactionId(captureId)
                    .status(ok ? "SUCCESS" : "FAILED")
                    .rawResponse(captureResp.getBody())
                    .build();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) {
                throw new PaymentDeclinedException("PayPal declined: " + e.getMessage());
            }
            log.error("PayPal client error {}: {}", e.getStatusCode(), e.getMessage());
            return PaymentResult.builder()
                    .success(false).status("FAILED")
                    .errorMessage(e.getMessage())
                    .rawResponse(e.getResponseBodyAsString())
                    .build();

        } catch (ResourceAccessException e) {
            throw new PaymentRetryableException("PayPal network error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("PayPal unexpected error for order={}", request.getOrderId(), e);
            return PaymentResult.builder()
                    .success(false).status("FAILED")
                    .errorMessage("Unexpected error: " + e.getMessage())
                    .build();
        }
    }

    public PaymentResult paypalFallback(PaymentRequest request, Throwable t) {
        log.error("PayPal circuit OPEN for order={}: {}", request.getOrderId(), t.getMessage());
        return PaymentResult.builder()
                .success(false).status("CIRCUIT_OPEN")
                .errorMessage("Payment service temporarily unavailable. Please try again shortly.")
                .build();
    }

    private String getAccessToken() {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + credentials);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/v1/oauth2/token", HttpMethod.POST,
                new HttpEntity<>("grant_type=client_credentials", headers), Map.class);

        Map<?, ?> body = resp.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new PaymentRetryableException("Failed to obtain PayPal access token", null);
        }
        return (String) body.get("access_token");
    }
}
