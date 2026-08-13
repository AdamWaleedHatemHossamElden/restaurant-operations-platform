package com.adam.restaurantoperations.reports;

import java.time.Instant;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    public record Period(Instant from, Instant to) {}

    public record OverviewResponse(
            Period period,
            long completedOrders,
            String completedOrderValue,
            String averageCompletedOrderValue,
            String paymentsReceived,
            long paymentCount,
            long reconciledPaymentCount,
            long invoiceCount,
            long reservations,
            long readyKitchenTickets,
            String scheduledStaffHours) {}

    public record TimePoint(String bucket, long count, String amount) {}

    public record SalesResponse(
            Period period,
            ReportGroupBy groupBy,
            long completedOrders,
            String completedOrderValue,
            String averageCompletedOrderValue,
            List<TimePoint> series,
            List<TableSales> byTable,
            List<OrderValue> topOrders) {}

    public record TableSales(
            long tableId,
            String tableNumber,
            String displayName,
            long completedOrders,
            String completedOrderValue) {}

    public record OrderValue(long orderId, String orderNumber, Instant completedAt, String total) {}

    public record MenuPerformanceResponse(Period period, int top, List<MenuItemMetric> items) {}

    public record MenuItemMetric(
            long menuItemId,
            String itemCode,
            String itemName,
            long quantitySold,
            long completedOrders,
            String completedOrderLineValue,
            String averageQuantityPerOrder) {}

    public record PaymentsResponse(
            Period period,
            ReportGroupBy groupBy,
            String paymentsReceived,
            long paymentCount,
            String averagePaymentAmount,
            long reconciledCount,
            long unreconciledCount,
            String reconciledAmount,
            String unreconciledAmount,
            List<PaymentMethodMetric> byMethod,
            List<TimePoint> series) {}

    public record PaymentMethodMetric(String method, long count, String amount) {}

    public record ReservationsResponse(
            Period period,
            long reservations,
            long plannedGuests,
            String averagePartySize,
            List<CountMetric> byStatus,
            List<ReservationTableMetric> byTable) {}

    public record CountMetric(String key, long count) {}

    public record ReservationTableMetric(
            Long tableId, String tableNumber, String displayName, long reservations, long plannedGuests) {}

    public record KitchenResponse(
            Period period,
            long ticketsCreated,
            long readyTickets,
            long cancelledTickets,
            String averagePreparationMinutes,
            List<CountMetric> byStatus,
            List<KitchenItemMetric> itemPreparation) {}

    public record KitchenItemMetric(String itemCode, String itemName, long quantity) {}

    public record InventoryResponse(
            Period period,
            long movementCount,
            long currentLowStockItems,
            List<CountMetric> movementCounts,
            List<InventoryItemMetric> items) {}

    public record InventoryItemMetric(
            long inventoryItemId,
            String code,
            String name,
            String unit,
            String receipt,
            String usage,
            String waste,
            String adjustmentIn,
            String adjustmentOut,
            String netMovement,
            String currentOnHand,
            boolean currentlyLowStock) {}

    public record StaffResponse(
            Period period,
            long shiftCount,
            long scheduledCount,
            long completedCount,
            long cancelledCount,
            String scheduledHours,
            String completedShiftHours,
            List<HoursMetric> hoursByRole,
            List<EmployeeHoursMetric> hoursByEmployee) {}

    public record HoursMetric(String key, long shifts, String hours) {}

    public record EmployeeHoursMetric(
            long employeeId,
            String employeeCode,
            String employeeName,
            long shifts,
            String scheduledHours,
            String completedShiftHours) {}
}
