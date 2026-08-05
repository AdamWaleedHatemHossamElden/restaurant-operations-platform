import axios from 'axios'

import { apiClient } from '../../lib/apiClient'
import {
  availableTablesSchema,
  reservationSchema,
  reservationsSchema,
  type AvailabilityRequest,
  type Reservation,
  type ReservationFilters,
  type ReservationStatus,
  type ReservationWriteRequest,
} from './reservationTypes'

export async function listReservations(filters: ReservationFilters): Promise<Reservation[]> {
  const response = await apiClient.get<unknown>('/reservations', { params: filters })
  return reservationsSchema.parse(response.data)
}

export async function createReservation(request: ReservationWriteRequest): Promise<Reservation> {
  const response = await apiClient.post<unknown>('/reservations', request)
  return reservationSchema.parse(response.data)
}

export async function updateReservation(
  id: number,
  request: ReservationWriteRequest & { version: number },
): Promise<Reservation> {
  const response = await apiClient.put<unknown>(`/reservations/${id}`, request)
  return reservationSchema.parse(response.data)
}

export async function transitionReservation(
  reservation: Pick<Reservation, 'id' | 'version'>,
  status: ReservationStatus,
): Promise<Reservation> {
  const response = await apiClient.patch<unknown>(`/reservations/${reservation.id}/status`, {
    status,
    version: reservation.version,
  })
  return reservationSchema.parse(response.data)
}

export async function listAvailableTables(request: AvailabilityRequest) {
  const response = await apiClient.get<unknown>('/reservations/availability', { params: request })
  return availableTablesSchema.parse(response.data)
}

export function reservationRequestError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    return 'Availability or reservation data changed. Refresh and try again.'
  }
  if (axios.isAxiosError(error) && error.response?.status === 404) {
    return 'The reservation or selected table no longer exists.'
  }
  return 'The reservation request could not be completed. Please try again.'
}
