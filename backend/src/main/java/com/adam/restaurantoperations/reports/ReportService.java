package com.adam.restaurantoperations.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.reports.ReportDtos.CountMetric;
import com.adam.restaurantoperations.reports.ReportDtos.EmployeeHoursMetric;
import com.adam.restaurantoperations.reports.ReportDtos.HoursMetric;
import com.adam.restaurantoperations.reports.ReportDtos.InventoryItemMetric;
import com.adam.restaurantoperations.reports.ReportDtos.InventoryResponse;
import com.adam.restaurantoperations.reports.ReportDtos.KitchenItemMetric;
import com.adam.restaurantoperations.reports.ReportDtos.KitchenResponse;
import com.adam.restaurantoperations.reports.ReportDtos.MenuItemMetric;
import com.adam.restaurantoperations.reports.ReportDtos.MenuPerformanceResponse;
import com.adam.restaurantoperations.reports.ReportDtos.OrderValue;
import com.adam.restaurantoperations.reports.ReportDtos.OverviewResponse;
import com.adam.restaurantoperations.reports.ReportDtos.PaymentMethodMetric;
import com.adam.restaurantoperations.reports.ReportDtos.PaymentsResponse;
import com.adam.restaurantoperations.reports.ReportDtos.Period;
import com.adam.restaurantoperations.reports.ReportDtos.ReservationTableMetric;
import com.adam.restaurantoperations.reports.ReportDtos.ReservationsResponse;
import com.adam.restaurantoperations.reports.ReportDtos.SalesResponse;
import com.adam.restaurantoperations.reports.ReportDtos.StaffResponse;
import com.adam.restaurantoperations.reports.ReportDtos.TableSales;
import com.adam.restaurantoperations.reports.ReportDtos.TimePoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final BigDecimal ZERO_QUANTITY = new BigDecimal("0.000");
    private static final BigDecimal ZERO_HOURS = new BigDecimal("0.00");
    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview(ReportRange range) {
        Aggregate orders = aggregate(
                "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM orders "
                        + "WHERE status = 'COMPLETED' AND completed_at >= ? AND completed_at < ?",
                range);
        Aggregate payments = aggregate(
                "SELECT COUNT(*), COALESCE(SUM(amount), 0) FROM payments "
                        + "WHERE status = 'SUCCEEDED' AND received_at >= ? AND received_at < ?",
                range);
        long reconciled = count(
                "SELECT COUNT(*) FROM payment_reconciliations r JOIN payments p ON p.id = r.payment_id "
                        + "WHERE p.received_at >= ? AND p.received_at < ?",
                range);
        long invoices = count("SELECT COUNT(*) FROM invoices WHERE issued_at >= ? AND issued_at < ?", range);
        long reservations = count(
                "SELECT COUNT(*) FROM reservations WHERE start_at >= ? AND start_at < ?", range);
        long kitchen = count(
                "SELECT COUNT(*) FROM kitchen_tickets WHERE status = 'READY' "
                        + "AND created_at >= ? AND created_at < ?",
                range);
        BigDecimal staffHours = decimal(
                "SELECT COALESCE(SUM(TIMESTAMPDIFF(SECOND, start_at, end_at)) / 3600, 0) "
                        + "FROM shifts WHERE status <> 'CANCELLED' AND start_at >= ? AND start_at < ?",
                range);
        return new OverviewResponse(
                period(range),
                orders.count(),
                money(orders.amount()),
                money(average(orders.amount(), orders.count())),
                money(payments.amount()),
                payments.count(),
                reconciled,
                invoices,
                reservations,
                kitchen,
                hours(staffHours));
    }

    @Transactional(readOnly = true)
    public SalesResponse sales(ReportRange range, ReportGroupBy groupBy, int top) {
        Aggregate aggregate = aggregate(
                "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM orders "
                        + "WHERE status = 'COMPLETED' AND completed_at >= ? AND completed_at < ?",
                range);
        String bucket = bucket("o.completed_at", groupBy);
        List<TimePoint> series = jdbc.query(
                "SELECT " + bucket + " bucket, COUNT(*) item_count, COALESCE(SUM(o.total), 0) amount "
                        + "FROM orders o WHERE o.status = 'COMPLETED' "
                        + "AND o.completed_at >= ? AND o.completed_at < ? "
                        + "GROUP BY bucket ORDER BY bucket",
                (rs, row) -> new TimePoint(rs.getString("bucket"), rs.getLong("item_count"),
                        money(rs.getBigDecimal("amount"))),
                from(range), to(range));
        List<TableSales> byTable = jdbc.query(
                "SELECT t.id, t.table_number, t.display_name, COUNT(*) order_count, SUM(o.total) amount "
                        + "FROM orders o JOIN restaurant_tables t ON t.id = o.restaurant_table_id "
                        + "WHERE o.status = 'COMPLETED' AND o.completed_at >= ? AND o.completed_at < ? "
                        + "GROUP BY t.id, t.table_number, t.display_name "
                        + "ORDER BY amount DESC, t.id ASC LIMIT ?",
                (rs, row) -> new TableSales(
                        rs.getLong("id"), rs.getString("table_number"), rs.getString("display_name"),
                        rs.getLong("order_count"), money(rs.getBigDecimal("amount"))),
                from(range), to(range), top);
        List<OrderValue> topOrders = jdbc.query(
                "SELECT id, order_number, completed_at, total FROM orders "
                        + "WHERE status = 'COMPLETED' AND completed_at >= ? AND completed_at < ? "
                        + "ORDER BY total DESC, completed_at ASC, id ASC LIMIT ?",
                (rs, row) -> new OrderValue(
                        rs.getLong("id"), rs.getString("order_number"), instant(rs, "completed_at"),
                        money(rs.getBigDecimal("total"))),
                from(range), to(range), top);
        return new SalesResponse(
                period(range), groupBy, aggregate.count(), money(aggregate.amount()),
                money(average(aggregate.amount(), aggregate.count())), series, byTable, topOrders);
    }

    @Transactional(readOnly = true)
    public MenuPerformanceResponse menu(ReportRange range, int top) {
        List<MenuItemMetric> items = jdbc.query(
                "SELECT oi.menu_item_id, MAX(oi.item_code_snapshot) item_code, "
                        + "MAX(oi.item_name_snapshot) item_name, SUM(oi.quantity) quantity_sold, "
                        + "COUNT(DISTINCT o.id) order_count, SUM(oi.line_total) line_value "
                        + "FROM order_items oi JOIN orders o ON o.id = oi.order_id "
                        + "WHERE o.status = 'COMPLETED' AND o.completed_at >= ? AND o.completed_at < ? "
                        + "GROUP BY oi.menu_item_id "
                        + "ORDER BY quantity_sold DESC, line_value DESC, oi.menu_item_id ASC LIMIT ?",
                (rs, row) -> {
                    long orderCount = rs.getLong("order_count");
                    BigDecimal quantity = rs.getBigDecimal("quantity_sold");
                    return new MenuItemMetric(
                            rs.getLong("menu_item_id"), rs.getString("item_code"),
                            rs.getString("item_name"), quantity.longValueExact(), orderCount,
                            money(rs.getBigDecimal("line_value")), quantity(quantity.divide(
                                    BigDecimal.valueOf(orderCount), 3, RoundingMode.HALF_UP)));
                },
                from(range), to(range), top);
        return new MenuPerformanceResponse(period(range), top, items);
    }

    @Transactional(readOnly = true)
    public PaymentsResponse payments(ReportRange range, ReportGroupBy groupBy) {
        PaymentAggregate aggregate = jdbc.queryForObject(
                "SELECT COUNT(*) payment_count, COALESCE(SUM(p.amount), 0) total_amount, "
                        + "SUM(CASE WHEN r.id IS NOT NULL THEN 1 ELSE 0 END) reconciled_count, "
                        + "COALESCE(SUM(CASE WHEN r.id IS NOT NULL THEN p.amount ELSE 0 END), 0) reconciled_amount "
                        + "FROM payments p LEFT JOIN payment_reconciliations r ON r.payment_id = p.id "
                        + "WHERE p.status = 'SUCCEEDED' AND p.received_at >= ? AND p.received_at < ?",
                (rs, row) -> new PaymentAggregate(
                        rs.getLong("payment_count"), rs.getBigDecimal("total_amount"),
                        rs.getLong("reconciled_count"), rs.getBigDecimal("reconciled_amount")),
                from(range), to(range));
        List<PaymentMethodMetric> methods = jdbc.query(
                "SELECT method, COUNT(*) payment_count, SUM(amount) amount FROM payments "
                        + "WHERE status = 'SUCCEEDED' AND received_at >= ? AND received_at < ? "
                        + "GROUP BY method ORDER BY method",
                (rs, row) -> new PaymentMethodMetric(
                        rs.getString("method"), rs.getLong("payment_count"),
                        money(rs.getBigDecimal("amount"))),
                from(range), to(range));
        String bucket = bucket("received_at", groupBy);
        List<TimePoint> series = jdbc.query(
                "SELECT " + bucket + " bucket, COUNT(*) item_count, SUM(amount) amount FROM payments "
                        + "WHERE status = 'SUCCEEDED' AND received_at >= ? AND received_at < ? "
                        + "GROUP BY bucket ORDER BY bucket",
                (rs, row) -> new TimePoint(rs.getString("bucket"), rs.getLong("item_count"),
                        money(rs.getBigDecimal("amount"))),
                from(range), to(range));
        long unreconciled = aggregate.count() - aggregate.reconciledCount();
        BigDecimal unreconciledAmount = aggregate.amount().subtract(aggregate.reconciledAmount());
        return new PaymentsResponse(
                period(range), groupBy, money(aggregate.amount()), aggregate.count(),
                money(average(aggregate.amount(), aggregate.count())), aggregate.reconciledCount(),
                unreconciled, money(aggregate.reconciledAmount()), money(unreconciledAmount), methods, series);
    }

    @Transactional(readOnly = true)
    public ReservationsResponse reservations(ReportRange range) {
        Aggregate aggregate = jdbc.queryForObject(
                "SELECT COUNT(*) item_count, COALESCE(SUM(party_size), 0) amount FROM reservations "
                        + "WHERE start_at >= ? AND start_at < ?",
                (rs, row) -> new Aggregate(rs.getLong("item_count"), rs.getBigDecimal("amount")),
                from(range), to(range));
        List<CountMetric> statuses = counts(
                "SELECT status metric_key, COUNT(*) metric_count FROM reservations "
                        + "WHERE start_at >= ? AND start_at < ? GROUP BY status ORDER BY status",
                range);
        List<ReservationTableMetric> tables = jdbc.query(
                "SELECT t.id, t.table_number, t.display_name, COUNT(*) reservation_count, "
                        + "SUM(r.party_size) planned_guests FROM reservations r "
                        + "LEFT JOIN restaurant_tables t ON t.id = r.restaurant_table_id "
                        + "WHERE r.start_at >= ? AND r.start_at < ? "
                        + "GROUP BY t.id, t.table_number, t.display_name "
                        + "ORDER BY reservation_count DESC, t.id ASC",
                (rs, row) -> new ReservationTableMetric(
                        nullableLong(rs, "id"), rs.getString("table_number"), rs.getString("display_name"),
                        rs.getLong("reservation_count"), rs.getLong("planned_guests")),
                from(range), to(range));
        return new ReservationsResponse(
                period(range), aggregate.count(), aggregate.amount().longValueExact(),
                quantity(average(aggregate.amount(), aggregate.count())), statuses, tables);
    }

    @Transactional(readOnly = true)
    public KitchenResponse kitchen(ReportRange range, int top) {
        KitchenAggregate aggregate = jdbc.queryForObject(
                "SELECT COUNT(*) ticket_count, SUM(status = 'READY') ready_count, "
                        + "SUM(status = 'CANCELLED') cancelled_count, "
                        + "COALESCE(AVG(CASE WHEN status = 'READY' AND ready_at IS NOT NULL "
                        + "THEN TIMESTAMPDIFF(MICROSECOND, created_at, ready_at) / 60000000 END), 0) avg_minutes "
                        + "FROM kitchen_tickets WHERE created_at >= ? AND created_at < ?",
                (rs, row) -> new KitchenAggregate(
                        rs.getLong("ticket_count"), rs.getLong("ready_count"),
                        rs.getLong("cancelled_count"), rs.getBigDecimal("avg_minutes")),
                from(range), to(range));
        List<CountMetric> statuses = counts(
                "SELECT status metric_key, COUNT(*) metric_count FROM kitchen_tickets "
                        + "WHERE created_at >= ? AND created_at < ? GROUP BY status ORDER BY status",
                range);
        List<KitchenItemMetric> items = jdbc.query(
                "SELECT oi.item_code_snapshot item_code, oi.item_name_snapshot item_name, "
                        + "SUM(oi.quantity) quantity FROM kitchen_ticket_items ki "
                        + "JOIN kitchen_tickets kt ON kt.id = ki.kitchen_ticket_id "
                        + "JOIN order_items oi ON oi.id = ki.order_item_id "
                        + "WHERE kt.created_at >= ? AND kt.created_at < ? "
                        + "GROUP BY oi.item_code_snapshot, oi.item_name_snapshot "
                        + "ORDER BY quantity DESC, item_code ASC, item_name ASC LIMIT ?",
                (rs, row) -> new KitchenItemMetric(
                        rs.getString("item_code"), rs.getString("item_name"), rs.getLong("quantity")),
                from(range), to(range), top);
        return new KitchenResponse(
                period(range), aggregate.count(), aggregate.ready(), aggregate.cancelled(),
                decimal(aggregate.averageMinutes(), 2), statuses, items);
    }

    @Transactional(readOnly = true)
    public InventoryResponse inventory(ReportRange range, int top) {
        long movementCount = count(
                "SELECT COUNT(*) FROM stock_movements WHERE occurred_at >= ? AND occurred_at < ?", range);
        long lowStock = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT i.id FROM inventory_items i "
                        + "LEFT JOIN stock_movements m ON m.inventory_item_id = i.id WHERE i.active = TRUE "
                        + "GROUP BY i.id, i.reorder_threshold HAVING COALESCE(SUM(CASE m.movement_type "
                        + "WHEN 'RECEIPT' THEN m.quantity WHEN 'ADJUSTMENT_IN' THEN m.quantity "
                        + "ELSE -m.quantity END), 0) <= i.reorder_threshold) low_stock",
                Long.class);
        List<CountMetric> types = counts(
                "SELECT movement_type metric_key, COUNT(*) metric_count FROM stock_movements "
                        + "WHERE occurred_at >= ? AND occurred_at < ? "
                        + "GROUP BY movement_type ORDER BY movement_type",
                range);
        List<InventoryItemMetric> items = jdbc.query(
                "SELECT i.id, i.code, i.name, i.unit, i.reorder_threshold, "
                        + "SUM(CASE WHEN m.occurred_at >= ? AND m.occurred_at < ? "
                        + "AND m.movement_type = 'RECEIPT' THEN m.quantity ELSE 0 END) receipt_quantity, "
                        + "SUM(CASE WHEN m.occurred_at >= ? AND m.occurred_at < ? "
                        + "AND m.movement_type = 'USAGE' THEN m.quantity ELSE 0 END) usage_quantity, "
                        + "SUM(CASE WHEN m.occurred_at >= ? AND m.occurred_at < ? "
                        + "AND m.movement_type = 'WASTE' THEN m.quantity ELSE 0 END) waste_quantity, "
                        + "SUM(CASE WHEN m.occurred_at >= ? AND m.occurred_at < ? "
                        + "AND m.movement_type = 'ADJUSTMENT_IN' THEN m.quantity ELSE 0 END) adjustment_in_quantity, "
                        + "SUM(CASE WHEN m.occurred_at >= ? AND m.occurred_at < ? "
                        + "AND m.movement_type = 'ADJUSTMENT_OUT' THEN m.quantity ELSE 0 END) adjustment_out_quantity, "
                        + "COALESCE(SUM(CASE m.movement_type WHEN 'RECEIPT' THEN m.quantity "
                        + "WHEN 'ADJUSTMENT_IN' THEN m.quantity ELSE -m.quantity END), 0) current_on_hand "
                        + "FROM inventory_items i LEFT JOIN stock_movements m ON m.inventory_item_id = i.id "
                        + "GROUP BY i.id, i.code, i.name, i.unit, i.reorder_threshold "
                        + "HAVING receipt_quantity + usage_quantity + waste_quantity "
                        + "+ adjustment_in_quantity + adjustment_out_quantity > 0 "
                        + "ORDER BY (receipt_quantity + usage_quantity + waste_quantity "
                        + "+ adjustment_in_quantity + adjustment_out_quantity) DESC, i.id ASC LIMIT ?",
                (rs, row) -> inventoryItem(rs),
                from(range), to(range), from(range), to(range), from(range), to(range),
                from(range), to(range), from(range), to(range), top);
        return new InventoryResponse(period(range), movementCount, lowStock, types, items);
    }

    @Transactional(readOnly = true)
    public StaffResponse staff(ReportRange range, int top) {
        StaffAggregate aggregate = jdbc.queryForObject(
                "SELECT COUNT(*) shift_count, SUM(status = 'SCHEDULED') scheduled_count, "
                        + "SUM(status = 'COMPLETED') completed_count, SUM(status = 'CANCELLED') cancelled_count, "
                        + "COALESCE(SUM(CASE WHEN status <> 'CANCELLED' "
                        + "THEN TIMESTAMPDIFF(SECOND, start_at, end_at) ELSE 0 END) / 3600, 0) scheduled_hours, "
                        + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' "
                        + "THEN TIMESTAMPDIFF(SECOND, start_at, end_at) ELSE 0 END) / 3600, 0) completed_hours "
                        + "FROM shifts WHERE start_at >= ? AND start_at < ?",
                (rs, row) -> new StaffAggregate(
                        rs.getLong("shift_count"), rs.getLong("scheduled_count"),
                        rs.getLong("completed_count"), rs.getLong("cancelled_count"),
                        rs.getBigDecimal("scheduled_hours"), rs.getBigDecimal("completed_hours")),
                from(range), to(range));
        List<HoursMetric> roles = jdbc.query(
                "SELECT operational_role metric_key, COUNT(*) shift_count, "
                        + "COALESCE(SUM(CASE WHEN status <> 'CANCELLED' "
                        + "THEN TIMESTAMPDIFF(SECOND, start_at, end_at) ELSE 0 END) / 3600, 0) hours "
                        + "FROM shifts WHERE start_at >= ? AND start_at < ? "
                        + "GROUP BY operational_role ORDER BY hours DESC, operational_role ASC",
                (rs, row) -> new HoursMetric(
                        rs.getString("metric_key"), rs.getLong("shift_count"),
                        hours(rs.getBigDecimal("hours"))),
                from(range), to(range));
        List<EmployeeHoursMetric> employees = jdbc.query(
                "SELECT e.id, e.employee_code, e.first_name, e.last_name, COUNT(*) shift_count, "
                        + "COALESCE(SUM(CASE WHEN s.status <> 'CANCELLED' "
                        + "THEN TIMESTAMPDIFF(SECOND, s.start_at, s.end_at) ELSE 0 END) / 3600, 0) scheduled_hours, "
                        + "COALESCE(SUM(CASE WHEN s.status = 'COMPLETED' "
                        + "THEN TIMESTAMPDIFF(SECOND, s.start_at, s.end_at) ELSE 0 END) / 3600, 0) completed_hours "
                        + "FROM shifts s JOIN employees e ON e.id = s.employee_id "
                        + "WHERE s.start_at >= ? AND s.start_at < ? "
                        + "GROUP BY e.id, e.employee_code, e.first_name, e.last_name "
                        + "ORDER BY scheduled_hours DESC, e.id ASC LIMIT ?",
                (rs, row) -> new EmployeeHoursMetric(
                        rs.getLong("id"), rs.getString("employee_code"),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getLong("shift_count"), hours(rs.getBigDecimal("scheduled_hours")),
                        hours(rs.getBigDecimal("completed_hours"))),
                from(range), to(range), top);
        return new StaffResponse(
                period(range), aggregate.count(), aggregate.scheduled(), aggregate.completed(),
                aggregate.cancelled(), hours(aggregate.scheduledHours()),
                hours(aggregate.completedHours()), roles, employees);
    }

    private InventoryItemMetric inventoryItem(ResultSet rs) throws SQLException {
        BigDecimal receipt = rs.getBigDecimal("receipt_quantity");
        BigDecimal usage = rs.getBigDecimal("usage_quantity");
        BigDecimal waste = rs.getBigDecimal("waste_quantity");
        BigDecimal adjustmentIn = rs.getBigDecimal("adjustment_in_quantity");
        BigDecimal adjustmentOut = rs.getBigDecimal("adjustment_out_quantity");
        BigDecimal net = receipt.add(adjustmentIn).subtract(usage).subtract(waste).subtract(adjustmentOut);
        BigDecimal onHand = rs.getBigDecimal("current_on_hand");
        return new InventoryItemMetric(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("unit"),
                quantity(receipt), quantity(usage), quantity(waste), quantity(adjustmentIn),
                quantity(adjustmentOut), quantity(net), quantity(onHand),
                onHand.compareTo(rs.getBigDecimal("reorder_threshold")) <= 0);
    }

    private List<CountMetric> counts(String sql, ReportRange range) {
        return jdbc.query(
                sql,
                (rs, row) -> new CountMetric(rs.getString("metric_key"), rs.getLong("metric_count")),
                from(range), to(range));
    }

    private Aggregate aggregate(String sql, ReportRange range) {
        return jdbc.queryForObject(
                sql,
                (rs, row) -> new Aggregate(rs.getLong(1), rs.getBigDecimal(2)),
                from(range), to(range));
    }

    private long count(String sql, ReportRange range) {
        return jdbc.queryForObject(sql, Long.class, from(range), to(range));
    }

    private BigDecimal decimal(String sql, ReportRange range) {
        return jdbc.queryForObject(sql, BigDecimal.class, from(range), to(range));
    }

    private Timestamp from(ReportRange range) {
        return Timestamp.from(range.from());
    }

    private Timestamp to(ReportRange range) {
        return Timestamp.from(range.to());
    }

    private Period period(ReportRange range) {
        return new Period(range.from(), range.to());
    }

    private String bucket(String column, ReportGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> "DATE_FORMAT(" + column + ", '%Y-%m-%d')";
            case WEEK -> "DATE_FORMAT(DATE_SUB(DATE(" + column
                    + "), INTERVAL WEEKDAY(" + column + ") DAY), '%Y-%m-%d')";
            case MONTH -> "DATE_FORMAT(" + column + ", '%Y-%m-01')";
        };
    }

    private BigDecimal average(BigDecimal amount, long count) {
        return count == 0 ? ZERO_MONEY : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal value) {
        return decimal(value == null ? ZERO_MONEY : value, 2);
    }

    private String quantity(BigDecimal value) {
        return decimal(value == null ? ZERO_QUANTITY : value, 3);
    }

    private String hours(BigDecimal value) {
        return decimal(value == null ? ZERO_HOURS : value, 2);
    }

    private String decimal(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record Aggregate(long count, BigDecimal amount) {}

    private record PaymentAggregate(
            long count, BigDecimal amount, long reconciledCount, BigDecimal reconciledAmount) {}

    private record KitchenAggregate(long count, long ready, long cancelled, BigDecimal averageMinutes) {}

    private record StaffAggregate(
            long count,
            long scheduled,
            long completed,
            long cancelled,
            BigDecimal scheduledHours,
            BigDecimal completedHours) {}
}
