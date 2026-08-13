package com.adam.restaurantoperations.payments;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.payments.PaymentDtos.InvoiceResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class InvoiceController {
    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvoiceResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Instant issuedFrom,
            @RequestParam(required = false) Instant issuedTo,
            @RequestParam(defaultValue = "issuedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        return service.list(search, issuedFrom, issuedTo, sortBy, direction);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @GetMapping("/orders/{orderId}")
    public InvoiceResponse forOrder(@PathVariable @Positive Long orderId) {
        return service.forOrder(orderId);
    }

    @PostMapping("/orders/{orderId}")
    public ResponseEntity<InvoiceResponse> issue(
            @PathVariable @Positive Long orderId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        InvoiceService.InvoiceIssueResult result = service.issue(
                orderId,
                Long.valueOf(jwt.getSubject()),
                RequestMetadata.from(servletRequest).ipAddress());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/invoices/" + result.invoice().id()))
                        .body(result.invoice())
                : ResponseEntity.ok(result.invoice());
    }
}
