import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from '../../lib/apiClient'
import { getKitchenTicketByOrder, listKitchenTickets, transitionKitchenItem } from './kitchenApi'
import type { KitchenTicket } from './kitchenTypes'

vi.mock('../../lib/apiClient', () => ({
  apiClient: { get: vi.fn(), patch: vi.fn() },
}))

const ticket: KitchenTicket = {
  id: 1,
  status: 'QUEUED',
  version: 0,
  orderId: 2,
  orderNumber: 'ORD-1',
  restaurantTable: { id: 3, tableNumber: 'T-3', displayName: 'Three', section: 'Main' },
  reservation: null,
  submittedAt: '2030-01-01T10:00:00Z',
  createdAt: '2030-01-01T10:00:00Z',
  startedAt: null,
  readyAt: null,
  cancelledAt: null,
  items: [],
}

describe('kitchen API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses the authenticated API and sends only status plus aggregate version', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [ticket] })
    vi.mocked(apiClient.patch).mockResolvedValue({ data: { ...ticket, status: 'PREPARING' } })
    await listKitchenTickets({ status: 'QUEUED', sortBy: 'submittedAt', direction: 'ASC' })
    expect(apiClient.get).toHaveBeenCalledWith('/kitchen/tickets', {
      params: { status: 'QUEUED', sortBy: 'submittedAt', direction: 'ASC' },
    })
    await transitionKitchenItem(ticket, 8, 'PREPARING')
    expect(apiClient.patch).toHaveBeenCalledWith('/kitchen/tickets/1/items/8/status', {
      status: 'PREPARING',
      version: 0,
    })
  })

  it('treats a missing order ticket as historical no-ticket state', async () => {
    vi.mocked(apiClient.get).mockRejectedValue({ isAxiosError: true, response: { status: 404 } })
    await expect(getKitchenTicketByOrder(2)).resolves.toBeNull()
  })
})
