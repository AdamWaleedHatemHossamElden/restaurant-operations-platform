import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'

import { tableFormSchema, type TableFormValues } from './tableSchema'
import type { RestaurantTable } from './tableTypes'

type TableFormDialogProps = {
  table: RestaurantTable | null
  isSaving: boolean
  error: string | null
  onClose: () => void
  onSave: (values: TableFormValues) => Promise<void>
}

const EMPTY_VALUES: TableFormValues = {
  tableNumber: '',
  displayName: '',
  capacity: 2,
  section: '',
  status: 'AVAILABLE',
}

export function TableFormDialog({ table, isSaving, error, onClose, onSave }: TableFormDialogProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<TableFormValues>({
    resolver: zodResolver(tableFormSchema),
    defaultValues: EMPTY_VALUES,
  })

  useEffect(() => {
    reset(
      table
        ? {
            tableNumber: table.tableNumber,
            displayName: table.displayName,
            capacity: table.capacity,
            section: table.section,
            status: table.status,
          }
        : EMPTY_VALUES,
    )
  }, [reset, table])

  return (
    <div className="dialog-backdrop" role="presentation">
      <section
        className="table-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="table-dialog-title"
      >
        <div className="table-dialog__heading">
          <div>
            <p className="eyebrow">{table ? 'Edit record' : 'New record'}</p>
            <h2 id="table-dialog-title">
              {table ? `Edit ${table.tableNumber}` : 'Create a table'}
            </h2>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={onClose}
            aria-label="Close table form"
          >
            &times;
          </button>
        </div>

        {error && (
          <div className="form-alert" role="alert">
            {error}
          </div>
        )}

        <form className="table-form" onSubmit={handleSubmit(onSave)} noValidate>
          <div className="form-field">
            <label htmlFor="table-number">Table number</label>
            <input
              id="table-number"
              aria-invalid={errors.tableNumber ? 'true' : 'false'}
              {...register('tableNumber')}
            />
            {errors.tableNumber && <p className="field-error">{errors.tableNumber.message}</p>}
          </div>
          <div className="form-field">
            <label htmlFor="display-name">Display name</label>
            <input
              id="display-name"
              aria-invalid={errors.displayName ? 'true' : 'false'}
              {...register('displayName')}
            />
            {errors.displayName && <p className="field-error">{errors.displayName.message}</p>}
          </div>
          <div className="form-field">
            <label htmlFor="capacity">Capacity</label>
            <input
              id="capacity"
              type="number"
              min="1"
              max="100"
              aria-invalid={errors.capacity ? 'true' : 'false'}
              {...register('capacity', { valueAsNumber: true })}
            />
            {errors.capacity && <p className="field-error">{errors.capacity.message}</p>}
          </div>
          <div className="form-field">
            <label htmlFor="section">Section</label>
            <input
              id="section"
              aria-invalid={errors.section ? 'true' : 'false'}
              {...register('section')}
            />
            {errors.section && <p className="field-error">{errors.section.message}</p>}
          </div>
          <div className="form-field">
            <label htmlFor="table-status">Operational status</label>
            <select id="table-status" {...register('status')}>
              <option value="AVAILABLE">Available</option>
              <option value="OUT_OF_SERVICE">Out of service</option>
            </select>
          </div>
          <div className="dialog-actions">
            <button className="button button--secondary" type="button" onClick={onClose}>
              Cancel
            </button>
            <button className="button button--primary" type="submit" disabled={isSaving}>
              {isSaving ? 'Saving…' : table ? 'Save changes' : 'Create table'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
