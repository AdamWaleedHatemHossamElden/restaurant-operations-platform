import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo } from 'react'
import { useForm, useWatch } from 'react-hook-form'

import { Dialog } from '../../components/ui/Dialog'
import type { Reservation } from '../reservations/reservationTypes'
import type { RestaurantTable } from '../tables/tableTypes'
import type { RestaurantOrder } from './orderTypes'
import { orderFormSchema, type OrderFormValues } from './orderSchemas'

type Props = {
  order?: RestaurantOrder | null
  tables: RestaurantTable[]
  reservations: Reservation[]
  isSaving: boolean
  error: string | null
  onClose: () => void
  onSave: (values: OrderFormValues) => Promise<void>
}

function valuesFor(order?: RestaurantOrder | null): OrderFormValues {
  return {
    restaurantTableId: order?.restaurantTable.id.toString() ?? '',
    reservationId: order?.reservation?.id.toString() ?? '',
    notes: order?.notes ?? '',
  }
}

export function OrderFormDialog({
  order,
  tables,
  reservations,
  isSaving,
  error,
  onClose,
  onSave,
}: Props) {
  const {
    control,
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<OrderFormValues>({
    resolver: zodResolver(orderFormSchema),
    defaultValues: valuesFor(order),
  })
  const selectedTableId = useWatch({ control, name: 'restaurantTableId' })
  const matchingReservations = useMemo(
    () =>
      reservations.filter(
        (reservation) =>
          reservation.status === 'SEATED' &&
          reservation.restaurantTable?.id.toString() === selectedTableId,
      ),
    [reservations, selectedTableId],
  )

  useEffect(() => reset(valuesFor(order)), [order, reset])

  return (
    <Dialog className="table-dialog order-dialog" labelledBy="order-dialog-title" onClose={onClose}>
      <div className="table-dialog__heading">
        <div>
          <p className="eyebrow">{order?.orderNumber ?? 'New order'}</p>
          <h2 id="order-dialog-title">{order ? 'Edit order details' : 'Create an order'}</h2>
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
      <form className="order-form" onSubmit={handleSubmit(onSave)} noValidate>
        <div className="form-field">
          <label htmlFor="order-table">Restaurant table</label>
          <select id="order-table" {...register('restaurantTableId')}>
            <option value="">Select a table</option>
            {tables
              .filter((table) => table.active && table.status === 'AVAILABLE')
              .map((table) => (
                <option value={table.id} key={table.id}>
                  {table.tableNumber} &middot; {table.displayName} &middot; {table.section}
                </option>
              ))}
          </select>
          {errors.restaurantTableId && (
            <p className="field-error">{errors.restaurantTableId.message}</p>
          )}
        </div>
        <div className="form-field">
          <label htmlFor="order-reservation">Seated reservation (optional)</label>
          <select id="order-reservation" {...register('reservationId')}>
            <option value="">No linked reservation</option>
            {matchingReservations.map((reservation) => (
              <option value={reservation.id} key={reservation.id}>
                {reservation.reservationCode} &middot; {reservation.guestName}
              </option>
            ))}
          </select>
          <p className="field-hint">
            Only seated reservations assigned to the selected table appear.
          </p>
        </div>
        <div className="form-field order-form__wide">
          <label htmlFor="order-notes">Order notes (optional)</label>
          <textarea id="order-notes" rows={3} {...register('notes')} />
          {errors.notes && <p className="field-error">{errors.notes.message}</p>}
        </div>
        <div className="dialog-actions order-form__wide">
          <button className="button button--secondary" type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="button button--primary" type="submit" disabled={isSaving}>
            {isSaving ? 'Saving…' : order ? 'Save details' : 'Create order'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
