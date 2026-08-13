package com.adam.restaurantoperations.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.adam.restaurantoperations.audit.PaymentAuditService;
import com.adam.restaurantoperations.orders.OrderEntity;
import com.adam.restaurantoperations.orders.OrderItemEntity;
import com.adam.restaurantoperations.orders.OrderItemModifierRepository;
import com.adam.restaurantoperations.orders.OrderItemRepository;
import com.adam.restaurantoperations.orders.OrderRepository;
import com.adam.restaurantoperations.orders.OrderStatus;
import com.adam.restaurantoperations.payments.PaymentDtos.InvoiceItemResponse;
import com.adam.restaurantoperations.payments.PaymentDtos.InvoiceModifierResponse;
import com.adam.restaurantoperations.payments.PaymentDtos.InvoiceResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {
    private static final Set<String> SORTS = Set.of("issuedAt", "invoiceNumber", "orderNumber", "total");

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository invoiceItems;
    private final InvoiceItemModifierRepository invoiceModifiers;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderItemModifierRepository orderModifiers;
    private final PaymentService paymentService;
    private final InvoiceNumberGenerator numbers;
    private final PaymentAuditService audit;

    public InvoiceService(
            InvoiceRepository invoices,
            InvoiceItemRepository invoiceItems,
            InvoiceItemModifierRepository invoiceModifiers,
            OrderRepository orders,
            OrderItemRepository orderItems,
            OrderItemModifierRepository orderModifiers,
            PaymentService paymentService,
            InvoiceNumberGenerator numbers,
            PaymentAuditService audit) {
        this.invoices = invoices;
        this.invoiceItems = invoiceItems;
        this.invoiceModifiers = invoiceModifiers;
        this.orders = orders;
        this.orderItems = orderItems;
        this.orderModifiers = orderModifiers;
        this.paymentService = paymentService;
        this.numbers = numbers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(
            String search,
            Instant issuedFrom,
            Instant issuedTo,
            String sortBy,
            Sort.Direction direction) {
        validateRange(issuedFrom, issuedTo);
        String field = SORTS.contains(sortBy) ? sortBy : "issuedAt";
        Sort sort = "orderNumber".equals(field)
                ? Sort.by(direction, "orderNumberSnapshot").and(Sort.by(Sort.Direction.ASC, "id"))
                : Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
        return invoices.findAll(filters(search, issuedFrom, issuedTo), sort).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return response(invoices.findById(id)
                .orElseThrow(() -> PaymentManagementException.notFound("Invoice")));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse forOrder(Long orderId) {
        if (!orders.existsById(orderId)) {
            throw PaymentManagementException.notFound("Order");
        }
        return response(invoices.findByOrderId(orderId)
                .orElseThrow(() -> PaymentManagementException.notFound("Invoice")));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvoiceIssueResult issue(Long orderId, Long actorId, String ipAddress) {
        OrderEntity order = lockOrder(orderId);
        InvoiceEntity existing = invoices.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return new InvoiceIssueResult(response(existing), false);
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw PaymentManagementException.conflict("Invoices may be issued only for COMPLETED orders");
        }
        BigDecimal paid = paymentService.paid(orderId);
        if (paid.compareTo(money(order.getTotal())) != 0) {
            throw PaymentManagementException.conflict("Invoice requires a fully paid order");
        }

        InvoiceEntity invoice = saveInvoice(new InvoiceEntity(
                uniqueInvoiceNumber(),
                order,
                money(order.getSubtotal()),
                money(order.getTotal()),
                paid,
                actorId));
        for (OrderItemEntity source : orderItems.findByOrderIdOrderByDisplayOrderAscIdAsc(orderId)) {
            InvoiceItemEntity invoiceItem = invoiceItems.saveAndFlush(new InvoiceItemEntity(invoice, source));
            orderModifiers.findByOrderItemIdOrderByDisplayOrderAscIdAsc(source.getId())
                    .forEach(modifier -> invoiceModifiers.save(
                            new InvoiceItemModifierEntity(invoiceItem, modifier)));
        }
        invoiceModifiers.flush();
        audit.record(
                "INVOICE_ISSUED",
                actorId,
                "INVOICE",
                invoice.getId(),
                Map.of(
                        "invoiceNumber", invoice.getInvoiceNumber(),
                        "orderId", orderId,
                        "orderNumber", order.getOrderNumber()),
                ipAddress);
        return new InvoiceIssueResult(response(invoice), true);
    }

    private InvoiceResponse response(InvoiceEntity invoice) {
        List<InvoiceItemResponse> items = invoiceItems
                .findByInvoiceIdOrderByDisplayOrderAscIdAsc(invoice.getId()).stream()
                .map(item -> new InvoiceItemResponse(
                        item.getId(),
                        item.getSourceOrderItem().getId(),
                        item.getItemCode(),
                        item.getItemName(),
                        item.getQuantity(),
                        money(item.getBasePrice()).toPlainString(),
                        money(item.getUnitTotal()).toPlainString(),
                        money(item.getLineTotal()).toPlainString(),
                        item.getDisplayOrder(),
                        invoiceModifiers.findByInvoiceItemIdOrderByDisplayOrderAscIdAsc(item.getId()).stream()
                                .map(modifier -> new InvoiceModifierResponse(
                                        modifier.getId(),
                                        modifier.getGroupName(),
                                        modifier.getOptionName(),
                                        money(modifier.getPriceAdjustment()).toPlainString(),
                                        modifier.getDisplayOrder()))
                                .toList()))
                .toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getOrder().getId(),
                invoice.getOrderNumberSnapshot(),
                invoice.getCurrency(),
                money(invoice.getSubtotal()).toPlainString(),
                money(invoice.getTotal()).toPlainString(),
                money(invoice.getPaidTotal()).toPlainString(),
                invoice.getIssuedAt(),
                invoice.getActorUserId(),
                items);
    }

    private Specification<InvoiceEntity> filters(String search, Instant issuedFrom, Instant issuedTo) {
        return (root, query, builder) -> {
            Predicate predicate = builder.conjunction();
            if (issuedFrom != null) {
                predicate = builder.and(predicate,
                        builder.greaterThanOrEqualTo(root.get("issuedAt"), issuedFrom));
            }
            if (issuedTo != null) {
                predicate = builder.and(predicate, builder.lessThan(root.get("issuedAt"), issuedTo));
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("invoiceNumber")), term),
                        builder.like(builder.lower(root.get("orderNumberSnapshot")), term)));
            }
            return predicate;
        };
    }

    private InvoiceEntity saveInvoice(InvoiceEntity invoice) {
        try {
            return invoices.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException exception) {
            throw PaymentManagementException.contention();
        }
    }

    private OrderEntity lockOrder(Long orderId) {
        try {
            return orders.findByIdForUpdate(orderId)
                    .orElseThrow(() -> PaymentManagementException.notFound("Order"));
        } catch (PessimisticLockingFailureException exception) {
            throw PaymentManagementException.contention();
        }
    }

    private String uniqueInvoiceNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = numbers.generate();
            if (!invoices.existsByInvoiceNumber(candidate)) {
                return candidate;
            }
        }
        throw PaymentManagementException.conflict("Invoice number conflict; retry");
    }

    private void validateRange(Instant start, Instant end) {
        if (start != null && end != null && !start.isBefore(end)) {
            throw PaymentManagementException.badRequest("Start of range must precede end of range");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public record InvoiceIssueResult(InvoiceResponse invoice, boolean created) {
    }
}
