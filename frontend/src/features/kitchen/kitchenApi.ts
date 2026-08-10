import axios from 'axios'

import { apiClient } from '../../lib/apiClient'
import {
  kitchenTicketSchema,
  kitchenTicketsSchema,
  type KitchenFilters,
  type KitchenItemStatus,
  type KitchenTicket,
} from './kitchenTypes'

export const kitchenKeys = {
  all: ['kitchen'] as const,
  queues: () => ['kitchen', 'tickets'] as const,
  detail: (id: number) => ['kitchen', 'tickets', id] as const,
  order: (orderId: number) => ['kitchen', 'orders', orderId] as const,
}

export async function listKitchenTickets(filters: KitchenFilters): Promise<KitchenTicket[]> {
  return kitchenTicketsSchema.parse(
    (await apiClient.get('/kitchen/tickets', { params: filters })).data,
  )
}

export async function getKitchenTicket(id: number): Promise<KitchenTicket> {
  return kitchenTicketSchema.parse((await apiClient.get(`/kitchen/tickets/${id}`)).data)
}

export async function getKitchenTicketByOrder(orderId: number): Promise<KitchenTicket | null> {
  try {
    return kitchenTicketSchema.parse((await apiClient.get(`/kitchen/orders/${orderId}`)).data)
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) return null
    throw error
  }
}

export async function transitionKitchenItem(
  ticket: KitchenTicket,
  itemId: number,
  status: KitchenItemStatus,
): Promise<KitchenTicket> {
  return kitchenTicketSchema.parse(
    (
      await apiClient.patch(`/kitchen/tickets/${ticket.id}/items/${itemId}/status`, {
        status,
        version: ticket.version,
      })
    ).data,
  )
}

export function kitchenRequestError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    const data = error.response.data
    const message =
      data && typeof data === 'object' && 'message' in data ? String(data.message) : ''
    if (message.includes('changed by another request') || message.includes('concurrent')) {
      return 'This ticket changed elsewhere. The latest kitchen state is being loaded.'
    }
    if (message.includes('no longer active') || message.includes('CANCELLED')) {
      return 'This order is no longer active in the kitchen.'
    }
    return message || 'That kitchen change is no longer valid.'
  }
  return 'The kitchen update could not be completed. Please try again.'
}
