import { useQuery } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent, type ReactNode } from 'react'

import { Dialog as AccessibleDialog } from '../../components/ui/Dialog'
import { listAvailability, staffKeys } from './staffApi'
import {
  addLocalDays,
  defaultShiftTimes,
  formatLocalTime,
  startOfLocalWeek,
  utcToLocalDateTimeValue,
} from './staffTime'
import type {
  Availability,
  AvailabilityValues,
  Employee,
  EmployeeValues,
  OperationalRole,
  Shift,
  ShiftValues,
} from './staffTypes'
import { operationalRoles } from './staffTypes'

function Dialog({
  title,
  children,
  onClose,
}: {
  title: string
  children: ReactNode
  onClose: () => void
}) {
  return (
    <AccessibleDialog
      className="dialog staff-dialog"
      labelledBy="staff-dialog-title"
      onClose={onClose}
    >
      <div className="dialog__header">
        <h2 id="staff-dialog-title">{title}</h2>
        <button className="icon-button" type="button" aria-label="Close dialog" onClick={onClose}>
          &times;
        </button>
      </div>
      {children}
    </AccessibleDialog>
  )
}

function Actions({
  error,
  saving,
  onClose,
}: {
  error: string | null
  saving: boolean
  onClose: () => void
}) {
  return (
    <>
      {error && (
        <div className="form-alert staff-form__wide" role="alert">
          {error}
        </div>
      )}
      <div className="dialog-actions staff-form__wide">
        <button className="button button--secondary" type="button" onClick={onClose}>
          Cancel
        </button>
        <button className="button button--primary" type="submit" disabled={saving}>
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </>
  )
}

function submit(event: FormEvent, action: () => Promise<void>) {
  event.preventDefault()
  void action().catch(() => undefined)
}

export function EmployeeDialog({
  employee,
  error,
  saving,
  onClose,
  onSave,
}: {
  employee: Employee | null
  error: string | null
  saving: boolean
  onClose: () => void
  onSave: (values: EmployeeValues) => Promise<void>
}) {
  const [values, setValues] = useState<EmployeeValues>({
    employeeCode: employee?.employeeCode ?? '',
    firstName: employee?.firstName ?? '',
    lastName: employee?.lastName ?? '',
    email: employee?.email ?? '',
    phone: employee?.phone ?? '',
    defaultOperationalRole: employee?.defaultOperationalRole ?? 'WAITER',
    employmentStartDate: employee?.employmentStartDate ?? '',
  })
  const set = (field: keyof EmployeeValues, value: string) =>
    setValues((current) => ({ ...current, [field]: value }))
  return (
    <Dialog
      title={employee ? `Edit ${employee.firstName} ${employee.lastName}` : 'Create employee'}
      onClose={onClose}
    >
      <form className="staff-form" onSubmit={(event) => submit(event, () => onSave(values))}>
        <label>
          Employee code
          <input
            required
            maxLength={40}
            pattern="[A-Za-z0-9][A-Za-z0-9_-]*"
            value={values.employeeCode}
            onChange={(event) => set('employeeCode', event.target.value)}
          />
        </label>
        <label>
          Default role
          <select
            value={values.defaultOperationalRole}
            onChange={(event) => set('defaultOperationalRole', event.target.value)}
          >
            {operationalRoles.map((role) => (
              <option key={role}>{role}</option>
            ))}
          </select>
        </label>
        <label>
          First name
          <input
            required
            maxLength={100}
            value={values.firstName}
            onChange={(event) => set('firstName', event.target.value)}
          />
        </label>
        <label>
          Last name
          <input
            required
            maxLength={100}
            value={values.lastName}
            onChange={(event) => set('lastName', event.target.value)}
          />
        </label>
        <label>
          Email
          <input
            type="email"
            maxLength={254}
            value={values.email}
            onChange={(event) => set('email', event.target.value)}
          />
        </label>
        <label>
          Phone
          <input
            type="tel"
            maxLength={40}
            value={values.phone}
            onChange={(event) => set('phone', event.target.value)}
          />
        </label>
        <label className="staff-form__wide">
          Employment start date
          <input
            type="date"
            value={values.employmentStartDate}
            onChange={(event) => set('employmentStartDate', event.target.value)}
          />
        </label>
        <Actions error={error} saving={saving} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function AvailabilityDialog({
  availability,
  error,
  saving,
  onClose,
  onSave,
}: {
  availability: Availability | null
  error: string | null
  saving: boolean
  onClose: () => void
  onSave: (values: AvailabilityValues) => Promise<void>
}) {
  const defaults = defaultShiftTimes()
  const [values, setValues] = useState<AvailabilityValues>({
    startLocal: availability ? utcToLocalDateTimeValue(availability.startAt) : defaults.startLocal,
    endLocal: availability ? utcToLocalDateTimeValue(availability.endAt) : defaults.endLocal,
    notes: availability?.notes ?? '',
  })
  return (
    <Dialog title={availability ? 'Edit availability' : 'Add availability'} onClose={onClose}>
      <form className="staff-form" onSubmit={(event) => submit(event, () => onSave(values))}>
        <label>
          Local start
          <input
            required
            type="datetime-local"
            value={values.startLocal}
            onChange={(event) => setValues({ ...values, startLocal: event.target.value })}
          />
        </label>
        <label>
          Local end
          <input
            required
            type="datetime-local"
            value={values.endLocal}
            onChange={(event) => setValues({ ...values, endLocal: event.target.value })}
          />
        </label>
        <label className="staff-form__wide">
          Notes
          <textarea
            maxLength={500}
            rows={3}
            value={values.notes}
            onChange={(event) => setValues({ ...values, notes: event.target.value })}
          />
        </label>
        <p className="field-hint staff-form__wide">
          Date-specific only. Stored as UTC; changing this window does not rewrite existing shifts.
        </p>
        <Actions error={error} saving={saving} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function ShiftDialog({
  shift,
  employees,
  error,
  saving,
  onClose,
  onSave,
}: {
  shift: Shift | null
  employees: Employee[]
  error: string | null
  saving: boolean
  onClose: () => void
  onSave: (values: ShiftValues) => Promise<void>
}) {
  const initialEmployee =
    employees.find((employee) => employee.id === shift?.employee.id) ??
    employees.find((employee) => employee.active)
  const defaults = defaultShiftTimes()
  const [values, setValues] = useState<ShiftValues>({
    employeeId: initialEmployee?.id ?? 0,
    operationalRole: shift?.operationalRole ?? initialEmployee?.defaultOperationalRole ?? 'WAITER',
    startLocal: shift ? utcToLocalDateTimeValue(shift.startAt) : defaults.startLocal,
    endLocal: shift ? utcToLocalDateTimeValue(shift.endAt) : defaults.endLocal,
    notes: shift?.notes ?? '',
  })
  const range = useMemo(() => {
    const selectedStart = new Date(values.startLocal)
    const start = startOfLocalWeek(
      Number.isNaN(selectedStart.getTime()) ? new Date() : selectedStart,
    )
    return { startAt: start.toISOString(), endAt: addLocalDays(start, 7).toISOString() }
  }, [values.startLocal])
  const windows = useQuery({
    queryKey: staffKeys.availability(values.employeeId, range.startAt, range.endAt),
    queryFn: () => listAvailability(values.employeeId, range.startAt, range.endAt),
    enabled: values.employeeId > 0,
  })
  const chooseEmployee = (employeeId: number) => {
    const employee = employees.find((value) => value.id === employeeId)
    setValues((current) => ({
      ...current,
      employeeId,
      operationalRole: employee?.defaultOperationalRole ?? current.operationalRole,
    }))
  }
  return (
    <Dialog title={shift ? 'Edit scheduled shift' : 'Create shift'} onClose={onClose}>
      <form className="staff-form" onSubmit={(event) => submit(event, () => onSave(values))}>
        <label className="staff-form__wide">
          Employee
          <select
            required
            value={values.employeeId || ''}
            onChange={(event) => chooseEmployee(Number(event.target.value))}
          >
            <option value="">Select employee</option>
            {employees
              .filter((employee) => employee.active || employee.id === shift?.employee.id)
              .map((employee) => (
                <option value={employee.id} key={employee.id}>
                  {employee.employeeCode} · {employee.firstName} {employee.lastName}
                </option>
              ))}
          </select>
        </label>
        <label>
          Local start
          <input
            required
            type="datetime-local"
            value={values.startLocal}
            onChange={(event) =>
              setValues((current) => ({ ...current, startLocal: event.target.value }))
            }
          />
        </label>
        <label>
          Local end
          <input
            required
            type="datetime-local"
            value={values.endLocal}
            onChange={(event) =>
              setValues((current) => ({ ...current, endLocal: event.target.value }))
            }
          />
        </label>
        <label className="staff-form__wide">
          Operational role
          <select
            value={values.operationalRole}
            onChange={(event) =>
              setValues((current) => ({
                ...current,
                operationalRole: event.target.value as OperationalRole,
              }))
            }
          >
            {operationalRoles.map((role) => (
              <option key={role}>{role}</option>
            ))}
          </select>
          <small>This scheduling role does not grant application access.</small>
        </label>
        <div className="availability-guide staff-form__wide" aria-live="polite">
          <strong>Selected week availability</strong>
          {windows.isPending && <span>Loading…</span>}
          {windows.data?.map((window) => (
            <span key={window.id}>
              {new Date(window.startAt).toLocaleDateString()} · {formatLocalTime(window.startAt)}–
              {formatLocalTime(window.endAt)}
            </span>
          ))}
          {windows.data?.length === 0 && <span>No availability covers this week.</span>}
        </div>
        <label className="staff-form__wide">
          Notes
          <textarea
            maxLength={1000}
            rows={3}
            value={values.notes}
            onChange={(event) =>
              setValues((current) => ({ ...current, notes: event.target.value }))
            }
          />
        </label>
        <Actions error={error} saving={saving} onClose={onClose} />
      </form>
    </Dialog>
  )
}
