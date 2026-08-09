import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { apiClient, setApiAccessToken } from '../../lib/apiClient'
import {
  addOrderItem,
  createOrder,
  getOrder,
  listOrders,
  removeOrderItem,
  transitionOrder,
  updateOrder,
  updateOrderItem,
} from './ordersApi'
import type { RestaurantOrder } from './orderTypes'

const originalAdapter = apiClient.defaults.adapter
const order: RestaurantOrder = {
  id: 7,
  orderNumber: 'ORD-20300101-ABC123',
  status: 'OPEN',
  version: 4,
  restaurantTable: { id: 2, tableNumber: 'T-2', displayName: 'Two', section: 'Main' },
  reservation: null,
  notes: null,
  subtotal: '12.50',
  total: '12.50',
  itemCount: 1,
  createdAt: '2030-01-01T10:00:00Z',
  updatedAt: '2030-01-01T10:05:00Z',
  submittedAt: null,
  completedAt: null,
  cancelledAt: null,
  items: [
    {
      id: 8,
      menuItemId: 3,
      itemCode: 'BURGER',
      itemName: 'Burger',
      basePrice: '10.00',
      quantity: 1,
      notes: null,
      unitTotal: '12.50',
      lineTotal: '12.50',
      displayOrder: 0,
      modifiers: [],
      createdAt: '2030-01-01T10:00:00Z',
      updatedAt: '2030-01-01T10:00:00Z',
    },
  ],
  history: [
    {
      id: 1,
      fromStatus: null,
      toStatus: 'OPEN',
      changedAt: '2030-01-01T10:00:00Z',
      changedByUserId: 1,
    },
  ],
}

function response(config: InternalAxiosRequestConfig): AxiosResponse {
  return {
    data: config.method === 'get' && config.url === '/orders' ? [order] : order,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }
}

describe('orders API', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
  })

  it('reuses the authenticated client and sends versions without client prices', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setApiAccessToken('memory-only-order-token')
    apiClient.defaults.adapter = async (config) => {
      requests.push(config)
      return response(config)
    }

    const createInput = { restaurantTableId: 2, reservationId: null, notes: null }
    await listOrders({ status: 'OPEN' })
    await getOrder(7)
    await createOrder(createInput)
    await updateOrder(order, createInput)
    await addOrderItem(order, {
      menuItemId: 3,
      quantity: 2,
      notes: null,
      modifierSelections: [{ modifierGroupId: 4, optionIds: [5] }],
    })
    await updateOrderItem(order, order.items[0], { quantity: 3, notes: 'No onions' })
    await removeOrderItem(order, order.items[0])
    await transitionOrder(order, 'SUBMITTED')

    expect(requests).toHaveLength(8)
    expect(
      requests.every(
        (request) => request.headers.get('Authorization') === 'Bearer memory-only-order-token',
      ),
    ).toBe(true)
    expect(JSON.parse(requests[4].data as string)).toEqual({
      menuItemId: 3,
      quantity: 2,
      notes: null,
      modifierSelections: [{ modifierGroupId: 4, optionIds: [5] }],
      version: 4,
    })
    expect(JSON.parse(requests[4].data as string)).not.toHaveProperty('total')
    expect(JSON.parse(requests[5].data as string)).toEqual({
      quantity: 3,
      notes: 'No onions',
      version: 4,
    })
    expect(requests[6].params).toEqual({ version: 4 })
    expect(JSON.parse(requests[7].data as string)).toEqual({ status: 'SUBMITTED', version: 4 })
  })
})
