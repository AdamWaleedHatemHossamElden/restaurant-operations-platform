package com.adam.restaurantoperations.payments;

import java.util.Set;
import java.util.stream.Collectors;

import com.adam.restaurantoperations.payments.PaymentDtos.PaymentRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSecurityBoundaryTest {
    @Test
    void paymentRequestAcceptsOnlyOperationalSettlementFields() {
        Set<String> fields = java.util.Arrays.stream(PaymentRequest.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("amount", "method", "externalreference");
        assertThat(fields).noneMatch(name ->
                name.contains("cardnumber")
                        || name.contains("pan")
                        || name.contains("cvv")
                        || name.contains("cvc")
                        || name.contains("expiry")
                        || name.contains("pin")
                        || name.contains("track"));
    }

    @Test
    void onlyTruthfulConfirmedPaymentStatusExists() {
        assertThat(PaymentStatus.values()).containsExactly(PaymentStatus.SUCCEEDED);
    }
}
