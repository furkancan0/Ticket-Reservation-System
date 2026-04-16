package com.ticketing.payment;

import com.ticketing.domain.enums.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayFactory {

    private final Map<PaymentProvider, PaymentGateway> gateways;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(PaymentGateway::provider, Function.identity()));
    }

    public PaymentGateway getGateway(PaymentProvider provider) {
        PaymentGateway gw = gateways.get(provider);
        if (gw == null) {
            throw new IllegalArgumentException("No payment gateway registered for provider: " + provider);
        }
        return gw;
    }
}
