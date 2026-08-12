import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { apiClient, setApiAccessToken } from '../../lib/apiClient'
import {
  listAvailability,
  listEmployees,
  listShifts,
  removeAvailability,
  saveAvailability,
  saveEmployee,
  saveShift,
  toggleEmployee,
  transitionShift,
} from './staffApi'
import type { Availability, Employee, Shift } from './staffTypes'

const originalAdapter = apiClient.defaults.adapter
const timestamp = '2030-06-03T09:00:00Z'
const employee: Employee = {
  id: 1,
  employeeCode: 'EMP001',
  firstName: 'Maria',
  lastName: 'Rossi',
  email: null,
  phone: null,
  defaultOperationalRole: 'WAITER',
  employmentStartDate: null,
  active: true,
  version: 2,
  createdAt: timestamp,
  updatedAt: timestamp,
}
const availability: Availability = {
  id: 2,
  employeeId: 1,
  startAt: timestamp,
  endAt: '2030-06-03T17:00:00Z',
  notes: null,
  version: 3,
  createdAt: timestamp,
  updatedAt: timestamp,
}
const shift: Shift = {
  id: 3,
  employee: {
    id: 1,
    employeeCode: 'EMP001',
    firstName: 'Maria',
    lastName: 'Rossi',
    defaultOperationalRole: 'WAITER',
    active: true,
  },
  operationalRole: 'HOST',
  startAt: '2030-06-03T10:00:00Z',
  endAt: '2030-06-03T16:00:00Z',
  durationMinutes: 360,
  status: 'SCHEDULED',
  notes: null,
  completedAt: null,
  cancelledAt: null,
  version: 4,
  createdAt: timestamp,
  updatedAt: timestamp,
}

function response(config: InternalAxiosRequestConfig): AxiosResponse {
  const url = config.url ?? ''
  let data: unknown = shift
  if (url.endsWith('/employees') && config.method === 'get') data = [employee]
  else if (url.includes('/availability') && config.method === 'get') data = [availability]
  else if (url.includes('/availability')) data = availability
  else if (url.endsWith('/shifts') && config.method === 'get') data = [shift]
  else if (url.includes('/employees')) data = employee
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}

describe('staff API', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
  })

  it('reuses the memory-only authenticated client and sends authoritative versions', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setApiAccessToken('memory-only-token')
    apiClient.defaults.adapter = async (config) => {
      requests.push(config)
      return response(config)
    }

    await listEmployees({ operationalRole: 'WAITER' })
    await saveEmployee(
      {
        employeeCode: employee.employeeCode,
        firstName: employee.firstName,
        lastName: employee.lastName,
        defaultOperationalRole: employee.defaultOperationalRole,
      },
      employee,
    )
    await toggleEmployee(employee)
    await listAvailability(employee.id, timestamp, availability.endAt)
    await saveAvailability(
      employee.id,
      {
        startLocal: '2030-06-03T12:00',
        endLocal: '2030-06-03T20:00',
      },
      availability,
    )
    await removeAvailability(availability)
    await listShifts({ employeeId: employee.id })
    await saveShift(
      {
        employeeId: employee.id,
        operationalRole: 'HOST',
        startLocal: '2030-06-03T13:00',
        endLocal: '2030-06-03T19:00',
      },
      shift,
    )
    await transitionShift(shift, 'COMPLETED')

    expect(requests).toHaveLength(9)
    expect(
      requests.every(
        (request) => request.headers.get('Authorization') === 'Bearer memory-only-token',
      ),
    ).toBe(true)
    expect(JSON.parse(requests[1].data as string)).toMatchObject({ version: 2 })
    expect(JSON.parse(requests[4].data as string)).toMatchObject({ version: 3 })
    expect(requests[5].params).toEqual({ version: 3 })
    expect(JSON.parse(requests[7].data as string)).toMatchObject({ version: 4 })
    expect(JSON.parse(requests[8].data as string)).toEqual({ status: 'COMPLETED', version: 4 })
  })
})
