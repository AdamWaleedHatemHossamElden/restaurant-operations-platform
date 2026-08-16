import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState, type ReactNode } from 'react'

import { AvailabilityDialog, EmployeeDialog, ShiftDialog } from '../features/staff/StaffDialogs'
import {
  listAvailability,
  listEmployees,
  listShifts,
  removeAvailability,
  saveAvailability,
  saveEmployee,
  saveShift,
  staffKeys,
  staffRequestError,
  toggleEmployee,
  transitionShift,
} from '../features/staff/staffApi'
import {
  addLocalDays,
  formatLocalDate,
  formatLocalTime,
  startOfLocalWeek,
  weekRange,
} from '../features/staff/staffTime'
import type {
  Availability,
  AvailabilityValues,
  Employee,
  EmployeeValues,
  OperationalRole,
  Shift,
  ShiftStatus,
  ShiftValues,
} from '../features/staff/staffTypes'
import { operationalRoles } from '../features/staff/staffTypes'

type StaffTab = 'employees' | 'availability' | 'schedule'

export function StaffPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<StaffTab>('employees')
  const [week, setWeek] = useState(() => startOfLocalWeek())
  const [search, setSearch] = useState('')
  const [role, setRole] = useState<'ALL' | OperationalRole>('ALL')
  const [active, setActive] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL')
  const [shiftStatus, setShiftStatus] = useState<'ALL' | ShiftStatus>('ALL')
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<number>()
  const [employeeEditor, setEmployeeEditor] = useState<Employee | null | undefined>(undefined)
  const [availabilityEditor, setAvailabilityEditor] = useState<Availability | null | undefined>(
    undefined,
  )
  const [shiftEditor, setShiftEditor] = useState<Shift | null | undefined>(undefined)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const range = useMemo(() => weekRange(week), [week])

  const employeeFilters = useMemo(
    () => ({
      search: search || undefined,
      operationalRole: role === 'ALL' ? undefined : role,
      active: active === 'ALL' ? undefined : active === 'ACTIVE',
    }),
    [active, role, search],
  )
  const employeesQuery = useQuery({
    queryKey: [...staffKeys.employees, employeeFilters],
    queryFn: () => listEmployees(employeeFilters),
  })
  const allEmployeesQuery = useQuery({
    queryKey: [...staffKeys.employees, 'all'],
    queryFn: () => listEmployees({}),
  })
  const availabilityQuery = useQuery({
    queryKey: staffKeys.availability(selectedEmployeeId ?? 0, range.startAt, range.endAt),
    queryFn: () => listAvailability(selectedEmployeeId!, range.startAt, range.endAt),
    enabled: Boolean(selectedEmployeeId),
  })
  const shiftsQuery = useQuery({
    queryKey: [
      ...staffKeys.shifts,
      range.startAt,
      range.endAt,
      selectedEmployeeId,
      role,
      shiftStatus,
    ],
    queryFn: () =>
      listShifts({
        startFrom: range.startAt,
        startTo: range.endAt,
        employeeId: selectedEmployeeId,
        operationalRole: role === 'ALL' ? undefined : role,
        status: shiftStatus === 'ALL' ? undefined : shiftStatus,
      }),
  })

  const refresh = async () => queryClient.invalidateQueries({ queryKey: staffKeys.all })
  const failed = async (caught: unknown) => {
    setError(staffRequestError(caught))
    await refresh()
  }
  const succeeded = async (message: string) => {
    setError(null)
    setNotice(message)
    await refresh()
  }

  const employeeSave = useMutation({
    mutationFn: ({ values, current }: { values: EmployeeValues; current?: Employee }) =>
      saveEmployee(values, current),
    onSuccess: async () => {
      setEmployeeEditor(undefined)
      await succeeded('Employee saved.')
    },
    onError: failed,
  })
  const employeeToggle = useMutation({
    mutationFn: toggleEmployee,
    onSuccess: async () => succeeded('Employee activation updated.'),
    onError: failed,
  })
  const availabilitySave = useMutation({
    mutationFn: ({ values, current }: { values: AvailabilityValues; current?: Availability }) =>
      saveAvailability(selectedEmployeeId!, values, current),
    onSuccess: async () => {
      setAvailabilityEditor(undefined)
      await succeeded('Availability saved.')
    },
    onError: failed,
  })
  const availabilityRemove = useMutation({
    mutationFn: removeAvailability,
    onSuccess: async () => succeeded('Availability removed. Existing shifts were preserved.'),
    onError: failed,
  })
  const shiftSave = useMutation({
    mutationFn: ({ values, current }: { values: ShiftValues; current?: Shift }) =>
      saveShift(values, current),
    onSuccess: async () => {
      setShiftEditor(undefined)
      await succeeded('Shift saved.')
    },
    onError: failed,
  })
  const shiftTransition = useMutation({
    mutationFn: ({ shift, status }: { shift: Shift; status: ShiftStatus }) =>
      transitionShift(shift, status),
    onSuccess: async (shift) => succeeded(`Shift ${shift.status.toLowerCase()}.`),
    onError: failed,
  })

  const employees = allEmployeesQuery.data ?? []
  const selectedEmployee = employees.find((employee) => employee.id === selectedEmployeeId)
  const days = Array.from({ length: 7 }, (_, index) => addLocalDays(week, index))
  const editorOpen =
    employeeEditor !== undefined || availabilityEditor !== undefined || shiftEditor !== undefined

  return (
    <div className="page staff-page">
      <section className="tables-hero staff-hero" aria-labelledby="staff-title">
        <div>
          <p className="eyebrow">People & scheduling</p>
          <h1 id="staff-title">Staff scheduling</h1>
          <p>Employee records, date-specific availability, and conflict-safe weekly shifts.</p>
        </div>
        <div className="staff-summary">
          <strong>{employees.filter((employee) => employee.active).length}</strong>
          <span>active employees</span>
        </div>
      </section>

      <nav className="menu-tabs" aria-label="Staff sections">
        {(['employees', 'availability', 'schedule'] as const).map((value) => (
          <button
            className={tab === value ? 'active' : ''}
            type="button"
            aria-current={tab === value ? 'page' : undefined}
            onClick={() => setTab(value)}
            key={value}
          >
            {value[0].toUpperCase() + value.slice(1)}
          </button>
        ))}
      </nav>

      {notice && (
        <div className="notice" role="status">
          <span>{notice}</span>
          <button type="button" aria-label="Dismiss notification" onClick={() => setNotice(null)}>
            &times;
          </button>
        </div>
      )}

      {error && !editorOpen && (
        <div className="form-alert" role="alert">
          {error}
        </div>
      )}

      {tab === 'employees' && (
        <section>
          <div className="workspace-toolbar">
            <div className="staff-filters">
              <label>
                Search
                <input
                  type="search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </label>
              <RoleFilter value={role} onChange={setRole} />
              <label>
                Active
                <select
                  value={active}
                  onChange={(event) => setActive(event.target.value as typeof active)}
                >
                  <option value="ALL">All</option>
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </label>
            </div>
            <button
              className="button button--primary"
              type="button"
              onClick={() => {
                setError(null)
                setEmployeeEditor(null)
              }}
            >
              Create employee
            </button>
          </div>
          <QueryState
            loading={employeesQuery.isPending}
            error={employeesQuery.isError}
            empty={!employeesQuery.data?.length}
            noun="employees"
          >
            <div className="staff-card-grid">
              {employeesQuery.data?.map((employee) => (
                <article className="staff-card" key={employee.id}>
                  <header>
                    <div>
                      <p className="menu-code">{employee.employeeCode}</p>
                      <h2>
                        {employee.firstName} {employee.lastName}
                      </h2>
                    </div>
                    <span
                      className={`status-pill ${employee.active ? 'status-pill--available' : 'status-pill--inactive'}`}
                    >
                      {employee.active ? 'Active' : 'Inactive'}
                    </span>
                  </header>
                  <strong>{employee.defaultOperationalRole}</strong>
                  <p>{employee.email || employee.phone || 'No contact details'}</p>
                  <div className="menu-card__actions">
                    <button
                      className="button button--secondary button--compact"
                      type="button"
                      onClick={() => {
                        setError(null)
                        setEmployeeEditor(employee)
                      }}
                    >
                      Edit
                    </button>
                    <button
                      className={`button button--compact ${employee.active ? 'button--danger-muted' : 'button--secondary'}`}
                      type="button"
                      onClick={() => employeeToggle.mutate(employee)}
                    >
                      {employee.active ? 'Deactivate' : 'Reactivate'}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </QueryState>
        </section>
      )}

      {tab === 'availability' && (
        <section>
          <WeekToolbar week={week} onWeek={setWeek}>
            <EmployeeFilter
              employees={employees}
              value={selectedEmployeeId}
              onChange={setSelectedEmployeeId}
            />
            <button
              className="button button--primary"
              type="button"
              disabled={!selectedEmployee?.active}
              onClick={() => {
                setError(null)
                setAvailabilityEditor(null)
              }}
            >
              Add window
            </button>
          </WeekToolbar>
          {!selectedEmployee ? (
            <div className="table-state">
              <h2>Select an employee.</h2>
              <p>Availability is date-specific and displayed in browser-local time.</p>
            </div>
          ) : (
            <div className="availability-layout">
              <section>
                <h2>{selectedEmployee.firstName}&apos;s availability</h2>
                {availabilityQuery.isPending && (
                  <div className="table-state">Loading availability…</div>
                )}
                {availabilityQuery.isError && (
                  <div className="table-state table-state--error" role="alert">
                    Availability could not be loaded.
                  </div>
                )}
                {availabilityQuery.data?.map((slot) => (
                  <article className="availability-card" key={slot.id}>
                    <div>
                      <strong>{formatLocalDate(slot.startAt)}</strong>
                      <span>
                        {formatLocalTime(slot.startAt)}–{formatLocalTime(slot.endAt)}
                      </span>
                      <small>{slot.notes || 'No notes'}</small>
                    </div>
                    <div className="menu-card__actions">
                      <button
                        className="button button--secondary button--compact"
                        type="button"
                        onClick={() => {
                          setError(null)
                          setAvailabilityEditor(slot)
                        }}
                      >
                        Edit
                      </button>
                      <button
                        className="button button--danger-muted button--compact"
                        type="button"
                        onClick={() => {
                          if (
                            window.confirm(
                              'Remove this availability window? Existing shifts will remain.',
                            )
                          )
                            availabilityRemove.mutate(slot)
                        }}
                      >
                        Remove
                      </button>
                    </div>
                  </article>
                ))}
                {availabilityQuery.data?.length === 0 && (
                  <div className="table-state">No availability this week.</div>
                )}
              </section>
              <section>
                <h2>Existing shifts</h2>
                <p className="field-hint">
                  Availability changes do not cancel or rewrite these shifts.
                </p>
                {shiftsQuery.data
                  ?.filter((shift) => shift.employee.id === selectedEmployeeId)
                  .map((shift) => (
                    <article className="availability-card" key={shift.id}>
                      <strong>
                        {formatLocalDate(shift.startAt)} · {formatLocalTime(shift.startAt)}–
                        {formatLocalTime(shift.endAt)}
                      </strong>
                      <span>
                        {shift.operationalRole} · {shift.status}
                      </span>
                    </article>
                  ))}
              </section>
            </div>
          )}
        </section>
      )}

      {tab === 'schedule' && (
        <section>
          <WeekToolbar week={week} onWeek={setWeek}>
            <EmployeeFilter
              employees={employees}
              value={selectedEmployeeId}
              onChange={setSelectedEmployeeId}
              all
            />
            <RoleFilter value={role} onChange={setRole} />
            <label>
              Status
              <select
                value={shiftStatus}
                onChange={(event) => setShiftStatus(event.target.value as typeof shiftStatus)}
              >
                <option value="ALL">All</option>
                <option value="SCHEDULED">Scheduled</option>
                <option value="COMPLETED">Completed</option>
                <option value="CANCELLED">Cancelled</option>
              </select>
            </label>
            <button
              className="button button--primary"
              type="button"
              disabled={!employees.some((employee) => employee.active)}
              onClick={() => {
                setError(null)
                setShiftEditor(null)
              }}
            >
              Create shift
            </button>
          </WeekToolbar>
          <QueryState
            loading={shiftsQuery.isPending}
            error={shiftsQuery.isError}
            empty={!shiftsQuery.data?.length}
            noun="shifts"
          >
            <div className="weekly-schedule">
              {days.map((day) => {
                const dayShifts =
                  shiftsQuery.data?.filter(
                    (shift) => new Date(shift.startAt).toDateString() === day.toDateString(),
                  ) ?? []
                return (
                  <section className="schedule-day" key={day.toISOString()}>
                    <header>
                      <h2>{formatLocalDate(day)}</h2>
                      <span>{dayShifts.length} shifts</span>
                    </header>
                    <div className="schedule-day__shifts">
                      {dayShifts.map((shift) => (
                        <ShiftCard
                          shift={shift}
                          key={shift.id}
                          onEdit={() => {
                            setError(null)
                            setShiftEditor(shift)
                          }}
                          onTransition={(status) => {
                            if (
                              window.confirm(
                                `${status === 'COMPLETED' ? 'Complete' : 'Cancel'} this shift?`,
                              )
                            )
                              shiftTransition.mutate({ shift, status })
                          }}
                        />
                      ))}
                      {!dayShifts.length && <p>No shifts</p>}
                    </div>
                  </section>
                )
              })}
            </div>
          </QueryState>
        </section>
      )}

      {employeeEditor !== undefined && (
        <EmployeeDialog
          employee={employeeEditor}
          error={error}
          saving={employeeSave.isPending}
          onClose={() => setEmployeeEditor(undefined)}
          onSave={(values) =>
            employeeSave
              .mutateAsync({ values, current: employeeEditor ?? undefined })
              .then(() => undefined)
          }
        />
      )}
      {availabilityEditor !== undefined && (
        <AvailabilityDialog
          availability={availabilityEditor}
          error={error}
          saving={availabilitySave.isPending}
          onClose={() => setAvailabilityEditor(undefined)}
          onSave={(values) =>
            availabilitySave
              .mutateAsync({ values, current: availabilityEditor ?? undefined })
              .then(() => undefined)
          }
        />
      )}
      {shiftEditor !== undefined && (
        <ShiftDialog
          shift={shiftEditor}
          employees={employees}
          error={error}
          saving={shiftSave.isPending}
          onClose={() => setShiftEditor(undefined)}
          onSave={(values) =>
            shiftSave
              .mutateAsync({ values, current: shiftEditor ?? undefined })
              .then(() => undefined)
          }
        />
      )}
    </div>
  )
}

function ShiftCard({
  shift,
  onEdit,
  onTransition,
}: {
  shift: Shift
  onEdit: () => void
  onTransition: (status: ShiftStatus) => void
}) {
  return (
    <article className={`shift-card shift-card--${shift.status.toLowerCase()}`}>
      <time dateTime={shift.startAt}>
        {formatLocalTime(shift.startAt)}–{formatLocalTime(shift.endAt)}
      </time>
      <strong>
        {shift.employee.firstName} {shift.employee.lastName}
      </strong>
      <span>{shift.operationalRole}</span>
      <span className="status-pill">{shift.status}</span>
      {shift.status === 'SCHEDULED' && (
        <div className="shift-card__actions">
          <button
            className="button button--secondary button--compact"
            type="button"
            onClick={onEdit}
          >
            Edit
          </button>
          <button
            className="button button--secondary button--compact"
            type="button"
            onClick={() => onTransition('COMPLETED')}
          >
            Complete
          </button>
          <button
            className="button button--danger-muted button--compact"
            type="button"
            onClick={() => onTransition('CANCELLED')}
          >
            Cancel
          </button>
        </div>
      )}
    </article>
  )
}

function WeekToolbar({
  week,
  onWeek,
  children,
}: {
  week: Date
  onWeek: (value: Date) => void
  children: ReactNode
}) {
  return (
    <div className="week-toolbar">
      <div className="week-navigation">
        <button
          className="button button--ghost button--compact"
          type="button"
          onClick={() => onWeek(addLocalDays(week, -7))}
        >
          Previous week
        </button>
        <button
          className="button button--secondary button--compact"
          type="button"
          onClick={() => onWeek(startOfLocalWeek())}
        >
          Current week
        </button>
        <button
          className="button button--ghost button--compact"
          type="button"
          onClick={() => onWeek(addLocalDays(week, 7))}
        >
          Next week
        </button>
        <strong>
          {formatLocalDate(week)} – {formatLocalDate(addLocalDays(week, 6))}
        </strong>
      </div>
      <div className="staff-filters">{children}</div>
    </div>
  )
}

function EmployeeFilter({
  employees,
  value,
  onChange,
  all = false,
}: {
  employees: Employee[]
  value?: number
  onChange: (value?: number) => void
  all?: boolean
}) {
  return (
    <label>
      Employee
      <select
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value ? Number(event.target.value) : undefined)}
      >
        <option value="">{all ? 'All employees' : 'Select employee'}</option>
        {employees.map((employee) => (
          <option value={employee.id} key={employee.id}>
            {employee.employeeCode} · {employee.firstName} {employee.lastName}
          </option>
        ))}
      </select>
    </label>
  )
}

function RoleFilter({
  value,
  onChange,
}: {
  value: 'ALL' | OperationalRole
  onChange: (value: 'ALL' | OperationalRole) => void
}) {
  return (
    <label>
      Role
      <select
        value={value}
        onChange={(event) => onChange(event.target.value as 'ALL' | OperationalRole)}
      >
        <option value="ALL">All roles</option>
        {operationalRoles.map((role) => (
          <option key={role}>{role}</option>
        ))}
      </select>
    </label>
  )
}

function QueryState({
  loading,
  error,
  empty,
  noun,
  children,
}: {
  loading: boolean
  error: boolean
  empty: boolean
  noun: string
  children: ReactNode
}) {
  if (loading) return <div className="table-state">Loading {noun}…</div>
  if (error)
    return (
      <div className="table-state table-state--error" role="alert">
        {noun} could not be loaded.
      </div>
    )
  if (empty)
    return (
      <div className="table-state">
        <h2>No {noun} match.</h2>
      </div>
    )
  return <>{children}</>
}
