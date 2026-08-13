package com.adam.restaurantoperations.payments;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.payments.PaymentDtos.PaymentRequest;
import com.adam.restaurantoperations.payments.PaymentDtos.PaymentResponse;
import com.adam.restaurantoperations.payments.PaymentDtos.PaymentSummaryResponse;
import com.adam.restaurantoperations.payments.PaymentDtos.ReconciliationRequest;
import com.adam.restaurantoperations.payments.PaymentDtos.ReconciliationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    public List<PaymentResponse> list(
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) Boolean reconciled,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Instant receivedFrom,
            @RequestParam(required = false) Instant receivedTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "receivedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        return service.list(
                method, reconciled, orderId, receivedFrom, receivedTo, search, sortBy, direction);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @GetMapping("/orders/{orderId}")
    public List<PaymentResponse> forOrder(@PathVariable @Positive Long orderId) {
        return service.forOrder(orderId);
    }

    @GetMapping("/orders/{orderId}/summary")
    public PaymentSummaryResponse summary(@PathVariable @Positive Long orderId) {
        return service.summary(orderId);
    }

    @PostMapping("/orders/{orderId}")
    @Operation(
            summary = "Record a confirmed payment",
            description = "Records an externally or physically confirmed settlement; no card data is accepted")
    public ResponseEntity<PaymentResponse> record(
            @PathVariable @Positive Long orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        PaymentService.PaymentRecordResult result = service.record(
                orderId,
                idempotencyKey,
                request,
                actor(jwt),
                RequestMetadata.from(servletRequest).ipAddress());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/payments/" + result.payment().id()))
                        .body(result.payment())
                : ResponseEntity.ok(result.payment());
    }

    @GetMapping("/{paymentId}/reconciliation")
    public ReconciliationResponse reconciliation(@PathVariable @Positive Long paymentId) {
        return service.reconciliation(paymentId);
    }

    @PostMapping("/{paymentId}/reconciliation")
    public ResponseEntity<ReconciliationResponse> reconcile(
            @PathVariable @Positive Long paymentId,
            @Valid @RequestBody ReconciliationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        PaymentService.ReconciliationResult result = service.reconcile(
                paymentId,
                request,
                actor(jwt),
                RequestMetadata.from(servletRequest).ipAddress());
        return result.created()
                ? ResponseEntity.created(URI.create(
                                "/api/v1/payments/" + paymentId + "/reconciliation"))
                        .body(result.reconciliation())
                : ResponseEntity.ok(result.reconciliation());
    }

    private Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
