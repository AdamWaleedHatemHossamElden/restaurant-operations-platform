package com.adam.restaurantoperations.reports;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.adam.restaurantoperations.reports.ReportDtos.InventoryItemMetric;
import com.adam.restaurantoperations.reports.ReportDtos.MenuItemMetric;
import com.adam.restaurantoperations.reports.ReportDtos.PaymentMethodMetric;
import com.adam.restaurantoperations.reports.ReportDtos.ReservationTableMetric;
import com.adam.restaurantoperations.reports.ReportDtos.TableSales;
import org.springframework.stereotype.Component;

@Component
public class CsvExporter {
    public byte[] export(
            ReportExport report,
            ReportService service,
            ReportRange range,
            ReportGroupBy groupBy,
            int top) {
        return switch (report) {
            case SALES -> sales(service, range, groupBy, top);
            case MENU -> menu(service, range, top);
            case PAYMENTS -> payments(service, range, groupBy);
            case RESERVATIONS -> reservations(service, range);
            case INVENTORY -> inventory(service, range, top);
            case STAFF -> staff(service, range, top);
        };
    }

    byte[] rows(List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();
        for (List<String> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) {
                    csv.append(',');
                }
                csv.append(cell(row.get(index)));
            }
            csv.append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    String cell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private byte[] sales(ReportService service, ReportRange range, ReportGroupBy groupBy, int top) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Table number", "Table name", "Completed orders", "Completed order value (EUR)"));
        for (TableSales item : service.sales(range, groupBy, top).byTable()) {
            rows.add(List.of(
                    item.tableNumber(), item.displayName(), Long.toString(item.completedOrders()),
                    item.completedOrderValue()));
        }
        return rows(rows);
    }

    private byte[] menu(ReportService service, ReportRange range, int top) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "Item code", "Historical item label", "Quantity sold", "Completed orders",
                "Completed-order line value (EUR)", "Average quantity per order"));
        for (MenuItemMetric item : service.menu(range, top).items()) {
            rows.add(List.of(
                    item.itemCode(), item.itemName(), Long.toString(item.quantitySold()),
                    Long.toString(item.completedOrders()), item.completedOrderLineValue(),
                    item.averageQuantityPerOrder()));
        }
        return rows(rows);
    }

    private byte[] payments(ReportService service, ReportRange range, ReportGroupBy groupBy) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Payment method", "Payment count", "Payments received (EUR)"));
        for (PaymentMethodMetric item : service.payments(range, groupBy).byMethod()) {
            rows.add(List.of(item.method(), Long.toString(item.count()), item.amount()));
        }
        return rows(rows);
    }

    private byte[] reservations(ReportService service, ReportRange range) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Table number", "Table name", "Reservations", "Planned guests"));
        for (ReservationTableMetric item : service.reservations(range).byTable()) {
            rows.add(List.of(
                    item.tableNumber() == null ? "Unassigned" : item.tableNumber(),
                    item.displayName() == null ? "Unassigned" : item.displayName(),
                    Long.toString(item.reservations()), Long.toString(item.plannedGuests())));
        }
        return rows(rows);
    }

    private byte[] inventory(ReportService service, ReportRange range, int top) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "Item code", "Item name", "Canonical unit", "Receipt", "Usage", "Waste",
                "Adjustment in", "Adjustment out", "Net movement"));
        for (InventoryItemMetric item : service.inventory(range, top).items()) {
            rows.add(List.of(
                    item.code(), item.name(), item.unit(), item.receipt(), item.usage(), item.waste(),
                    item.adjustmentIn(), item.adjustmentOut(), item.netMovement()));
        }
        return rows(rows);
    }

    private byte[] staff(ReportService service, ReportRange range, int top) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "Employee code", "Employee name", "Shifts", "Scheduled hours",
                "Completed shift hours"));
        for (var item : service.staff(range, top).hoursByEmployee()) {
            rows.add(List.of(
                    item.employeeCode(), item.employeeName(), Long.toString(item.shifts()),
                    item.scheduledHours(), item.completedShiftHours()));
        }
        return rows(rows);
    }
}
