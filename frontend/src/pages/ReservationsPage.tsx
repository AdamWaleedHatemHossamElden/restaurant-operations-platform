import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { ReservationFormDialog } from '../features/reservations/ReservationFormDialog'
import type { ReservationFormValues } from '../features/reservations/reservationSchema'
import {
  formatReservationTime,
  localDateBoundaryToUtc,
  localDateTimeToUtc,
} from '../features/reservations/reservationTime'
import type {
  Reservation,
  ReservationFilters,
  ReservationStatus,
} from '../features/reservations/reservationTypes'
import {
  createReservation,
  listReservations,
  reservationRequestError,
  transitionReservation,
  updateReservation,
} from '../features/reservations/reservationsApi'

type StatusAction = { status: ReservationStatus; label: string; confirm: boolean }

const STATUS_ACTIONS: Record<ReservationStatus, StatusAction[]> = {
  PENDING: [
    { status: 'CONFIRMED', label: 'Confirm', confirm: false },
    { status: 'CANCELLED', label: 'Cancel', confirm: true },
  ],
  CONFIRMED: [
    { status: 'SEATED', label: 'Seat', confirm: false },
    { status: 'CANCELLED', label: 'Cancel', confirm: true },
    { status: 'NO_SHOW', label: 'Mark no-show', confirm: true },
  ],
  SEATED: [
    { status: 'COMPLETED', label: 'Complete', confirm: true },
    { status: 'CANCELLED', label: 'Cancel', confirm: true },
  ],
  COMPLETED: [],
  CANCELLED: [],
  NO_SHOW: [],
}

function todayValue(offsetDays = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function statusLabel(status: ReservationStatus) {
  return status
    .replace('_', ' ')
    .toLowerCase()
    .replace(/^./, (letter) => letter.toUpperCase())
}

export function ReservationsPage() {
  const queryClient = useQueryClient()
  const [startDate, setStartDate] = useState(todayValue())
  const [endDate, setEndDate] = useState(todayValue(7))
  const [status, setStatus] = useState('')
  const [assigned, setAssigned] = useState('')
  const [guestName, setGuestName] = useState('')
  const [reservationCode, setReservationCode] = useState('')
  const [sortBy, setSortBy] = useState('startAt')
  const [direction, setDirection] = useState('ASC')
  const [editing, setEditing] = useState<Reservation | null | undefined>(undefined)
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const filters = useMemo<ReservationFilters>(
    () => ({
      startFrom: localDateBoundaryToUtc(startDate),
      startTo: localDateBoundaryToUtc(endDate, true),
      status: status ? (status as ReservationStatus) : undefined,
      assigned: assigned === '' ? undefined : assigned === 'true',
      guestName: guestName || undefined,
      reservationCode: reservationCode || undefined,
      sortBy: sortBy as ReservationFilters['sortBy'],
      direction: direction as ReservationFilters['direction'],
    }),
    [assigned, direction, endDate, guestName, reservationCode, sortBy, startDate, status],
  )
  const reservationsQuery = useQuery({
    queryKey: ['reservations', filters],
    queryFn: () => listReservations(filters),
  })
  const refreshReservations = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['reservations'] }),
      queryClient.invalidateQueries({ queryKey: ['reservation-availability'] }),
    ])
  }

  const saveMutation = useMutation({
    mutationFn: async (values: ReservationFormValues) => {
      const request = {
        guestName: values.guestName,
        guestPhone: values.guestPhone,
        guestEmail: values.guestEmail || null,
        partySize: values.partySize,
        startAt: localDateTimeToUtc(values.startLocal),
        durationMinutes: values.durationMinutes,
        restaurantTableId: values.restaurantTableId ? Number(values.restaurantTableId) : null,
        notes: values.notes || null,
      }
      return editing
        ? updateReservation(editing.id, { ...request, version: editing.version })
        : createReservation(request)
    },
    onSuccess: async (reservation) => {
      setNotice(
        `${reservation.reservationCode} was ${editing ? 'updated' : 'created'} successfully.`,
      )
      setEditing(undefined)
      setFormError(null)
      await refreshReservations()
    },
    onError: (error) => setFormError(reservationRequestError(error)),
  })
  const statusMutation = useMutation({
    mutationFn: ({
      reservation,
      target,
    }: {
      reservation: Reservation
      target: ReservationStatus
    }) => transitionReservation(reservation, target),
    onSuccess: async (reservation) => {
      setNotice(`${reservation.reservationCode} is now ${statusLabel(reservation.status)}.`)
      await refreshReservations()
    },
    onError: (error) => setNotice(reservationRequestError(error)),
  })

  const applyStatus = (reservation: Reservation, action: StatusAction) => {
    if (
      action.confirm &&
      !window.confirm(
        `${action.label} ${reservation.reservationCode}? This changes its workflow state.`,
      )
    ) {
      return
    }
    statusMutation.mutate({ reservation, target: action.status })
  }

  const summary = reservationsQuery.data?.reduce(
    (counts, reservation) => {
      counts.total += 1
      if (reservation.status === 'CONFIRMED') counts.confirmed += 1
      if (reservation.status === 'SEATED') counts.seated += 1
      if (!reservation.restaurantTable) counts.unassigned += 1
      return counts
    },
    { total: 0, confirmed: 0, seated: 0, unassigned: 0 },
  ) ?? { total: 0, confirmed: 0, seated: 0, unassigned: 0 }

  return (
    <div className="page reservations-page">
      <section className="tables-hero reservation-hero" aria-labelledby="reservations-title">
        <div>
          <p className="eyebrow">Guest planning</p>
          <h1 id="reservations-title">Reservations</h1>
          <p>
            Coordinate arrivals, assignments, and service progress in the browser&apos;s local time.
          </p>
        </div>
        <button
          className="button button--primary"
          type="button"
          onClick={() => {
            setFormError(null)
            setEditing(null)
          }}
        >
          Add reservation
        </button>
      </section>

      <section className="reservation-summary" aria-label="Reservation summary">
        <div>
          <strong>{summary.total}</strong>
          <span>In view</span>
        </div>
        <div>
          <strong>{summary.confirmed}</strong>
          <span>Confirmed</span>
        </div>
        <div>
          <strong>{summary.seated}</strong>
          <span>Seated</span>
        </div>
        <div>
          <strong>{summary.unassigned}</strong>
          <span>Unassigned</span>
        </div>
      </section>

      {notice && (
        <div className="notice" role="status">
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice(null)} aria-label="Dismiss notification">
            &times;
          </button>
        </div>
      )}

      <section className="reservation-filters" aria-label="Reservation filters">
        <div className="form-field">
          <label htmlFor="reservation-from">From date</label>
          <input
            id="reservation-from"
            type="date"
            value={startDate}
            onChange={(event) => setStartDate(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="reservation-to">Through date</label>
          <input
            id="reservation-to"
            type="date"
            value={endDate}
            onChange={(event) => setEndDate(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="reservation-status-filter">Status</label>
          <select
            id="reservation-status-filter"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="">Any status</option>
            {Object.keys(STATUS_ACTIONS).map((value) => (
              <option value={value} key={value}>
                {statusLabel(value as ReservationStatus)}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="reservation-assignment-filter">Assignment</label>
          <select
            id="reservation-assignment-filter"
            value={assigned}
            onChange={(event) => setAssigned(event.target.value)}
          >
            <option value="">Any assignment</option>
            <option value="true">Assigned</option>
            <option value="false">Unassigned</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="reservation-guest-search">Guest name</label>
          <input
            id="reservation-guest-search"
            type="search"
            value={guestName}
            onChange={(event) => setGuestName(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="reservation-code-search">Reservation code</label>
          <input
            id="reservation-code-search"
            type="search"
            value={reservationCode}
            onChange={(event) => setReservationCode(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="reservation-sort">Sort by</label>
          <select
            id="reservation-sort"
            value={sortBy}
            onChange={(event) => setSortBy(event.target.value)}
          >
            <option value="startAt">Date and time</option>
            <option value="guestName">Guest name</option>
            <option value="partySize">Party size</option>
            <option value="status">Status</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="reservation-direction">Direction</label>
          <select
            id="reservation-direction"
            value={direction}
            onChange={(event) => setDirection(event.target.value)}
          >
            <option value="ASC">Ascending</option>
            <option value="DESC">Descending</option>
          </select>
        </div>
      </section>

      {reservationsQuery.isPending && (
        <div className="table-state">Loading reservations&hellip;</div>
      )}
      {reservationsQuery.isError && (
        <div className="table-state table-state--error" role="alert">
          <p>Reservations could not be loaded.</p>
          <button
            className="button button--secondary"
            type="button"
            onClick={() => reservationsQuery.refetch()}
          >
            Try again
          </button>
        </div>
      )}
      {reservationsQuery.data?.length === 0 && (
        <div className="table-state">
          <h2>No reservations match these filters.</h2>
          <p>Create a reservation or adjust the date range and filters.</p>
        </div>
      )}
      {reservationsQuery.data && reservationsQuery.data.length > 0 && (
        <div className="reservation-agenda" aria-label="Reservation agenda">
          {reservationsQuery.data.map((reservation) => (
            <article className="reservation-card" key={reservation.id}>
              <div className="reservation-card__time">
                <time dateTime={reservation.startAt}>
                  {formatReservationTime(reservation.startAt)}
                </time>
                <span>{reservation.durationMinutes} minutes</span>
              </div>
              <div className="reservation-card__guest">
                <p className="table-card__number">{reservation.reservationCode}</p>
                <h2>{reservation.guestName}</h2>
                <p>{reservation.partySize} guests</p>
              </div>
              <div className="reservation-card__assignment">
                <span
                  className={`reservation-status reservation-status--${reservation.status.toLowerCase()}`}
                >
                  {statusLabel(reservation.status)}
                </span>
                <strong>{reservation.restaurantTable?.tableNumber ?? 'Unassigned'}</strong>
                {reservation.restaurantTable && <span>{reservation.restaurantTable.section}</span>}
              </div>
              <div className="reservation-card__actions">
                <button
                  className="button button--secondary"
                  type="button"
                  disabled={['COMPLETED', 'CANCELLED', 'NO_SHOW'].includes(reservation.status)}
                  onClick={() => {
                    setFormError(null)
                    setEditing(reservation)
                  }}
                >
                  Edit
                </button>
                {STATUS_ACTIONS[reservation.status].map((action) => (
                  <button
                    className={`button ${action.confirm ? 'button--danger' : 'button--primary'}`}
                    type="button"
                    disabled={statusMutation.isPending}
                    onClick={() => applyStatus(reservation, action)}
                    key={action.status}
                  >
                    {action.label}
                  </button>
                ))}
              </div>
            </article>
          ))}
        </div>
      )}

      {editing !== undefined && (
        <ReservationFormDialog
          reservation={editing}
          isSaving={saveMutation.isPending}
          error={formError}
          onClose={() => setEditing(undefined)}
          onSave={async (values) => {
            try {
              await saveMutation.mutateAsync(values)
            } catch {
              // The mutation's onError handler presents the safe user-facing message.
            }
          }}
        />
      )}
    </div>
  )
}
