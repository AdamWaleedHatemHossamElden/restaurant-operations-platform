package com.adam.restaurantoperations.reports;

import java.time.Instant;

import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.reports.ReportDtos.InventoryResponse;
import com.adam.restaurantoperations.reports.ReportDtos.KitchenResponse;
import com.adam.restaurantoperations.reports.ReportDtos.MenuPerformanceResponse;
import com.adam.restaurantoperations.reports.ReportDtos.OverviewResponse;
import com.adam.restaurantoperations.reports.ReportDtos.PaymentsResponse;
import com.adam.restaurantoperations.reports.ReportDtos.ReservationsResponse;
import com.adam.restaurantoperations.reports.ReportDtos.SalesResponse;
import com.adam.restaurantoperations.reports.ReportDtos.StaffResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class ReportController {
    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private final ReportService service;
    private final CsvExporter exporter;

    public ReportController(ReportService service, CsvExporter exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(@RequestParam Instant from, @RequestParam Instant to) {
        return service.overview(range(from, to));
    }

    @GetMapping("/sales")
    public SalesResponse sales(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "DAY") ReportGroupBy groupBy,
            @RequestParam(defaultValue = "10") Integer top) {
        return service.sales(range(from, to), groupBy, ReportRange.validatedTop(top));
    }

    @GetMapping("/menu-performance")
    public MenuPerformanceResponse menu(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") Integer top) {
        return service.menu(range(from, to), ReportRange.validatedTop(top));
    }

    @GetMapping("/payments")
    public PaymentsResponse payments(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "DAY") ReportGroupBy groupBy) {
        return service.payments(range(from, to), groupBy);
    }

    @GetMapping("/reservations")
    public ReservationsResponse reservations(@RequestParam Instant from, @RequestParam Instant to) {
        return service.reservations(range(from, to));
    }

    @GetMapping("/kitchen")
    public KitchenResponse kitchen(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") Integer top) {
        return service.kitchen(range(from, to), ReportRange.validatedTop(top));
    }

    @GetMapping("/inventory")
    public InventoryResponse inventory(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "100") Integer top) {
        return service.inventory(range(from, to), ReportRange.validatedTop(top));
    }

    @GetMapping("/staff")
    public StaffResponse staff(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "100") Integer top) {
        return service.staff(range(from, to), ReportRange.validatedTop(top));
    }

    @GetMapping("/exports/{report}.csv")
    public ResponseEntity<byte[]> export(
            @PathVariable String report,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "DAY") ReportGroupBy groupBy,
            @RequestParam(defaultValue = "100") Integer top) {
        ReportExport type = ReportExport.parse(report);
        byte[] content = exporter.export(
                type, service, range(from, to), groupBy, ReportRange.validatedTop(top));
        String filename = "restaurant-" + type.name().toLowerCase(java.util.Locale.ROOT) + "-report.csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    private ReportRange range(Instant from, Instant to) {
        return ReportRange.validated(from, to);
    }
}
