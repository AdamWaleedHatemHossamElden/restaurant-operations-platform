package com.adam.restaurantoperations.kitchen;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.adam.restaurantoperations.audit.KitchenAuditService;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenItemResponse;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenItemStatusRequest;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenModifierSnapshot;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenReservationSummary;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenTableSummary;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenTicketResponse;
import com.adam.restaurantoperations.kitchen.realtime.KitchenDomainEventPublisher;
import com.adam.restaurantoperations.kitchen.realtime.KitchenEventType;
import com.adam.restaurantoperations.kitchen.realtime.KitchenRealtimeEvent;
import com.adam.restaurantoperations.orders.OrderEntity;
import com.adam.restaurantoperations.orders.OrderItemEntity;
import com.adam.restaurantoperations.orders.OrderItemModifierRepository;
import com.adam.restaurantoperations.orders.OrderItemRepository;
import com.adam.restaurantoperations.orders.OrderRepository;
import com.adam.restaurantoperations.orders.OrderStatus;
import com.adam.restaurantoperations.reservations.ReservationEntity;
import com.adam.restaurantoperations.tables.RestaurantTableEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KitchenService {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "submittedAt", "order.submittedAt",
            "orderNumber", "order.orderNumber",
            "table", "order.restaurantTable.tableNumber",
            "status", "status");

    private final KitchenTicketRepository tickets;
    private final KitchenTicketItemRepository kitchenItems;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderItemModifierRepository orderModifiers;
    private final KitchenAuditService audit;
    private final KitchenDomainEventPublisher events;

    public KitchenService(
            KitchenTicketRepository tickets,
            KitchenTicketItemRepository kitchenItems,
            OrderRepository orders,
            OrderItemRepository orderItems,
            OrderItemModifierRepository orderModifiers,
            KitchenAuditService audit,
            KitchenDomainEventPublisher events) {
        this.tickets = tickets;
        this.kitchenItems = kitchenItems;
        this.orders = orders;
        this.orderItems = orderItems;
        this.orderModifiers = orderModifiers;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<KitchenTicketResponse> list(
            KitchenTicketStatus status,
            Long tableId,
            String orderNumber,
            Instant submittedFrom,
            Instant submittedTo,
            boolean includeCancelled,
            String sortBy,
            Sort.Direction direction) {
        validateRange(submittedFrom, submittedTo);
        String field = SORT_FIELDS.getOrDefault(sortBy, "createdAt");
        Sort sort = Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
        return tickets.findAll(
                        filters(status, tableId, orderNumber, submittedFrom, submittedTo, includeCancelled),
                        sort)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public KitchenTicketResponse get(Long ticketId) {
        return response(find(ticketId));
    }

    @Transactional(readOnly = true)
    public KitchenTicketResponse getByOrder(Long orderId) {
        return response(tickets.findByOrderId(orderId)
                .orElseThrow(() -> KitchenManagementException.notFound("Kitchen ticket")));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public KitchenTicketResponse transitionItem(
            Long ticketId,
            Long itemId,
            KitchenItemStatusRequest request,
            Long actorId,
            String ipAddress) {
        Long orderId = tickets.findOrderIdById(ticketId)
                .orElseThrow(() -> KitchenManagementException.notFound("Kitchen ticket"));
        OrderEntity order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> KitchenManagementException.notFound("Order"));
        KitchenTicketEntity ticket = tickets.findByIdForUpdate(ticketId)
                .orElseThrow(() -> KitchenManagementException.notFound("Kitchen ticket"));
        if (!ticket.getOrder().getId().equals(order.getId())) {
            throw KitchenManagementException.conflict("Kitchen ticket changed concurrently; retry");
        }
        verifyVersion(ticket, request.version());
        if (order.getStatus() != OrderStatus.SUBMITTED || ticket.getStatus() == KitchenTicketStatus.CANCELLED) {
            throw KitchenManagementException.conflict("Kitchen work is no longer active for this order");
        }
        KitchenTicketItemEntity item = kitchenItems.findByIdAndTicketId(itemId, ticketId)
                .orElseThrow(() -> KitchenManagementException.notFound("Kitchen item"));
        if (!item.getStatus().canTransitionTo(request.status())) {
            throw KitchenManagementException.conflict("Kitchen item status transition is not allowed");
        }

        Instant changedAt = Instant.now();
        KitchenTicketStatus previousTicketStatus = ticket.getStatus();
        item.transitionTo(request.status(), changedAt);
        kitchenItems.saveAndFlush(item);
        KitchenTicketStatus derivedStatus = derive(kitchenItems.findOrderedByTicketId(ticketId));
        ticket.derive(derivedStatus, changedAt);
        KitchenTicketEntity savedTicket = save(ticket);

        String itemAction = request.status() == KitchenItemStatus.PREPARING
                ? "KITCHEN_ITEM_PREPARING"
                : "KITCHEN_ITEM_READY";
        audit.record(itemAction, actorId, ticketId, order.getId(), itemId, ipAddress);
        publish(
                KitchenEventType.KITCHEN_ITEM_STATUS_CHANGED,
                savedTicket,
                itemId,
                request.status(),
                changedAt);
        if (previousTicketStatus != derivedStatus) {
            if (derivedStatus == KitchenTicketStatus.READY) {
                audit.record("KITCHEN_TICKET_READY", actorId, ticketId, order.getId(), null, ipAddress);
            }
            publish(
                    KitchenEventType.KITCHEN_TICKET_STATUS_CHANGED,
                    savedTicket,
                    null,
                    null,
                    changedAt);
        }
        return response(savedTicket);
    }

    public KitchenTicketEntity createForSubmittedOrder(OrderEntity order, Long actorId, String ipAddress) {
        if (tickets.existsByOrderId(order.getId())) {
            throw KitchenManagementException.conflict("Kitchen ticket already exists for this order");
        }
        List<OrderItemEntity> submittedItems = orderItems.findByOrderIdOrderByDisplayOrderAscIdAsc(order.getId());
        if (submittedItems.isEmpty()) {
            throw KitchenManagementException.conflict("A submitted order requires kitchen items");
        }
        KitchenTicketEntity ticket = save(new KitchenTicketEntity(order));
        List<KitchenTicketItemEntity> newItems = submittedItems.stream()
                .map(orderItem -> new KitchenTicketItemEntity(ticket, orderItem))
                .toList();
        kitchenItems.saveAll(newItems);
        kitchenItems.flush();
        audit.record("KITCHEN_TICKET_CREATED", actorId, ticket.getId(), order.getId(), null, ipAddress);
        publish(KitchenEventType.KITCHEN_TICKET_CREATED, ticket, null, null, Instant.now());
        return ticket;
    }

    public void cancelForSubmittedOrder(OrderEntity order, Long actorId, String ipAddress) {
        tickets.findByOrderIdForUpdate(order.getId()).ifPresent(ticket -> {
            if (ticket.getStatus() == KitchenTicketStatus.CANCELLED) {
                return;
            }
            Instant changedAt = Instant.now();
            ticket.cancel(changedAt);
            KitchenTicketEntity saved = save(ticket);
            audit.record(
                    "KITCHEN_TICKET_CANCELLED",
                    actorId,
                    ticket.getId(),
                    order.getId(),
                    null,
                    ipAddress);
            publish(KitchenEventType.KITCHEN_TICKET_CANCELLED, saved, null, null, changedAt);
        });
    }

    public void requireReadyForCompletion(OrderEntity order) {
        tickets.findByOrderIdForUpdate(order.getId()).ifPresent(ticket -> {
            if (ticket.getStatus() != KitchenTicketStatus.READY) {
                throw KitchenManagementException.conflict(
                        "Kitchen ticket must be READY before completing the order");
            }
        });
    }

    private KitchenTicketStatus derive(List<KitchenTicketItemEntity> items) {
        if (items.stream().allMatch(item -> item.getStatus() == KitchenItemStatus.QUEUED)) {
            return KitchenTicketStatus.QUEUED;
        }
        if (items.stream().allMatch(item -> item.getStatus() == KitchenItemStatus.READY)) {
            return KitchenTicketStatus.READY;
        }
        return KitchenTicketStatus.PREPARING;
    }

    private KitchenTicketResponse response(KitchenTicketEntity ticket) {
        OrderEntity order = ticket.getOrder();
        RestaurantTableEntity table = order.getRestaurantTable();
        ReservationEntity reservation = order.getReservation();
        List<KitchenItemResponse> items = kitchenItems.findOrderedByTicketId(ticket.getId()).stream()
                .map(this::itemResponse)
                .toList();
        return new KitchenTicketResponse(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getVersion(),
                order.getId(),
                order.getOrderNumber(),
                new KitchenTableSummary(
                        table.getId(),
                        table.getTableNumber(),
                        table.getDisplayName(),
                        table.getSection()),
                reservation == null
                        ? null
                        : new KitchenReservationSummary(reservation.getId(), reservation.getReservationCode()),
                order.getSubmittedAt(),
                ticket.getCreatedAt(),
                ticket.getStartedAt(),
                ticket.getReadyAt(),
                ticket.getCancelledAt(),
                items);
    }

    private KitchenItemResponse itemResponse(KitchenTicketItemEntity item) {
        OrderItemEntity orderItem = item.getOrderItem();
        List<KitchenModifierSnapshot> modifiers = orderModifiers
                .findByOrderItemIdOrderByDisplayOrderAscIdAsc(orderItem.getId())
                .stream()
                .map(modifier -> new KitchenModifierSnapshot(
                        modifier.getGroupNameSnapshot(),
                        modifier.getOptionNameSnapshot()))
                .toList();
        return new KitchenItemResponse(
                item.getId(),
                orderItem.getId(),
                orderItem.getItemCodeSnapshot(),
                orderItem.getItemNameSnapshot(),
                orderItem.getQuantity(),
                orderItem.getNotes(),
                orderItem.getDisplayOrder(),
                item.getStatus(),
                item.getStartedAt(),
                item.getReadyAt(),
                modifiers);
    }

    private Specification<KitchenTicketEntity> filters(
            KitchenTicketStatus status,
            Long tableId,
            String orderNumber,
            Instant submittedFrom,
            Instant submittedTo,
            boolean includeCancelled) {
        return (root, query, builder) -> {
            Predicate predicate = builder.conjunction();
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            } else if (!includeCancelled) {
                predicate = builder.and(
                        predicate,
                        builder.notEqual(root.get("status"), KitchenTicketStatus.CANCELLED));
            }
            if (tableId != null) {
                predicate = builder.and(
                        predicate,
                        builder.equal(root.get("order").get("restaurantTable").get("id"), tableId));
            }
            if (hasText(orderNumber)) {
                String term = "%" + orderNumber.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(
                        predicate,
                        builder.like(builder.lower(root.get("order").get("orderNumber")), term));
            }
            if (submittedFrom != null) {
                predicate = builder.and(
                        predicate,
                        builder.greaterThanOrEqualTo(root.get("order").get("submittedAt"), submittedFrom));
            }
            if (submittedTo != null) {
                predicate = builder.and(
                        predicate,
                        builder.lessThan(root.get("order").get("submittedAt"), submittedTo));
            }
            return predicate;
        };
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw KitchenManagementException.badRequest("Start of range must precede end of range");
        }
    }

    private void verifyVersion(KitchenTicketEntity ticket, Long suppliedVersion) {
        if (ticket.getVersion() != suppliedVersion) {
            throw KitchenManagementException.stale();
        }
    }

    private KitchenTicketEntity find(Long id) {
        return tickets.findById(id).orElseThrow(() -> KitchenManagementException.notFound("Kitchen ticket"));
    }

    private KitchenTicketEntity save(KitchenTicketEntity ticket) {
        try {
            return tickets.saveAndFlush(ticket);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw KitchenManagementException.stale();
        } catch (DataIntegrityViolationException exception) {
            throw KitchenManagementException.conflict("Kitchen data conflicts with another request; retry");
        }
    }

    private void publish(
            KitchenEventType type,
            KitchenTicketEntity ticket,
            Long kitchenItemId,
            KitchenItemStatus itemStatus,
            Instant timestamp) {
        events.publish(new KitchenRealtimeEvent(
                type,
                ticket.getId(),
                ticket.getOrder().getId(),
                ticket.getOrder().getOrderNumber(),
                ticket.getStatus(),
                kitchenItemId,
                itemStatus,
                timestamp));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
