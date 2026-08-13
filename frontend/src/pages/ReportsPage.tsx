import { useQuery } from '@tanstack/react-query'
import { useMemo, useState, type ReactNode } from 'react'

import { formatEur } from '../features/menu/money'
import {
  downloadReport,
  getInventoryReport,
  getKitchenReport,
  getMenuPerformanceReport,
  getOverviewReport,
  getPaymentsReport,
  getReservationsReport,
  getSalesReport,
  getStaffReport,
  reportKeys,
} from '../features/reports/reportsApi'
import {
  customRange,
  localDateInput,
  presetRange,
  type ReportPreset,
} from '../features/reports/reportTime'
import type { ExportableReport, ReportGroupBy, ReportRange } from '../features/reports/reportTypes'

type ReportTab =
  'overview' | 'sales' | 'menu' | 'payments' | 'reservations' | 'kitchen' | 'inventory' | 'staff'
type QueryState = { isPending: boolean; isError: boolean; refetch: () => unknown; data?: unknown }

const tabs: Array<{ id: ReportTab; label: string }> = [
  { id: 'overview', label: 'Overview' },
  { id: 'sales', label: 'Sales' },
  { id: 'menu', label: 'Menu' },
  { id: 'payments', label: 'Payments' },
  { id: 'reservations', label: 'Reservations' },
  { id: 'kitchen', label: 'Kitchen' },
  { id: 'inventory', label: 'Inventory' },
  { id: 'staff', label: 'Staff' },
]

function ReportState({ query, children }: { query: QueryState; children: ReactNode }) {
  if (query.isPending) return <div className="table-state">Loading report&hellip;</div>
  if (query.isError)
    return (
      <div className="table-state table-state--error" role="alert">
        <p>This report could not be loaded.</p>
        <button className="button button--secondary" type="button" onClick={() => query.refetch()}>
          Try again
        </button>
      </div>
    )
  return <>{children}</>
}

function Kpi({ label, value, note }: { label: string; value: string | number; note?: string }) {
  return (
    <article className="report-kpi">
      <span>{label}</span>
      <strong>{value}</strong>
      {note && <small>{note}</small>}
    </article>
  )
}

function Bars({ points }: { points: Array<{ label: string; value: number; detail: string }> }) {
  const maximum = Math.max(...points.map((point) => point.value), 1)
  if (points.length === 0) return <p className="report-empty">No activity in this period.</p>
  return (
    <div className="report-bars" role="img" aria-label="Time-series bar chart">
      {points.map((point) => (
        <div className="report-bar" key={point.label} title={`${point.label}: ${point.detail}`}>
          <span className="report-bar__value">{point.detail}</span>
          <span className="report-bar__track" aria-hidden="true">
            <span style={{ height: `${Math.max((point.value / maximum) * 100, 3)}%` }} />
          </span>
          <span>{point.label}</span>
        </div>
      ))}
    </div>
  )
}

function MetricTable({
  headers,
  rows,
  empty = 'No report rows in this period.',
}: {
  headers: string[]
  rows: Array<Array<ReactNode>>
  empty?: string
}) {
  if (rows.length === 0) return <p className="report-empty">{empty}</p>
  return (
    <div className="data-table-wrap">
      <table className="data-table report-table">
        <thead>
          <tr>
            {headers.map((header) => (
              <th key={header}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, cellIndex) => (
                <td key={cellIndex}>{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function label(value: string) {
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/^./, (first) => first.toUpperCase())
}

export function ReportsPage() {
  const [tab, setTab] = useState<ReportTab>('overview')
  const [preset, setPreset] = useState<ReportPreset | 'CUSTOM'>('LAST_30_DAYS')
  const [range, setRange] = useState<ReportRange>(() => presetRange('LAST_30_DAYS'))
  const [fromInput, setFromInput] = useState(() => localDateInput(range.from))
  const [toInput, setToInput] = useState(() => localDateInput(range.to))
  const [groupBy, setGroupBy] = useState<ReportGroupBy>('DAY')
  const [rangeError, setRangeError] = useState<string | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)

  const overview = useQuery({
    queryKey: reportKeys.section('overview', range),
    queryFn: () => getOverviewReport(range),
    enabled: tab === 'overview',
  })
  const sales = useQuery({
    queryKey: reportKeys.section('sales', range, groupBy),
    queryFn: () => getSalesReport(range, groupBy),
    enabled: tab === 'sales',
  })
  const menu = useQuery({
    queryKey: reportKeys.section('menu', range),
    queryFn: () => getMenuPerformanceReport(range),
    enabled: tab === 'menu',
  })
  const payments = useQuery({
    queryKey: reportKeys.section('payments', range, groupBy),
    queryFn: () => getPaymentsReport(range, groupBy),
    enabled: tab === 'payments',
  })
  const reservations = useQuery({
    queryKey: reportKeys.section('reservations', range),
    queryFn: () => getReservationsReport(range),
    enabled: tab === 'reservations',
  })
  const kitchen = useQuery({
    queryKey: reportKeys.section('kitchen', range),
    queryFn: () => getKitchenReport(range),
    enabled: tab === 'kitchen',
  })
  const inventory = useQuery({
    queryKey: reportKeys.section('inventory', range),
    queryFn: () => getInventoryReport(range),
    enabled: tab === 'inventory',
  })
  const staff = useQuery({
    queryKey: reportKeys.section('staff', range),
    queryFn: () => getStaffReport(range),
    enabled: tab === 'staff',
  })
  const displayPeriod = useMemo(
    () =>
      `${new Date(range.from).toLocaleDateString()} – ${new Date(range.to).toLocaleDateString()} (exclusive)`,
    [range],
  )

  const choosePreset = (next: ReportPreset) => {
    const nextRange = presetRange(next)
    setPreset(next)
    setRange(nextRange)
    setFromInput(localDateInput(nextRange.from))
    setToInput(localDateInput(nextRange.to))
    setRangeError(null)
  }
  const applyCustom = () => {
    const next = customRange(fromInput, toInput)
    if (!next) {
      setRangeError('Choose a valid start and exclusive end date.')
      return
    }
    setRange(next)
    setRangeError(null)
  }
  const exportFor = async (report: ExportableReport) => {
    setExporting(true)
    setExportError(null)
    try {
      await downloadReport(report, range, groupBy)
    } catch {
      setExportError('The CSV export could not be downloaded.')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="page reports-page">
      <section className="page-heading reports-hero">
        <div>
          <p className="eyebrow">Phase 9 · decision support</p>
          <h1>Reports & analytics</h1>
          <p>
            Read-only operational summaries built from authoritative historical and current records.
          </p>
        </div>
        <div className="report-period">
          <span>Selected period</span>
          <strong>{displayPeriod}</strong>
          <small>All API ranges are half-open [from, to).</small>
        </div>
      </section>

      <section className="report-controls" aria-label="Report period controls">
        <div className="report-presets">
          {(
            [
              ['TODAY', 'Today'],
              ['LAST_7_DAYS', 'Last 7 days'],
              ['LAST_30_DAYS', 'Last 30 days'],
              ['THIS_MONTH', 'This month'],
            ] as const
          ).map(([value, text]) => (
            <button
              type="button"
              className={preset === value ? 'is-active' : ''}
              onClick={() => choosePreset(value)}
              key={value}
            >
              {text}
            </button>
          ))}
        </div>
        <div className="report-custom-range">
          <label>
            Start date
            <input
              type="date"
              value={fromInput}
              onChange={(event) => {
                setFromInput(event.target.value)
                setPreset('CUSTOM')
              }}
            />
          </label>
          <label>
            End date (exclusive)
            <input
              type="date"
              value={toInput}
              onChange={(event) => {
                setToInput(event.target.value)
                setPreset('CUSTOM')
              }}
            />
          </label>
          <button className="button button--secondary" type="button" onClick={applyCustom}>
            Apply dates
          </button>
          <label>
            Group by
            <select
              value={groupBy}
              onChange={(event) => setGroupBy(event.target.value as ReportGroupBy)}
            >
              <option value="DAY">Day</option>
              <option value="WEEK">Week</option>
              <option value="MONTH">Month</option>
            </select>
          </label>
        </div>
        {rangeError && (
          <p className="form-error" role="alert">
            {rangeError}
          </p>
        )}
      </section>

      <div className="workspace-tabs reports-tabs" role="tablist" aria-label="Reports workspace">
        {tabs.map((item) => (
          <button
            key={item.id}
            role="tab"
            aria-selected={tab === item.id}
            className={tab === item.id ? 'is-active' : ''}
            onClick={() => setTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {exportError && (
        <p className="form-error" role="alert">
          {exportError}
        </p>
      )}

      {tab === 'overview' && (
        <ReportState query={overview}>
          {overview.data && (
            <section aria-labelledby="overview-heading">
              <h2 id="overview-heading">Operational overview</h2>
              <div className="report-kpis">
                <Kpi
                  label="Completed orders"
                  value={overview.data.completedOrders}
                  note={formatEur(overview.data.completedOrderValue)}
                />
                <Kpi
                  label="Payments received"
                  value={formatEur(overview.data.paymentsReceived)}
                  note={`${overview.data.paymentCount} payments`}
                />
                <Kpi label="Reservations" value={overview.data.reservations} />
                <Kpi label="Ready kitchen tickets" value={overview.data.readyKitchenTickets} />
                <Kpi label="Scheduled staff hours" value={overview.data.scheduledStaffHours} />
                <Kpi
                  label="Invoices issued"
                  value={overview.data.invoiceCount}
                  note={`${overview.data.reconciledPaymentCount} reconciled payments`}
                />
              </div>
              {overview.data.completedOrders === 0 &&
                overview.data.paymentCount === 0 &&
                overview.data.reservations === 0 &&
                overview.data.readyKitchenTickets === 0 && (
                  <p className="report-empty">No operational activity in this period.</p>
                )}
            </section>
          )}
        </ReportState>
      )}

      {tab === 'sales' && (
        <ReportState query={sales}>
          {sales.data && (
            <section aria-labelledby="sales-heading">
              <div className="report-section-heading">
                <div>
                  <h2 id="sales-heading">Completed-order sales</h2>
                  <p>Order value is distinct from payment receipts.</p>
                </div>
                <button
                  className="button button--secondary"
                  disabled={exporting}
                  onClick={() => exportFor('sales')}
                >
                  Export sales CSV
                </button>
              </div>
              <div className="report-kpis">
                <Kpi label="Completed orders" value={sales.data.completedOrders} />
                <Kpi
                  label="Completed order value"
                  value={formatEur(sales.data.completedOrderValue)}
                />
                <Kpi
                  label="Average order value"
                  value={formatEur(sales.data.averageCompletedOrderValue)}
                />
              </div>
              <Bars
                points={sales.data.series.map((point) => ({
                  label: point.bucket,
                  value: Number(point.amount),
                  detail: formatEur(point.amount),
                }))}
              />
              <h3>Sales by table</h3>
              <MetricTable
                headers={['Table', 'Orders', 'Order value']}
                rows={sales.data.byTable.map((item) => [
                  `${item.tableNumber} · ${item.displayName}`,
                  item.completedOrders,
                  formatEur(item.completedOrderValue),
                ])}
              />
              <h3>Highest-value completed orders</h3>
              <MetricTable
                headers={['Order', 'Completed', 'Total']}
                rows={sales.data.topOrders.map((item) => [
                  item.orderNumber,
                  new Date(item.completedAt).toLocaleString(),
                  formatEur(item.total),
                ])}
              />
            </section>
          )}
        </ReportState>
      )}

      {tab === 'menu' && (
        <ReportState query={menu}>
          {menu.data && (
            <section>
              <div className="report-section-heading">
                <div>
                  <h2>Menu performance</h2>
                  <p>Historical order-item snapshots preserve reporting when the menu changes.</p>
                </div>
                <button
                  className="button button--secondary"
                  disabled={exporting}
                  onClick={() => exportFor('menu')}
                >
                  Export menu CSV
                </button>
              </div>
              <MetricTable
                headers={['Item', 'Quantity', 'Orders', 'Line value', 'Avg. per order']}
                rows={menu.data.items.map((item) => [
                  <span key={item.menuItemId}>
                    <strong>{item.itemName}</strong>
                    <small>{item.itemCode}</small>
                  </span>,
                  item.quantitySold,
                  item.completedOrders,
                  formatEur(item.completedOrderLineValue),
                  item.averageQuantityPerOrder,
                ])}
              />
            </section>
          )}
        </ReportState>
      )}

      {tab === 'payments' && (
        <ReportState query={payments}>
          {payments.data && (
            <section>
              <div className="report-section-heading">
                <div>
                  <h2>Payment reconciliation</h2>
                  <p>Successful receipts grouped by received timestamp.</p>
                </div>
                <button
                  className="button button--secondary"
                  disabled={exporting}
                  onClick={() => exportFor('payments')}
                >
                  Export payments CSV
                </button>
              </div>
              <div className="report-kpis">
                <Kpi
                  label="Payments received"
                  value={formatEur(payments.data.paymentsReceived)}
                  note={`${payments.data.paymentCount} payments`}
                />
                <Kpi
                  label="Reconciled"
                  value={formatEur(payments.data.reconciledAmount)}
                  note={`${payments.data.reconciledCount} payments`}
                />
                <Kpi
                  label="Unreconciled"
                  value={formatEur(payments.data.unreconciledAmount)}
                  note={`${payments.data.unreconciledCount} payments`}
                />
              </div>
              <Bars
                points={payments.data.series.map((point) => ({
                  label: point.bucket,
                  value: Number(point.amount),
                  detail: formatEur(point.amount),
                }))}
              />
              <MetricTable
                headers={['Method', 'Count', 'Amount']}
                rows={payments.data.byMethod.map((item) => [
                  label(item.method),
                  item.count,
                  formatEur(item.amount),
                ])}
              />
            </section>
          )}
        </ReportState>
      )}

      {tab === 'reservations' && (
        <ReportState query={reservations}>
          {reservations.data && (
            <section>
              <div className="report-section-heading">
                <h2>Reservation activity</h2>
                <button
                  className="button button--secondary"
                  disabled={exporting}
                  onClick={() => exportFor('reservations')}
                >
                  Export reservations CSV
                </button>
              </div>
              <div className="report-kpis">
                <Kpi label="Reservations" value={reservations.data.reservations} />
                <Kpi label="Planned guests" value={reservations.data.plannedGuests} />
                <Kpi label="Average party" value={reservations.data.averagePartySize} />
              </div>
              <h3>Status breakdown</h3>
              <MetricTable
                headers={['Status', 'Reservations']}
                rows={reservations.data.byStatus.map((item) => [label(item.key), item.count])}
              />
              <h3>Table allocation</h3>
              <MetricTable
                headers={['Table', 'Reservations', 'Guests']}
                rows={reservations.data.byTable.map((item) => [
                  item.tableNumber ? `${item.tableNumber} · ${item.displayName}` : 'Unassigned',
                  item.reservations,
                  item.plannedGuests,
                ])}
              />
            </section>
          )}
        </ReportState>
      )}

      {tab === 'kitchen' && (
        <ReportState query={kitchen}>
          {kitchen.data && (
            <section>
              <h2>Kitchen throughput</h2>
              <div className="report-kpis">
                <Kpi label="Tickets created" value={kitchen.data.ticketsCreated} />
                <Kpi label="Ready tickets" value={kitchen.data.readyTickets} />
                <Kpi label="Cancelled tickets" value={kitchen.data.cancelledTickets} />
                <Kpi
                  label="Average preparation"
                  value={`${kitchen.data.averagePreparationMinutes} min`}
                  note="Ready tickets only"
                />
              </div>
              <h3>Ticket status</h3>
              <MetricTable
                headers={['Status', 'Tickets']}
                rows={kitchen.data.byStatus.map((item) => [label(item.key), item.count])}
              />
              <h3>Prepared item volume</h3>
              <MetricTable
                headers={['Item', 'Quantity']}
                rows={kitchen.data.itemPreparation.map((item) => [
                  `${item.itemCode} · ${item.itemName}`,
                  item.quantity,
                ])}
              />
            </section>
          )}
        </ReportState>
      )}

      {tab === 'inventory' && (
        <ReportState query={inventory}>
          {inventory.data && (
            <section>
              <div className="report-section-heading">
                <div>
                  <h2>Inventory movement</h2>
                  <p>Quantities remain separated by canonical unit.</p>
                </div>
                <button
                  className="button button--secondary"
                  disabled={exporting}
                  onClick={() => exportFor('inventory')}
                >
                  Export inventory CSV
                </button>
              </div>
              <div className="report-kpis">
                <Kpi label="Movements" value={inventory.data.movementCount} />
                <Kpi label="Currently low stock" value={inventory.data.currentLowStockItems} />
              </div>
              <MetricTable
                headers={['Item', 'Unit', 'Receipt', 'Usage', 'Waste', 'Net', 'On hand']}
                rows={inventory.data.items.map((item) => [
                  <span key={item.inventoryItemId}>
                    <strong>{item.name}</strong>
                    <small>
                      {item.code}
                      {item.currentlyLowStock ? ' · Low stock' : ''}
                    </small>
                  </span>,
                  item.unit,
                  item.receipt,
                  item.usage,
                  item.waste,
                  item.netMovement,
                  item.currentOnHand,
                ])}
              />
            </section>
          )}
        </ReportState>
      )}

      {tab === 'staff' && (
        <ReportState query={staff}>
          {staff.data && (
            <section>
              <div className="report-section-heading">
                <h2>Staff scheduling</h2>
                <button
                  className="button button--secondary"
                  disabled={exporting}
                  onClick={() => exportFor('staff')}
                >
                  Export staff CSV
                </button>
              </div>
              <div className="report-kpis">
                <Kpi label="Shifts" value={staff.data.shiftCount} />
                <Kpi label="Scheduled hours" value={staff.data.scheduledHours} />
                <Kpi label="Completed shift hours" value={staff.data.completedShiftHours} />
                <Kpi label="Cancelled shifts" value={staff.data.cancelledCount} />
              </div>
              <h3>Hours by role</h3>
              <MetricTable
                headers={['Role', 'Shifts', 'Hours']}
                rows={staff.data.hoursByRole.map((item) => [
                  label(item.key),
                  item.shifts,
                  item.hours,
                ])}
              />
              <h3>Hours by employee</h3>
              <MetricTable
                headers={['Employee', 'Shifts', 'Scheduled', 'Completed']}
                rows={staff.data.hoursByEmployee.map((item) => [
                  `${item.employeeCode} · ${item.employeeName}`,
                  item.shifts,
                  item.scheduledHours,
                  item.completedShiftHours,
                ])}
              />
            </section>
          )}
        </ReportState>
      )}
    </div>
  )
}
