package com.adam.restaurantoperations.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {
    private PaymentDtos() {
    }

    public record PaymentRequest(
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2)
            BigDecimal amount,
            @NotNull PaymentMethod method,
            @Size(max = 120) String externalReference) {
    }

    public record ReconciliationRequest(@Size(max = 120) String reconciliationReference) {
    }

    public record ReconciliationResponse(
            Long id,
            Long paymentId,
            String reconciliationReference,
            Instant reconciledAt,
            Long actorUserId) {
        static ReconciliationResponse from(PaymentReconciliationEntity value) {
            return new ReconciliationResponse(
                    value.getId(),
                    value.getPayment().getId(),
                    value.getReconciliationReference(),
                    value.getReconciledAt(),
                    value.getActorUserId());
        }
    }

    public record PaymentResponse(
            Long id,
            String paymentNumber,
            Long orderId,
            String orderNumber,
            PaymentMethod method,
            PaymentStatus status,
            String amount,
            String currency,
            String externalReference,
            Instant receivedAt,
            Long actorUserId,
            ReconciliationResponse reconciliation) {
    }

    public record PaymentSummaryResponse(
            Long orderId,
            String orderNumber,
            String orderStatus,
            String currency,
            String orderTotal,
            String paidAmount,
            String outstandingAmount,
            PaymentState paymentState,
            Long invoiceId,
            String invoiceNumber,
            List<PaymentResponse> payments) {
    }

    public record InvoiceModifierResponse(
            Long id,
            String groupName,
            String optionName,
            String priceAdjustment,
            int displayOrder) {
    }

    public record InvoiceItemResponse(
            Long id,
            Long sourceOrderItemId,
            String itemCode,
            String itemName,
            int quantity,
            String basePrice,
            String unitTotal,
            String lineTotal,
            int displayOrder,
            List<InvoiceModifierResponse> modifiers) {
    }

    public record InvoiceResponse(
            Long id,
            String invoiceNumber,
            Long orderId,
            String orderNumber,
            String currency,
            String subtotal,
            String total,
            String paidTotal,
            Instant issuedAt,
            Long actorUserId,
            List<InvoiceItemResponse> items) {
    }
}
