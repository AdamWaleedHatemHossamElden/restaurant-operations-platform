import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { formatEur } from '../features/menu/money'
import { OrderFormDialog } from '../features/orders/OrderFormDialog'
import type { OrderFormValues } from '../features/orders/orderSchemas'
import { createOrder, listOrders, orderKeys, orderRequestError } from '../features/orders/ordersApi'
import type { OrderFilters, OrderStatus } from '../features/orders/orderTypes'
import { listReservations } from '../features/reservations/reservationsApi'
import { localDateBoundaryToUtc } from '../features/reservations/reservationTime'
import { listTables } from '../features/tables/tablesApi'

const STATUSES: OrderStatus[] = ['OPEN', 'SUBMITTED', 'COMPLETED', 'CANCELLED']

function localDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
}

function todayValue(offsetDays = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function OrdersPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [orderNumber, setOrderNumber] = useState('')
  const [status, setStatus] = useState('')
  const [tableId, setTableId] = useState('')
  const [fromDate, setFromDate] = useState(todayValue(-7))
  const [toDate, setToDate] = useState(todayValue(1))
  const [sortBy, setSortBy] = useState<OrderFilters['sortBy']>('createdAt')
  const [direction, setDirection] = useState<OrderFilters['direction']>('DESC')
  const [creating, setCreating] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const filters = useMemo<OrderFilters>(
    () => ({
      orderNumber: orderNumber || undefined,
      status: status ? (status as OrderStatus) : undefined,
      tableId: tableId ? Number(tableId) : undefined,
      createdFrom: fromDate ? localDateBoundaryToUtc(fromDate) : undefined,
      createdTo: toDate ? localDateBoundaryToUtc(toDate, true) : undefined,
      sortBy,
      direction,
    }),
    [direction, fromDate, orderNumber, sortBy, status, tableId, toDate],
  )
  const ordersQuery = useQuery({
    queryKey: [...orderKeys.all, filters],
    queryFn: () => listOrders(filters),
  })
  const tablesQuery = useQuery({
    queryKey: ['tables', 'order-options'],
    queryFn: () => listTables({}),
  })
  const reservationsQuery = useQuery({
    queryKey: ['reservations', 'seated-order-options'],
    queryFn: () => listReservations({ status: 'SEATED', sortBy: 'startAt', direction: 'ASC' }),
  })
  const createMutation = useMutation({
    mutationFn: (values: OrderFormValues) =>
      createOrder({
        restaurantTableId: Number(values.restaurantTableId),
        reservationId: values.reservationId ? Number(values.reservationId) : null,
        notes: values.notes || null,
      }),
    onSuccess: async (order) => {
      await queryClient.invalidateQueries({ queryKey: orderKeys.all })
      setCreating(false)
      navigate(`/orders/${order.id}`)
    },
    onError: (error) => setFormError(orderRequestError(error)),
  })

  return (
    <div className="page orders-page">
      <section className="tables-hero orders-hero" aria-labelledby="orders-title">
        <div>
          <p className="eyebrow">Phase 4B order capture</p>
          <h1 id="orders-title">Orders</h1>
          <p>Capture table orders, preserve commercial snapshots, and follow service status.</p>
        </div>
        <button
          className="button button--primary"
          type="button"
          onClick={() => {
            setFormError(null)
            setCreating(true)
          }}
        >
          Create order
        </button>
      </section>

      <section className="order-filters" aria-label="Order filters">
        <div className="form-field">
          <label htmlFor="order-search">Order number</label>
          <input
            id="order-search"
            type="search"
            value={orderNumber}
            onChange={(event) => setOrderNumber(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="order-status-filter">Status</label>
          <select
            id="order-status-filter"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="">Any status</option>
            {STATUSES.map((value) => (
              <option value={value} key={value}>
                {value}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="order-table-filter">Table</label>
          <select
            id="order-table-filter"
            value={tableId}
            onChange={(event) => setTableId(event.target.value)}
          >
            <option value="">Any table</option>
            {tablesQuery.data?.map((table) => (
              <option value={table.id} key={table.id}>
                {table.tableNumber}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="order-from">Created from</label>
          <input
            id="order-from"
            type="date"
            value={fromDate}
            onChange={(event) => setFromDate(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="order-to">Created through</label>
          <input
            id="order-to"
            type="date"
            value={toDate}
            onChange={(event) => setToDate(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="order-sort">Sort by</label>
          <select
            id="order-sort"
            value={sortBy}
            onChange={(event) => setSortBy(event.target.value as OrderFilters['sortBy'])}
          >
            <option value="createdAt">Created time</option>
            <option value="orderNumber">Order number</option>
            <option value="status">Status</option>
            <option value="total">Total</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="order-direction">Direction</label>
          <select
            id="order-direction"
            value={direction}
            onChange={(event) => setDirection(event.target.value as OrderFilters['direction'])}
          >
            <option value="DESC">Descending</option>
            <option value="ASC">Ascending</option>
          </select>
        </div>
      </section>

      {ordersQuery.isPending && <div className="table-state">Loading orders&hellip;</div>}
      {ordersQuery.isError && (
        <div className="table-state table-state--error" role="alert">
          <p>Orders could not be loaded.</p>
          <button
            className="button button--secondary"
            type="button"
            onClick={() => ordersQuery.refetch()}
          >
            Try again
          </button>
        </div>
      )}
      {ordersQuery.data?.length === 0 && (
        <div className="table-state">
          <h2>No orders match these filters.</h2>
          <p>Create an order or adjust the filters.</p>
        </div>
      )}
      {ordersQuery.data && ordersQuery.data.length > 0 && (
        <div className="order-list" aria-label="Orders">
          {ordersQuery.data.map((order) => (
            <article className="order-card" key={order.id}>
              <div>
                <p className="eyebrow">{order.restaurantTable.tableNumber}</p>
                <h2>
                  <Link to={`/orders/${order.id}`}>{order.orderNumber}</Link>
                </h2>
                <p>
                  {order.reservation
                    ? `${order.reservation.reservationCode} · ${order.reservation.guestName}`
                    : 'Walk-in order'}
                </p>
              </div>
              <div className="order-card__status">
                <span className={`order-status order-status--${order.status.toLowerCase()}`}>
                  {order.status}
                </span>
                <span>
                  {order.itemCount} {order.itemCount === 1 ? 'item' : 'items'}
                </span>
              </div>
              <div className="order-card__total">
                <strong>{formatEur(order.total)}</strong>
                <time dateTime={order.createdAt}>{localDateTime(order.createdAt)}</time>
              </div>
              <Link className="button button--secondary button--link" to={`/orders/${order.id}`}>
                Open order
              </Link>
            </article>
          ))}
        </div>
      )}

      {creating && (
        <OrderFormDialog
          tables={tablesQuery.data ?? []}
          reservations={reservationsQuery.data ?? []}
          isSaving={createMutation.isPending}
          error={formError}
          onClose={() => setCreating(false)}
          onSave={async (values) => {
            try {
              await createMutation.mutateAsync(values)
            } catch {
              /* safe error is shown */
            }
          }}
        />
      )}
    </div>
  )
}
