import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { listCategories, listGroups, listItems } from '../features/menu/menuApi'
import { formatEur } from '../features/menu/money'
import type { MenuItem } from '../features/menu/menuTypes'
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

  const orderQuery = useQuery({
    queryKey: orderKeys.detail(id),
    queryFn: () => getOrder(id),
    enabled: Number.isInteger(id) && id > 0,
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
              disabled={statusMutation.isPending}
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
      {notice && (
        <div className="notice" role="status">
          <span>{notice}</span>
          <button type="button" aria-label="Dismiss notification" onClick={() => setNotice(null)}>
            &times;
          </button>
        </div>
      )}

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
            Taxes, discounts, tips, and payments are not part of Phase 4B.
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
    </div>
  )
}
