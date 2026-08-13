package com.adam.restaurantoperations.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.adam.restaurantoperations.audit.PaymentAuditService;
import com.adam.restaurantoperations.orders.OrderEntity;
import com.adam.restaurantoperations.orders.OrderRepository;
import com.adam.restaurantoperations.orders.OrderStatus;
import com.adam.restaurantoperations.payments.PaymentDtos.PaymentRequest;
import com.adam.restaurantoperations.payments.PaymentDtos.PaymentResponse;
import com.adam.restaurantoperations.payments.PaymentDtos.PaymentSummaryResponse;
import com.adam.restaurantoperations.payments.PaymentDtos.ReconciliationRequest;
import com.adam.restaurantoperations.payments.PaymentDtos.ReconciliationResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final int MONEY_SCALE = 2;
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}");
    private static final Set<String> SORTS =
            Set.of("receivedAt", "amount", "paymentNumber", "orderNumber", "method");

    private final PaymentRepository payments;
    private final PaymentReconciliationRepository reconciliations;
    private final InvoiceRepository invoices;
    private final OrderRepository orders;
    private final PaymentNumberGenerator numbers;
    private final PaymentAuditService audit;

    public PaymentService(
            PaymentRepository payments,
            PaymentReconciliationRepository reconciliations,
            InvoiceRepository invoices,
            OrderRepository orders,
            PaymentNumberGenerator numbers,
            PaymentAuditService audit) {
        this.payments = payments;
        this.reconciliations = reconciliations;
        this.invoices = invoices;
        this.orders = orders;
        this.numbers = numbers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(
            PaymentMethod method,
            Boolean reconciled,
            Long orderId,
            Instant receivedFrom,
            Instant receivedTo,
            String search,
            String sortBy,
            Sort.Direction direction) {
        validateRange(receivedFrom, receivedTo);
        String field = SORTS.contains(sortBy) ? sortBy : "receivedAt";
        Sort sort = "orderNumber".equals(field)
                ? Sort.by(direction, "order.orderNumber").and(Sort.by(Sort.Direction.ASC, "id"))
                : Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
        return payments.findAll(filters(method, orderId, receivedFrom, receivedTo, search), sort).stream()
                .filter(payment -> reconciled == null
                        || reconciliations.findByPaymentId(payment.getId()).isPresent() == reconciled)
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return response(findPayment(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forOrder(Long orderId) {
        findOrder(orderId);
        return payments.findByOrderIdOrderByReceivedAtAscIdAsc(orderId).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentSummaryResponse summary(Long orderId) {
        return summary(findOrder(orderId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentRecordResult record(
            Long orderId,
            String suppliedIdempotencyKey,
            PaymentRequest request,
            Long actorId,
            String ipAddress) {
        String key = idempotencyKey(suppliedIdempotencyKey);
        BigDecimal amount = money(request.amount());
        String externalReference = optional(request.externalReference());
        validateReferencePolicy(request.method(), externalReference);

        OrderEntity order = lockOrder(orderId);
        PaymentEntity existing = payments.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            verifyIdempotentMatch(existing, orderId, amount, request.method(), externalReference);
            return new PaymentRecordResult(response(existing), false);
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw PaymentManagementException.conflict("Payments may be recorded only for COMPLETED orders");
        }
        if (externalReference != null && payments.findByExternalReference(externalReference).isPresent()) {
            throw PaymentManagementException.conflict("External payment reference is already recorded");
        }
        BigDecimal paid = paid(orderId);
        BigDecimal outstanding = money(order.getTotal().subtract(paid));
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentManagementException.conflict("Order is already fully paid");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw PaymentManagementException.conflict("Payment exceeds the outstanding amount");
        }

        PaymentEntity payment = savePayment(new PaymentEntity(
                uniquePaymentNumber(),
                order,
                key,
                request.method(),
                amount,
                externalReference,
                actorId));
        audit.record(
                "PAYMENT_RECORDED",
                actorId,
                "PAYMENT",
                payment.getId(),
                Map.of(
                        "paymentNumber", payment.getPaymentNumber(),
                        "orderId", orderId,
                        "orderNumber", order.getOrderNumber(),
                        "method", request.method().name(),
                        "amount", amount.toPlainString()),
                ipAddress);
        return new PaymentRecordResult(response(payment), true);
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse reconciliation(Long paymentId) {
        findPayment(paymentId);
        return reconciliations.findByPaymentId(paymentId)
                .map(ReconciliationResponse::from)
                .orElseThrow(() -> PaymentManagementException.notFound("Payment reconciliation"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconciliationResult reconcile(
            Long paymentId,
            ReconciliationRequest request,
            Long actorId,
            String ipAddress) {
        PaymentEntity payment;
        try {
            payment = payments.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> PaymentManagementException.notFound("Payment"));
        } catch (PessimisticLockingFailureException exception) {
            throw PaymentManagementException.contention();
        }
        PaymentReconciliationEntity existing = reconciliations.findByPaymentId(paymentId).orElse(null);
        if (existing != null) {
            return new ReconciliationResult(ReconciliationResponse.from(existing), false);
        }
        PaymentReconciliationEntity saved;
        try {
            saved = reconciliations.saveAndFlush(new PaymentReconciliationEntity(
                    payment,
                    optional(request.reconciliationReference()),
                    actorId));
        } catch (DataIntegrityViolationException exception) {
            throw PaymentManagementException.contention();
        }
        audit.record(
                "PAYMENT_RECONCILED",
                actorId,
                "PAYMENT_RECONCILIATION",
                saved.getId(),
                Map.of("paymentId", paymentId, "orderId", payment.getOrder().getId()),
                ipAddress);
        return new ReconciliationResult(ReconciliationResponse.from(saved), true);
    }

    PaymentSummaryResponse summary(OrderEntity order) {
        BigDecimal paid = paid(order.getId());
        BigDecimal outstanding = money(order.getTotal().subtract(paid));
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
            throw PaymentManagementException.conflict("Recorded payments exceed the order total");
        }
        InvoiceEntity invoice = invoices.findByOrderId(order.getId()).orElse(null);
        return new PaymentSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                "EUR",
                money(order.getTotal()).toPlainString(),
                paid.toPlainString(),
                outstanding.toPlainString(),
                state(paid, outstanding),
                invoice == null ? null : invoice.getId(),
                invoice == null ? null : invoice.getInvoiceNumber(),
                payments.findByOrderIdOrderByReceivedAtAscIdAsc(order.getId()).stream()
                        .map(this::response)
                        .toList());
    }

    BigDecimal paid(Long orderId) {
        return money(payments.successfulTotal(orderId));
    }

    private PaymentResponse response(PaymentEntity payment) {
        ReconciliationResponse reconciliation = reconciliations.findByPaymentId(payment.getId())
                .map(ReconciliationResponse::from)
                .orElse(null);
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getOrder().getId(),
                payment.getOrder().getOrderNumber(),
                payment.getMethod(),
                payment.getStatus(),
                money(payment.getAmount()).toPlainString(),
                payment.getCurrency(),
                payment.getExternalReference(),
                payment.getReceivedAt(),
                payment.getActorUserId(),
                reconciliation);
    }

    private Specification<PaymentEntity> filters(
            PaymentMethod method,
            Long orderId,
            Instant receivedFrom,
            Instant receivedTo,
            String search) {
        return (root, query, builder) -> {
            Predicate predicate = builder.conjunction();
            if (method != null) {
                predicate = builder.and(predicate, builder.equal(root.get("method"), method));
            }
            if (orderId != null) {
                predicate = builder.and(predicate, builder.equal(root.get("order").get("id"), orderId));
            }
            if (receivedFrom != null) {
                predicate = builder.and(predicate,
                        builder.greaterThanOrEqualTo(root.get("receivedAt"), receivedFrom));
            }
            if (receivedTo != null) {
                predicate = builder.and(predicate, builder.lessThan(root.get("receivedAt"), receivedTo));
            }
            if (hasText(search)) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("paymentNumber")), term),
                        builder.like(builder.lower(root.get("order").get("orderNumber")), term),
                        builder.like(builder.lower(root.get("externalReference")), term)));
            }
            return predicate;
        };
    }

    private void verifyIdempotentMatch(
            PaymentEntity existing,
            Long orderId,
            BigDecimal amount,
            PaymentMethod method,
            String externalReference) {
        if (!existing.getOrder().getId().equals(orderId)
                || existing.getAmount().compareTo(amount) != 0
                || existing.getMethod() != method
                || !Objects.equals(existing.getExternalReference(), externalReference)) {
            throw PaymentManagementException.conflict(
                    "Idempotency key was already used for different payment data");
        }
    }

    private void validateReferencePolicy(PaymentMethod method, String externalReference) {
        if (method.requiresExternalReference() && externalReference == null) {
            throw PaymentManagementException.badRequest(
                    "External reference is required for CARD and BANK_TRANSFER payments");
        }
    }

    private PaymentEntity savePayment(PaymentEntity payment) {
        try {
            return payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw PaymentManagementException.conflict(
                    "Payment data conflicts with an existing settlement; reload and retry");
        }
    }

    private OrderEntity lockOrder(Long id) {
        try {
            return orders.findByIdForUpdate(id)
                    .orElseThrow(() -> PaymentManagementException.notFound("Order"));
        } catch (PessimisticLockingFailureException exception) {
            throw PaymentManagementException.contention();
        }
    }

    private OrderEntity findOrder(Long id) {
        return orders.findById(id).orElseThrow(() -> PaymentManagementException.notFound("Order"));
    }

    private PaymentEntity findPayment(Long id) {
        return payments.findById(id).orElseThrow(() -> PaymentManagementException.notFound("Payment"));
    }

    private String uniquePaymentNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = numbers.generate();
            if (!payments.existsByPaymentNumber(candidate)) {
                return candidate;
            }
        }
        throw PaymentManagementException.conflict("Payment number conflict; retry");
    }

    private String idempotencyKey(String value) {
        String normalized = hasText(value) ? value.trim() : "";
        if (!IDEMPOTENCY_KEY.matcher(normalized).matches()) {
            throw PaymentManagementException.badRequest("Idempotency-Key must contain 8 to 64 safe characters");
        }
        return normalized;
    }

    private PaymentState state(BigDecimal paid, BigDecimal outstanding) {
        if (paid.compareTo(BigDecimal.ZERO) == 0) {
            return PaymentState.UNPAID;
        }
        return outstanding.compareTo(BigDecimal.ZERO) == 0
                ? PaymentState.PAID
                : PaymentState.PARTIALLY_PAID;
    }

    private void validateRange(Instant start, Instant end) {
        if (start != null && end != null && !start.isBefore(end)) {
            throw PaymentManagementException.badRequest("Start of range must precede end of range");
        }
    }

    private BigDecimal money(BigDecimal value) {
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw PaymentManagementException.badRequest("Money values support at most two decimal places");
        }
    }

    private String optional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PaymentRecordResult(PaymentResponse payment, boolean created) {
    }

    public record ReconciliationResult(ReconciliationResponse reconciliation, boolean created) {
    }
}
