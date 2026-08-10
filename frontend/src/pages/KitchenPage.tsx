import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import {
  kitchenKeys,
  kitchenRequestError,
  listKitchenTickets,
  transitionKitchenItem,
} from '../features/kitchen/kitchenApi'
import { useKitchenRealtimeState } from '../features/kitchen/kitchenRealtimeContext'
import type {
  KitchenItem,
  KitchenItemStatus,
  KitchenTicket,
  KitchenTicketStatus,
} from '../features/kitchen/kitchenTypes'
import { orderKeys } from '../features/orders/ordersApi'

const ACTIVE_STATUSES: KitchenTicketStatus[] = ['QUEUED', 'PREPARING', 'READY']

function localTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { timeStyle: 'short' }).format(new Date(value))
}

function elapsed(value: string) {
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60_000))
  if (minutes < 60) return `${minutes} min waiting`
  const hours = Math.floor(minutes / 60)
  return `${hours}h ${minutes % 60}m waiting`
}

function nextItemStatus(item: KitchenItem): KitchenItemStatus | null {
  if (item.status === 'QUEUED') return 'PREPARING'
  if (item.status === 'PREPARING') return 'READY'
  return null
}

function TicketCard({
  ticket,
  onProgress,
  isSaving,
}: {
  ticket: KitchenTicket
  onProgress: (ticket: KitchenTicket, item: KitchenItem, status: KitchenItemStatus) => void
  isSaving: boolean
}) {
  const readOnly = ticket.status === 'CANCELLED'
  return (
    <article className={`kitchen-ticket kitchen-ticket--${ticket.status.toLowerCase()}`}>
      <header className="kitchen-ticket__header">
        <div>
          <p className="eyebrow">Table {ticket.restaurantTable.tableNumber}</p>
          <h3>
            <Link to={`/orders/${ticket.orderId}`}>{ticket.orderNumber}</Link>
          </h3>
        </div>
        <span className={`kitchen-status kitchen-status--${ticket.status.toLowerCase()}`}>
          {ticket.status}
        </span>
      </header>
      <div className="kitchen-ticket__timing">
        <time dateTime={ticket.submittedAt}>Submitted {localTime(ticket.submittedAt)}</time>
        <strong>{elapsed(ticket.submittedAt)}</strong>
      </div>
      {ticket.reservation && <p>Reservation {ticket.reservation.reservationCode}</p>}
      {readOnly && <p className="form-alert">Cancelled — preparation controls are disabled.</p>}
      <ol className="kitchen-item-list">
        {ticket.items.map((item) => {
          const next = nextItemStatus(item)
          return (
            <li className="kitchen-item" key={item.id}>
              <div className="kitchen-item__heading">
                <strong>
                  <span>{item.quantity}×</span> {item.itemName}
                </strong>
                <span
                  className={`kitchen-item-state kitchen-item-state--${item.status.toLowerCase()}`}
                >
                  {item.status}
                </span>
              </div>
              <small>{item.itemCode}</small>
              {item.modifiers.length > 0 && (
                <ul className="kitchen-modifiers">
                  {item.modifiers.map((modifier, index) => (
                    <li key={`${modifier.groupName}-${modifier.optionName}-${index}`}>
                      {modifier.groupName}: {modifier.optionName}
                    </li>
                  ))}
                </ul>
              )}
              {item.notes && <p className="kitchen-item__note">Note: {item.notes}</p>}
              {!readOnly && next && (
                <button
                  className="button button--primary kitchen-item__action"
                  type="button"
                  disabled={isSaving}
                  onClick={() => onProgress(ticket, item, next)}
                >
                  {next === 'PREPARING' ? 'Start preparing' : 'Mark ready'}
                </button>
              )}
            </li>
          )
        })}
      </ol>
    </article>
  )
}

export function KitchenPage() {
  const queryClient = useQueryClient()
  const realtimeState = useKitchenRealtimeState()
  const [view, setView] = useState<'ACTIVE' | 'ALL' | KitchenTicketStatus>('ACTIVE')
  const [orderNumber, setOrderNumber] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const filters = useMemo(
    () => ({
      status: view !== 'ACTIVE' && view !== 'ALL' ? view : undefined,
      orderNumber: orderNumber || undefined,
      includeCancelled: view === 'ALL' || view === 'CANCELLED',
      sortBy: 'submittedAt' as const,
      direction: 'ASC' as const,
    }),
    [orderNumber, view],
  )
  const ticketsQuery = useQuery({
    queryKey: [...kitchenKeys.queues(), filters],
    queryFn: () => listKitchenTickets(filters),
  })
  const transitionMutation = useMutation({
    mutationFn: ({
      ticket,
      item,
      status,
    }: {
      ticket: KitchenTicket
      item: KitchenItem
      status: KitchenItemStatus
    }) => transitionKitchenItem(ticket, item.id, status),
    onSuccess: async (ticket) => {
      setNotice(`Kitchen state updated for ${ticket.orderNumber}.`)
      queryClient.setQueryData(kitchenKeys.detail(ticket.id), ticket)
      queryClient.setQueryData(kitchenKeys.order(ticket.orderId), ticket)
      await queryClient.invalidateQueries({ queryKey: kitchenKeys.all })
      await queryClient.invalidateQueries({ queryKey: orderKeys.detail(ticket.orderId) })
    },
    onError: async (error) => {
      setNotice(kitchenRequestError(error))
      await queryClient.invalidateQueries({ queryKey: kitchenKeys.all })
    },
  })
  const tickets = ticketsQuery.data ?? []

  return (
    <div className="page kitchen-page">
      <section className="tables-hero kitchen-hero" aria-labelledby="kitchen-title">
        <div>
          <p className="eyebrow">Phase 5 live preparation</p>
          <h1 id="kitchen-title">Kitchen display</h1>
          <p>REST-authoritative tickets with secure realtime change notifications.</p>
        </div>
        <span
          className={`realtime-state realtime-state--${realtimeState}`}
          role="status"
          aria-live="polite"
        >
          Realtime: {realtimeState}
        </span>
      </section>

      <section className="kitchen-filters" aria-label="Kitchen filters">
        <div className="form-field">
          <label htmlFor="kitchen-view">Queue view</label>
          <select
            id="kitchen-view"
            value={view}
            onChange={(event) => setView(event.target.value as typeof view)}
          >
            <option value="ACTIVE">Active queue</option>
            <option value="QUEUED">Queued</option>
            <option value="PREPARING">Preparing</option>
            <option value="READY">Ready</option>
            <option value="CANCELLED">Cancelled history</option>
            <option value="ALL">All tickets</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="kitchen-order-search">Order number</label>
          <input
            id="kitchen-order-search"
            type="search"
            value={orderNumber}
            onChange={(event) => setOrderNumber(event.target.value)}
          />
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
      {ticketsQuery.isPending && <div className="table-state">Loading kitchen queue&hellip;</div>}
      {ticketsQuery.isError && (
        <div className="table-state table-state--error" role="alert">
          <p>The kitchen queue could not be loaded.</p>
          <button className="button button--secondary" onClick={() => ticketsQuery.refetch()}>
            Try again
          </button>
        </div>
      )}
      {!ticketsQuery.isPending && !ticketsQuery.isError && tickets.length === 0 && (
        <div className="table-state">
          <h2>No kitchen tickets match.</h2>
          <p>Submitted orders will appear here automatically.</p>
        </div>
      )}

      {view === 'ACTIVE' ? (
        <div className="kitchen-board">
          {ACTIVE_STATUSES.map((status) => (
            <section className="kitchen-column" aria-labelledby={`kitchen-${status}`} key={status}>
              <header>
                <h2 id={`kitchen-${status}`}>{status}</h2>
                <span>{tickets.filter((ticket) => ticket.status === status).length}</span>
              </header>
              <div className="kitchen-column__tickets">
                {tickets
                  .filter((ticket) => ticket.status === status)
                  .map((ticket) => (
                    <TicketCard
                      ticket={ticket}
                      isSaving={transitionMutation.isPending}
                      onProgress={(currentTicket, item, next) =>
                        transitionMutation.mutate({ ticket: currentTicket, item, status: next })
                      }
                      key={ticket.id}
                    />
                  ))}
              </div>
            </section>
          ))}
        </div>
      ) : (
        <div className="kitchen-history-list">
          {tickets.map((ticket) => (
            <TicketCard
              ticket={ticket}
              isSaving={transitionMutation.isPending}
              onProgress={(currentTicket, item, next) =>
                transitionMutation.mutate({ ticket: currentTicket, item, status: next })
              }
              key={ticket.id}
            />
          ))}
        </div>
      )}
    </div>
  )
}
