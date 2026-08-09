import axios from 'axios'

import { apiClient } from '../../lib/apiClient'
import {
  orderSchema,
  ordersSchema,
  type ModifierSelectionInput,
  type OrderCreateInput,
  type OrderFilters,
  type OrderItem,
  type OrderItemInput,
  type OrderStatus,
  type RestaurantOrder,
} from './orderTypes'

export const orderKeys = {
  all: ['orders'] as const,
  detail: (id: number) => ['orders', id] as const,
}

export async function listOrders(filters: OrderFilters): Promise<RestaurantOrder[]> {
  return ordersSchema.parse((await apiClient.get('/orders', { params: filters })).data)
}

export async function getOrder(id: number): Promise<RestaurantOrder> {
  return orderSchema.parse((await apiClient.get(`/orders/${id}`)).data)
}

export async function createOrder(input: OrderCreateInput): Promise<RestaurantOrder> {
  return orderSchema.parse((await apiClient.post('/orders', input)).data)
}

export async function updateOrder(
  order: RestaurantOrder,
  input: OrderCreateInput,
): Promise<RestaurantOrder> {
  return orderSchema.parse(
    (await apiClient.put(`/orders/${order.id}`, { ...input, version: order.version })).data,
  )
}

export async function addOrderItem(
  order: RestaurantOrder,
  input: OrderItemInput,
): Promise<RestaurantOrder> {
  return orderSchema.parse(
    (await apiClient.post(`/orders/${order.id}/items`, { ...input, version: order.version })).data,
  )
}

export async function updateOrderItem(
  order: RestaurantOrder,
  item: OrderItem,
  input: { quantity: number; notes: string | null; modifierSelections?: ModifierSelectionInput[] },
): Promise<RestaurantOrder> {
  return orderSchema.parse(
    (
      await apiClient.put(`/orders/${order.id}/items/${item.id}`, {
        ...input,
        version: order.version,
      })
    ).data,
  )
}

export async function removeOrderItem(
  order: RestaurantOrder,
  item: OrderItem,
): Promise<RestaurantOrder> {
  return orderSchema.parse(
    (
      await apiClient.delete(`/orders/${order.id}/items/${item.id}`, {
        params: { version: order.version },
      })
    ).data,
  )
}

export async function transitionOrder(
  order: RestaurantOrder,
  status: OrderStatus,
): Promise<RestaurantOrder> {
  return orderSchema.parse(
    (await apiClient.patch(`/orders/${order.id}/status`, { status, version: order.version })).data,
  )
}

export function orderRequestError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    const data = error.response.data
    const message =
      data && typeof data === 'object' && 'message' in data ? String(data.message) : ''
    if (message.includes('changed by another request') || message.includes('concurrently')) {
      return 'This order changed elsewhere. Reload it and try again.'
    }
    if (message.includes('modifier'))
      return 'The current modifier configuration cannot accept that selection.'
    if (message.includes('available for sale')) return 'That menu item is no longer available.'
    if (message.includes('SEATED') || message.includes('Reservation')) {
      return 'The reservation no longer matches this table or service state.'
    }
    return message || 'The order cannot be changed in its current state.'
  }
  if (axios.isAxiosError(error) && error.response?.status === 404) {
    return 'The order or a related record no longer exists.'
  }
  return 'The order request could not be completed. Please try again.'
}
