import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'

import { reservationFormSchema, type ReservationFormValues } from './reservationSchema'
import {
  defaultReservationLocalTime,
  localDateTimeToUtc,
  utcToLocalDateTimeValue,
} from './reservationTime'
import type { Reservation } from './reservationTypes'
import { listAvailableTables } from './reservationsApi'

type ReservationFormDialogProps = {
  reservation: Reservation | null
  isSaving: boolean
  error: string | null
  onClose: () => void
  onSave: (values: ReservationFormValues) => Promise<void>
}

function valuesFor(reservation: Reservation | null): ReservationFormValues {
  if (reservation) {
    return {
      guestName: reservation.guestName,
      guestPhone: reservation.guestPhone,
      guestEmail: reservation.guestEmail ?? '',
      partySize: reservation.partySize,
      startLocal: utcToLocalDateTimeValue(reservation.startAt),
      durationMinutes: reservation.durationMinutes,
      restaurantTableId: reservation.restaurantTable?.id.toString() ?? '',
      notes: reservation.notes ?? '',
    }
  }
  return {
    guestName: '',
    guestPhone: '',
    guestEmail: '',
    partySize: 2,
    startLocal: defaultReservationLocalTime(),
    durationMinutes: 90,
    restaurantTableId: '',
    notes: '',
  }
}

export function ReservationFormDialog({
  reservation,
  isSaving,
  error,
  onClose,
  onSave,
}: ReservationFormDialogProps) {
  const {
    control,
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ReservationFormValues>({
    resolver: zodResolver(reservationFormSchema),
    defaultValues: valuesFor(reservation),
  })
  const [partySize, startLocal, durationMinutes] = useWatch({
    control,
    name: ['partySize', 'startLocal', 'durationMinutes'],
  })
  const canCheckAvailability =
    Number.isInteger(partySize) &&
    partySize >= 1 &&
    durationMinutes >= 15 &&
    Boolean(startLocal) &&
    !Number.isNaN(new Date(startLocal).getTime())

  const availability = useQuery({
    queryKey: ['reservation-availability', partySize, startLocal, durationMinutes, reservation?.id],
    queryFn: () =>
      listAvailableTables({
        partySize,
        startAt: localDateTimeToUtc(startLocal),
        durationMinutes,
        excludeReservationId: reservation?.id,
      }),
    enabled: canCheckAvailability,
  })

  useEffect(() => {
    reset(valuesFor(reservation))
  }, [reset, reservation])

  return (
    <div className="dialog-backdrop" role="presentation">
      <section
        className="table-dialog reservation-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="reservation-dialog-title"
      >
        <div className="table-dialog__heading">
          <div>
            <p className="eyebrow">{reservation ? reservation.reservationCode : 'New booking'}</p>
            <h2 id="reservation-dialog-title">
              {reservation ? 'Edit reservation' : 'Create a reservation'}
            </h2>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Close form">
            &times;
          </button>
        </div>

        {error && (
          <div className="form-alert" role="alert">
            {error}
          </div>
        )}

        <form className="reservation-form" onSubmit={handleSubmit(onSave)} noValidate>
          <div className="form-field">
            <label htmlFor="guest-name">Guest name</label>
            <input
              id="guest-name"
              aria-invalid={Boolean(errors.guestName)}
              {...register('guestName')}
            />
            {errors.guestName && <p className="field-error">{errors.guestName.message}</p>}
          </div>
          <div className="form-field">
            <label htmlFor="guest-phone">Phone</label>
            <input
              id="guest-phone"
              type="tel"
              aria-invalid={Boolean(errors.guestPhone)}
              {...register('guestPhone')}
            />
            {errors.guestPhone && <p className="field-error">{errors.guestPhone.message}</p>}
          </div>
          <div className="form-field reservation-form__wide">
            <label htmlFor="guest-email">Email (optional)</label>
            <input
              id="guest-email"
              type="email"
              aria-invalid={Boolean(errors.guestEmail)}
              {...register('guestEmail')}
            />
            {errors.guestEmail && <p className="field-error">{errors.guestEmail.message}</p>}
          </div>
          <div className="form-field">
            <label htmlFor="party-size">Party size</label>
            <input
              id="party-size"
              type="number"
              min="1"
              max="100"
              {...register('partySize', { valueAsNumber: true })}
            />
            {errors.partySize && <p className="field-error">{errors.partySize.message}</p>}
          </div>
          <div className="form-field">
            <label htmlFor="reservation-duration">Duration</label>
            <select
              id="reservation-duration"
              {...register('durationMinutes', { valueAsNumber: true })}
            >
              {[30, 45, 60, 90, 120, 150, 180, 240].map((minutes) => (
                <option value={minutes} key={minutes}>
                  {minutes} minutes
                </option>
              ))}
            </select>
            {errors.durationMinutes && (
              <p className="field-error">{errors.durationMinutes.message}</p>
            )}
          </div>
          <div className="form-field reservation-form__wide">
            <label htmlFor="reservation-start">Local date and time</label>
            <input id="reservation-start" type="datetime-local" {...register('startLocal')} />
            <p className="field-hint">
              Saved as UTC and displayed in this browser&apos;s timezone.
            </p>
            {errors.startLocal && <p className="field-error">{errors.startLocal.message}</p>}
          </div>
          <div className="form-field reservation-form__wide">
            <label htmlFor="reservation-table">Table assignment</label>
            <select id="reservation-table" {...register('restaurantTableId')}>
              <option value="">Unassigned</option>
              {availability.data?.map((table) => (
                <option value={table.id} key={table.id}>
                  {table.tableNumber} &middot; {table.displayName} &middot; {table.section} &middot;
                  seats {table.capacity}
                </option>
              ))}
            </select>
            {availability.isPending && canCheckAvailability && (
              <p className="field-hint">Checking live table availability&hellip;</p>
            )}
            {availability.isError && (
              <p className="field-error">Available tables could not be loaded.</p>
            )}
            {availability.data?.length === 0 && (
              <p className="field-hint">No suitable table is available. You may save unassigned.</p>
            )}
          </div>
          <div className="form-field reservation-form__wide">
            <label htmlFor="reservation-notes">Notes (optional)</label>
            <textarea id="reservation-notes" rows={3} {...register('notes')} />
            {errors.notes && <p className="field-error">{errors.notes.message}</p>}
          </div>
          <div className="dialog-actions reservation-form__wide">
            <button className="button button--secondary" type="button" onClick={onClose}>
              Cancel
            </button>
            <button className="button button--primary" type="submit" disabled={isSaving}>
              {isSaving ? 'Saving…' : reservation ? 'Save changes' : 'Create reservation'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
