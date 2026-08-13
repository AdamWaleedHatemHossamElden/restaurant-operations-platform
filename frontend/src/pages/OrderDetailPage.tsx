import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { listCategories, listGroups, listItems } from '../features/menu/menuApi'
import { formatEur } from '../features/menu/money'
import type { MenuItem } from '../features/menu/menuTypes'
import { getKitchenTicketByOrder, kitchenKeys } from '../features/kitchen/kitchenApi'
import { OrderFormDialog } from '../features/orders/OrderFormDialog'
import { OrderItemDialog } from '../features/orders/OrderItemDialog'
import type { OrderFormValues, OrderItemFormValues } from '../features/orders/orderSchemas'
import {
  addOrderItem,
  getOrder,
  orderKeys,
  orderRequestError,
  removeOrderItem,
  transitionOrder,
  updateOrder,
  updateOrderItem,
} from '../features/orders/ordersApi'
import type {
  ModifierSelectionInput,
  OrderItem,
  OrderStatus,
  RestaurantOrder,
} from '../features/orders/orderTypes'
import { listReservations } from '../features/reservations/reservationsApi'
import { listTables } from '../features/tables/tablesApi'
import { InvoiceDocument, RecordPaymentDialog } from '../features/payments/PaymentDialogs'
import {
  getInvoice,
  getOrderPaymentSummary,
  invoiceKeys,
  issueInvoice,
  paymentKeys,
  paymentRequestError,
  recordPayment,
} from '../features/payments/paymentsApi'
import type { PaymentInput } from '../features/payments/paymentTypes'

type ItemEditor = { menuItem: MenuItem; orderItem: OrderItem | null }

const TRANSITIONS: Partial<Record<OrderStatus, { status: OrderStatus; label: string }[]>> = {
  OPEN: [
    { status: 'SUBMITTED', label: 'Submit order' },
    { status: 'CANCELLED', label: 'Cancel order' },
  ],
  SUBMITTED: [
    { status: 'COMPLETED', label: 'Complete order' },
    { status: 'CANCELLED', label: 'Cancel order' },
  ],
}

function localDateTime(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
}

export function OrderDetailPage() {
  const { orderId } = useParams()
  const id = Number(orderId)
  const queryClient = useQueryClient()
  const [categoryId, setCategoryId] = useState('')
  const [search, setSearch] = useState('')
  const [editor, setEditor] = useState<ItemEditor | null>(null)
  const [editingDetails, setEditingDetails] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [paymentKey, setPaymentKey] = useState<string | null>(null)
  const [showInvoice, setShowInvoice] = useState(false)
  const [paymentError, setPaymentError] = useState<string | null>(null)

  const orderQuery = useQuery({
    queryKey: orderKeys.detail(id),
    queryFn: () => getOrder(id),
    enabled: Number.isInteger(id) && id > 0,
  })
  const kitchenQuery = useQuery({
    queryKey: kitchenKeys.order(id),
    queryFn: () => getKitchenTicketByOrder(id),
    enabled:
      Number.isInteger(id) &&
      id > 0 &&
      Boolean(orderQuery.data && orderQuery.data.status !== 'OPEN'),
  })
  const categoriesQuery = useQuery({
    queryKey: ['menu', 'categories', 'order-capture'],
    queryFn: () => listCategories({ active: true }),
  })
  const itemsQuery = useQuery({
    queryKey: ['menu', 'items', 'order-capture'],
    queryFn: () => listItems({}),
  })
  const groupsQuery = useQuery({
    queryKey: ['menu', 'modifier-groups', 'order-capture'],
    queryFn: () => listGroups({}),
  })
  const tablesQuery = useQuery({
    queryKey: ['tables', 'order-options'],
    queryFn: () => listTables({}),
  })
  const reservationsQuery = useQuery({
    queryKey: ['reservations', 'seated-order-options'],
    queryFn: () => listReservations({ status: 'SEATED', sortBy: 'startAt', direction: 'ASC' }),
  })
  const paymentSummaryQuery = useQuery({
    queryKey: paymentKeys.summary(id),
    queryFn: () => getOrderPaymentSummary(id),
    enabled: Number.isInteger(id) && id > 0 && orderQuery.data?.status === 'COMPLETED',
  })
  const invoiceQuery = useQuery({
    queryKey: invoiceKeys.detail(paymentSummaryQuery.data?.invoiceId ?? 0),
    queryFn: () => getInvoice(paymentSummaryQuery.data!.invoiceId!),
    enabled: showInvoice && Boolean(paymentSummaryQuery.data?.invoiceId),
  })

  const availableItems = useMemo(
    () =>
      (itemsQuery.data ?? []).filter(
        (item) =>
          item.effectivelyAvailable &&
          (!categoryId || item.category.id === Number(categoryId)) &&
          (!search || `${item.code} ${item.name}`.toLowerCase().includes(search.toLowerCase())),
      ),
    [categoryId, itemsQuery.data, search],
  )

  const applyServerOrder = async (order: RestaurantOrder) => {
    queryClient.setQueryData(orderKeys.detail(order.id), order)
    await queryClient.invalidateQueries({ queryKey: orderKeys.all })
  }
  const itemMutation = useMutation({
    mutationFn: ({
      order,
      menuItem,
      orderItem,
      values,
      selections,
    }: {
      order: RestaurantOrder
      menuItem: MenuItem
      orderItem: OrderItem | null
      values: OrderItemFormValues
      selections: ModifierSelectionInput[] | undefined
    }) =>
      orderItem
        ? updateOrderItem(order, orderItem, {
            quantity: values.quantity,
            notes: values.notes || null,
            ...(selections === undefined ? {} : { modifierSelections: selections }),
          })
        : addOrderItem(order, {
            menuItemId: menuItem.id,
            quantity: values.quantity,
            notes: values.notes || null,
            modifierSelections: selections ?? [],
          }),
    onSuccess: async (order) => {
      await applyServerOrder(order)
      setEditor(null)
      setFormError(null)
      setNotice('The backend-authoritative order totals were updated.')
    },
    onError: (error) => setFormError(orderRequestError(error)),
  })
  const detailsMutation = useMutation({
    mutationFn: ({ order, values }: { order: RestaurantOrder; values: OrderFormValues }) =>
      updateOrder(order, {
        restaurantTableId: Number(values.restaurantTableId),
        reservationId: values.reservationId ? Number(values.reservationId) : null,
        notes: values.notes || null,
      }),
    onSuccess: async (order) => {
      await applyServerOrder(order)
      setEditingDetails(false)
      setFormError(null)
      setNotice('Order details updated.')
    },
    onError: (error) => setFormError(orderRequestError(error)),
  })
  const removeMutation = useMutation({
    mutationFn: ({ order, item }: { order: RestaurantOrder; item: OrderItem }) =>
      removeOrderItem(order, item),
    onSuccess: async (order) => {
      await applyServerOrder(order)
      setNotice('Item removed and totals recalculated.')
    },
    onError: (error) => setNotice(orderRequestError(error)),
  })
  const statusMutation = useMutation({
    mutationFn: ({ order, status }: { order: RestaurantOrder; status: OrderStatus }) =>
      transitionOrder(order, status),
    onSuccess: async (order) => {
      await applyServerOrder(order)
      setNotice(`Order is now ${order.status}.`)
    },
    onError: (error) => setNotice(orderRequestError(error)),
  })
  const paymentMutation = useMutation({
    mutationFn: ({ input, key }: { input: PaymentInput; key: string }) =>
      recordPayment(id, input, key),
    onSuccess: async () => {
      setPaymentKey(null)
      setPaymentError(null)
      setNotice('Confirmed payment recorded. The outstanding amount was recalculated.')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: paymentKeys.all }),
        queryClient.invalidateQueries({ queryKey: paymentKeys.summary(id) }),
      ])
    },
    onError: async (error) => {
      setPaymentError(paymentRequestError(error))
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        await queryClient.invalidateQueries({ queryKey: paymentKeys.summary(id) })
      }
    },
  })
  const invoiceMutation = useMutation({
    mutationFn: () => issueInvoice(id),
    onSuccess: async (invoice) => {
      queryClient.setQueryData(invoiceKeys.detail(invoice.id), invoice)
      setShowInvoice(true)
      setNotice('Immutable invoice issued from the order snapshots.')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: invoiceKeys.all }),
        queryClient.invalidateQueries({ queryKey: paymentKeys.summary(id) }),
      ])
    },
    onError: (error) => setNotice(paymentRequestError(error)),
  })

  if (!Number.isInteger(id) || id <= 0)
    return <div className="page table-state table-state--error">Invalid order.</div>
  if (orderQuery.isPending) return <div className="page table-state">Loading order&hellip;</div>
  if (orderQuery.isError || !orderQuery.data) {
    return (
      <div className="page table-state table-state--error" role="alert">
        <h1>Order unavailable</h1>
        <button className="button button--secondary" onClick={() => orderQuery.refetch()}>
          Try again
        </button>
      </div>
    )
  }

  const order = orderQuery.data
  const mutable = order.status === 'OPEN'
  const kitchenTicket = kitchenQuery.data

  return (
    <div className="page order-detail-page">
      <Link className="back-link" to="/orders">
        &larr; Back to orders
      </Link>
      <section className="order-detail-header" aria-labelledby="order-title">
        <div>
          <p className="eyebrow">
            {order.restaurantTable.tableNumber} &middot; {order.restaurantTable.section}
          </p>
          <h1 id="order-title">{order.orderNumber}</h1>
          <p>
            {order.reservation
              ? `${order.reservation.reservationCode} · ${order.reservation.guestName}`
              : 'Walk-in order'}
          </p>
        </div>
        <div className="order-detail-header__total">
          <span className={`order-status order-status--${order.status.toLowerCase()}`}>
            {order.status}
          </span>
          <strong>{formatEur(order.total)}</strong>
        </div>
        <div className="order-detail-actions">
          {mutable && (
            <button
              className="button button--secondary"
              type="button"
              onClick={() => {
                setFormError(null)
                setEditingDetails(true)
              }}
            >
              Edit details
            </button>
          )}
          {(TRANSITIONS[order.status] ?? []).map((action) => (
            <button
              className={`button ${action.status === 'CANCELLED' ? 'button--danger' : 'button--primary'}`}
              type="button"
              disabled={
                statusMutation.isPending ||
                (action.status === 'COMPLETED' && kitchenTicket?.status !== 'READY')
              }
              onClick={() => {
                if (window.confirm(`${action.label} ${order.orderNumber}?`))
                  statusMutation.mutate({ order, status: action.status })
              }}
              key={action.status}
            >
              {action.label}
            </button>
          ))}
        </div>
      </section>
      {order.status !== 'OPEN' && (
        <section className="order-kitchen-summary" aria-labelledby="order-kitchen-title">
          <div>
            <p className="eyebrow">Preparation</p>
            <h2 id="order-kitchen-title">Kitchen</h2>
          </div>
          {kitchenQuery.isPending && <span>Loading kitchen state&hellip;</span>}
          {kitchenQuery.isError && <span>Kitchen state is temporarily unavailable.</span>}
          {!kitchenQuery.isPending && !kitchenQuery.isError && kitchenTicket && (
            <>
              <span
                className={`kitchen-status kitchen-status--${kitchenTicket.status.toLowerCase()}`}
              >
                {kitchenTicket.status}
              </span>
              <p>
                {kitchenTicket.items.filter((item) => item.status === 'READY').length} of{' '}
                {kitchenTicket.items.length} items ready
              </p>
              <Link className="button button--secondary button--link" to="/kitchen">
                Open kitchen
              </Link>
            </>
          )}
          {!kitchenQuery.isPending && !kitchenQuery.isError && !kitchenTicket && (
            <span>No kitchen ticket is associated with this historical order.</span>
          )}
          {order.status === 'SUBMITTED' && kitchenTicket?.status !== 'READY' && (
            <p className="field-hint">Kitchen preparation must be READY before completion.</p>
          )}
        </section>
      )}
      {notice && (
        <div className="notice" role="status">
          <span>{notice}</span>
          <button type="button" aria-label="Dismiss notification" onClick={() => setNotice(null)}>
            &times;
          </button>
        </div>
      )}

      <section className="order-payment-summary" aria-labelledby="order-payment-title">
        <div className="order-section-heading">
          <div>
            <p className="eyebrow">Settlement</p>
            <h2 id="order-payment-title">Payment summary</h2>
          </div>
          {paymentSummaryQuery.data && (
            <span
              className={`payment-state payment-state--${paymentSummaryQuery.data.paymentState.toLowerCase()}`}
            >
              {paymentSummaryQuery.data.paymentState.replace('_', ' ')}
            </span>
          )}
        </div>
        {order.status !== 'COMPLETED' && (
          <p>
            Payments and invoices become available after the order reaches COMPLETED. This order
            remains read-only for settlement.
          </p>
        )}
        {order.status === 'COMPLETED' && paymentSummaryQuery.isPending && (
          <div className="table-state">Loading payment summary&hellip;</div>
        )}
        {order.status === 'COMPLETED' && paymentSummaryQuery.isError && (
          <div className="table-state table-state--error" role="alert">
            Payment summary could not be loaded.
          </div>
        )}
        {paymentSummaryQuery.data && (
          <>
            <dl className="payment-summary-values">
              <div>
                <dt>Order total</dt>
                <dd>{formatEur(paymentSummaryQuery.data.orderTotal)}</dd>
              </div>
              <div>
                <dt>Paid</dt>
                <dd>{formatEur(paymentSummaryQuery.data.paidAmount)}</dd>
              </div>
              <div>
                <dt>Outstanding</dt>
                <dd>{formatEur(paymentSummaryQuery.data.outstandingAmount)}</dd>
              </div>
            </dl>
            <div className="payment-history">
              {paymentSummaryQuery.data.payments.length === 0 ? (
                <p>No confirmed payments recorded.</p>
              ) : (
                paymentSummaryQuery.data.payments.map((payment) => (
                  <div key={payment.id}>
                    <span>
                      <strong>{payment.paymentNumber}</strong>
                      <small>
                        {payment.method.replace('_', ' ')} ·{' '}
                        {new Date(payment.receivedAt).toLocaleString()}
                      </small>
                    </span>
                    <strong>{formatEur(payment.amount)}</strong>
                  </div>
                ))
              )}
            </div>
            <div className="order-detail-actions">
              {paymentSummaryQuery.data.paymentState !== 'PAID' && (
                <button
                  className="button button--primary"
                  type="button"
                  onClick={() => {
                    setPaymentError(null)
                    setPaymentKey(crypto.randomUUID())
                  }}
                >
                  Record confirmed payment
                </button>
              )}
              {paymentSummaryQuery.data.paymentState === 'PAID' &&
                !paymentSummaryQuery.data.invoiceId && (
                  <button
                    className="button button--primary"
                    type="button"
                    disabled={invoiceMutation.isPending}
                    onClick={() => invoiceMutation.mutate()}
                  >
                    {invoiceMutation.isPending ? 'Issuing…' : 'Issue invoice'}
                  </button>
                )}
              {paymentSummaryQuery.data.invoiceId && (
                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => setShowInvoice(true)}
                >
                  View {paymentSummaryQuery.data.invoiceNumber}
                </button>
              )}
              <Link className="button button--secondary button--link" to="/payments">
                Payments workspace
              </Link>
            </div>
          </>
        )}
        {showInvoice && invoiceQuery.isPending && (
          <div className="table-state">Loading invoice&hellip;</div>
        )}
        {showInvoice && invoiceQuery.data && (
          <div className="invoice-view">
            <div className="invoice-view__actions">
              <button
                className="button button--secondary"
                type="button"
                onClick={() => setShowInvoice(false)}
              >
                Hide invoice
              </button>
              <button
                className="button button--primary"
                type="button"
                onClick={() => window.print()}
              >
                Print invoice
              </button>
            </div>
            <InvoiceDocument invoice={invoiceQuery.data} />
          </div>
        )}
      </section>

      <div className="order-capture-layout">
        <section className="menu-browser" aria-labelledby="menu-browser-title">
          <div className="order-section-heading">
            <div>
              <p className="eyebrow">Current catalog</p>
              <h2 id="menu-browser-title">Menu browser</h2>
            </div>
          </div>
          {!mutable && <div className="form-alert">Submitted orders are commercially frozen.</div>}
          <div className="menu-browser__filters">
            <div className="form-field">
              <label htmlFor="order-menu-category">Category</label>
              <select
                id="order-menu-category"
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
              >
                <option value="">All categories</option>
                {categoriesQuery.data?.map((category) => (
                  <option value={category.id} key={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label htmlFor="order-menu-search">Search menu</label>
              <input
                id="order-menu-search"
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
          </div>
          {itemsQuery.isPending && <div className="table-state">Loading menu&hellip;</div>}
          {itemsQuery.isError && (
            <div className="table-state table-state--error">Menu could not be loaded.</div>
          )}
          {availableItems.length === 0 && !itemsQuery.isPending && (
            <div className="table-state">No available menu items match.</div>
          )}
          <div className="order-menu-grid">
            {availableItems.map((item) => (
              <article className="order-menu-card" key={item.id}>
                <div>
                  <p className="eyebrow">{item.code}</p>
                  <h3>{item.name}</h3>
                  <p>{item.description || item.category.name}</p>
                </div>
                <div>
                  <strong>{formatEur(item.basePrice)}</strong>
                  {item.modifierGroups.some((group) => group.active) && <span>Modifiers</span>}
                </div>
                <button
                  className="button button--primary"
                  type="button"
                  disabled={!mutable}
                  onClick={() => {
                    setFormError(null)
                    setEditor({ menuItem: item, orderItem: null })
                  }}
                >
                  Add
                </button>
              </article>
            ))}
          </div>
        </section>

        <section className="order-cart" aria-labelledby="current-order-title">
          <div className="order-section-heading">
            <div>
              <p className="eyebrow">Server snapshots</p>
              <h2 id="current-order-title">Current order</h2>
            </div>
            <strong>{formatEur(order.total)}</strong>
          </div>
          {order.items.length === 0 && (
            <div className="table-state">
              <h3>No items yet.</h3>
              <p>Add at least one item before submitting.</p>
            </div>
          )}
          <div className="order-line-list">
            {order.items.map((item) => {
              const menuItem = itemsQuery.data?.find(
                (candidate) => candidate.id === item.menuItemId,
              )
              return (
                <article className="order-line" key={item.id}>
                  <div className="order-line__heading">
                    <div>
                      <span>{item.quantity}&times;</span>
                      <h3>{item.itemName}</h3>
                      <small>{item.itemCode}</small>
                    </div>
                    <strong>{formatEur(item.lineTotal)}</strong>
                  </div>
                  {item.modifiers.length > 0 && (
                    <ul>
                      {item.modifiers.map((modifier) => (
                        <li key={modifier.id}>
                          {modifier.groupName}: {modifier.optionName}{' '}
                          <span>+{formatEur(modifier.priceAdjustment)}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                  {item.notes && <p className="order-line__notes">{item.notes}</p>}
                  <div className="order-line__pricing">
                    <span>{formatEur(item.unitTotal)} each</span>
                    <span>Snapshot base {formatEur(item.basePrice)}</span>
                  </div>
                  {mutable && (
                    <div className="order-line__actions">
                      <button
                        className="button button--secondary"
                        type="button"
                        disabled={!menuItem}
                        onClick={() =>
                          menuItem && (setFormError(null), setEditor({ menuItem, orderItem: item }))
                        }
                      >
                        Edit
                      </button>
                      <button
                        className="button button--danger"
                        type="button"
                        disabled={removeMutation.isPending}
                        onClick={() => {
                          if (window.confirm(`Remove ${item.itemName} from this order?`))
                            removeMutation.mutate({ order, item })
                        }}
                      >
                        Remove
                      </button>
                    </div>
                  )}
                </article>
              )
            })}
          </div>
          <dl className="order-totals">
            <div>
              <dt>Subtotal</dt>
              <dd>{formatEur(order.subtotal)}</dd>
            </div>
            <div>
              <dt>Total</dt>
              <dd>{formatEur(order.total)}</dd>
            </div>
          </dl>
          <p className="field-hint">
            Taxes, discounts, and tips are not included. Payment totals remain server-authoritative.
          </p>
        </section>
      </div>

      <section className="order-history" aria-labelledby="order-history-title">
        <div className="order-section-heading">
          <div>
            <p className="eyebrow">Business history</p>
            <h2 id="order-history-title">Status timeline</h2>
          </div>
        </div>
        <ol>
          {order.history.map((entry) => (
            <li key={entry.id}>
              <span aria-hidden="true" />
              <div>
                <strong>{entry.toStatus}</strong>
                <time dateTime={entry.changedAt}>{localDateTime(entry.changedAt)}</time>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {editor && (
        <OrderItemDialog
          menuItem={editor.menuItem}
          groups={groupsQuery.data ?? []}
          orderItem={editor.orderItem}
          isSaving={itemMutation.isPending}
          error={formError}
          onClose={() => setEditor(null)}
          onSave={async (values, selections) => {
            try {
              await itemMutation.mutateAsync({
                order,
                menuItem: editor.menuItem,
                orderItem: editor.orderItem,
                values,
                selections,
              })
            } catch {
              /* safe error is shown */
            }
          }}
        />
      )}
      {editingDetails && (
        <OrderFormDialog
          order={order}
          tables={tablesQuery.data ?? []}
          reservations={reservationsQuery.data ?? []}
          isSaving={detailsMutation.isPending}
          error={formError}
          onClose={() => setEditingDetails(false)}
          onSave={async (values) => {
            try {
              await detailsMutation.mutateAsync({ order, values })
            } catch {
              /* safe error is shown */
            }
          }}
        />
      )}
      {paymentKey && paymentSummaryQuery.data && (
        <RecordPaymentDialog
          outstanding={paymentSummaryQuery.data.outstandingAmount}
          pending={paymentMutation.isPending}
          error={paymentError}
          onClose={() => setPaymentKey(null)}
          onSave={async (input) => {
            try {
              await paymentMutation.mutateAsync({ input, key: paymentKey })
            } catch {
              /* safe error is shown and the same idempotency key is retained */
            }
          }}
        />
      )}
    </div>
  )
}
