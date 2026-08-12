import axios from 'axios'

import { apiClient } from '../../lib/apiClient'
import { localDateTimeToUtc } from './staffTime'
import {
  availabilityListSchema,
  availabilitySchema,
  employeeSchema,
  employeesSchema,
  shiftSchema,
  shiftsSchema,
  type Availability,
  type AvailabilityValues,
  type Employee,
  type EmployeeValues,
  type OperationalRole,
  type Shift,
  type ShiftStatus,
  type ShiftValues,
} from './staffTypes'

export const staffKeys = {
  all: ['staff'] as const,
  employees: ['staff', 'employees'] as const,
  availability: (employeeId: number, startAt: string, endAt: string) =>
    ['staff', 'availability', employeeId, startAt, endAt] as const,
  shifts: ['staff', 'shifts'] as const,
}

export async function listEmployees(filters: {
  search?: string
  operationalRole?: OperationalRole
  active?: boolean
}): Promise<Employee[]> {
  const response = await apiClient.get<unknown>('/staff/employees', { params: filters })
  return employeesSchema.parse(response.data)
}

export async function saveEmployee(values: EmployeeValues, employee?: Employee): Promise<Employee> {
  const request = {
    ...values,
    email: values.email || null,
    phone: values.phone || null,
    employmentStartDate: values.employmentStartDate || null,
    version: employee?.version,
  }
  const response = employee
    ? await apiClient.put<unknown>(`/staff/employees/${employee.id}`, request)
    : await apiClient.post<unknown>('/staff/employees', request)
  return employeeSchema.parse(response.data)
}

export async function toggleEmployee(employee: Employee): Promise<Employee> {
  const response = await apiClient.patch<unknown>(`/staff/employees/${employee.id}/activation`, {
    active: !employee.active,
    version: employee.version,
  })
  return employeeSchema.parse(response.data)
}

export async function listAvailability(
  employeeId: number,
  startAt: string,
  endAt: string,
): Promise<Availability[]> {
  const response = await apiClient.get<unknown>(`/staff/employees/${employeeId}/availability`, {
    params: { startAt, endAt },
  })
  return availabilityListSchema.parse(response.data)
}

export async function saveAvailability(
  employeeId: number,
  values: AvailabilityValues,
  current?: Availability,
): Promise<Availability> {
  const request = {
    startAt: localDateTimeToUtc(values.startLocal),
    endAt: localDateTimeToUtc(values.endLocal),
    notes: values.notes || null,
    version: current?.version,
  }
  const response = current
    ? await apiClient.put<unknown>(
        `/staff/employees/${employeeId}/availability/${current.id}`,
        request,
      )
    : await apiClient.post<unknown>(`/staff/employees/${employeeId}/availability`, request)
  return availabilitySchema.parse(response.data)
}

export async function removeAvailability(value: Availability): Promise<void> {
  await apiClient.delete(`/staff/employees/${value.employeeId}/availability/${value.id}`, {
    params: { version: value.version },
  })
}

export async function listShifts(filters: {
  employeeId?: number
  operationalRole?: OperationalRole
  status?: ShiftStatus
  startFrom?: string
  startTo?: string
  search?: string
}): Promise<Shift[]> {
  const response = await apiClient.get<unknown>('/staff/shifts', { params: filters })
  return shiftsSchema.parse(response.data)
}

export async function saveShift(values: ShiftValues, shift?: Shift): Promise<Shift> {
  const request = {
    employeeId: values.employeeId,
    operationalRole: values.operationalRole,
    startAt: localDateTimeToUtc(values.startLocal),
    endAt: localDateTimeToUtc(values.endLocal),
    notes: values.notes || null,
    version: shift?.version,
  }
  const response = shift
    ? await apiClient.put<unknown>(`/staff/shifts/${shift.id}`, request)
    : await apiClient.post<unknown>('/staff/shifts', request)
  return shiftSchema.parse(response.data)
}

export async function transitionShift(shift: Shift, status: ShiftStatus): Promise<Shift> {
  const response = await apiClient.patch<unknown>(`/staff/shifts/${shift.id}/status`, {
    status,
    version: shift.version,
  })
  return shiftSchema.parse(response.data)
}

export function staffRequestError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    const message = error.response.data?.message
    return typeof message === 'string'
      ? message
      : 'Staff data changed or conflicts with the schedule. Refresh and retry.'
  }
  if (axios.isAxiosError(error) && error.response?.status === 404) {
    return 'The employee, availability window, or shift no longer exists.'
  }
  return 'The staff scheduling request could not be completed. Please try again.'
}
