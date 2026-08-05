import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { apiClient, setApiAccessToken } from '../../lib/apiClient'
import {
  createReservation,
  listAvailableTables,
  listReservations,
  transitionReservation,
  updateReservation,
} from './reservationsApi'
import type { AssignedTable, Reservation } from './reservationTypes'

const originalAdapter = apiClient.defaults.adapter
const table: AssignedTable = {
  id: 4,
  tableNumber: 'R-04',
  displayName: 'Window four',
  section: 'Main',
  capacity: 6,
}
const reservation: Reservation = {
  id: 10,
  reservationCode: 'RSV-TEST123456',
  guestName: 'Ada Guest',
  guestPhone: '+12025550123',
  guestEmail: 'ada@example.com',
  partySize: 4,
  startAt: '2030-04-12T18:00:00Z',
  endAt: '2030-04-12T19:30:00Z',
  durationMinutes: 90,
  restaurantTable: table,
  status: 'PENDING',
  notes: null,
  createdAt: '2030-04-01T10:00:00Z',
  updatedAt: '2030-04-01T10:00:00Z',
  version: 0,
}

function response(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}

describe('reservation API', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
  })

  it('uses the authenticated client for reads, availability, writes, and transitions', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setApiAccessToken('memory-only-test-token')
    apiClient.defaults.adapter = async (config) => {
      requests.push(config)
      const data = config.url?.endsWith('/availability')
        ? [table]
        : config.method === 'get'
          ? [reservation]
          : reservation
      return response(config, data)
    }

    await listReservations({ status: 'PENDING' })
    await listAvailableTables({
      startAt: reservation.startAt,
      durationMinutes: 90,
      partySize: 4,
    })
    const write = {
      guestName: reservation.guestName,
      guestPhone: reservation.guestPhone,
      guestEmail: reservation.guestEmail,
      partySize: reservation.partySize,
      startAt: reservation.startAt,
      durationMinutes: reservation.durationMinutes,
      restaurantTableId: table.id,
      notes: null,
    }
    await createReservation(write)
    await updateReservation(reservation.id, { ...write, version: 0 })
    await transitionReservation(reservation, 'CONFIRMED')

    expect(requests.map(({ method, url }) => [method, url])).toEqual([
      ['get', '/reservations'],
      ['get', '/reservations/availability'],
      ['post', '/reservations'],
      ['put', '/reservations/10'],
      ['patch', '/reservations/10/status'],
    ])
    expect(
      requests.every(
        (request) => request.headers.get('Authorization') === 'Bearer memory-only-test-token',
      ),
    ).toBe(true)
    expect(JSON.parse(requests[4].data as string)).toEqual({ status: 'CONFIRMED', version: 0 })
  })
})
