import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { listKitchenTickets, transitionKitchenItem } from '../features/kitchen/kitchenApi'
import { useKitchenRealtimeState } from '../features/kitchen/kitchenRealtimeContext'
import type { KitchenTicket } from '../features/kitchen/kitchenTypes'
import { KitchenPage } from './KitchenPage'

vi.mock('../features/kitchen/kitchenApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/kitchen/kitchenApi')>()
  return { ...original, listKitchenTickets: vi.fn(), transitionKitchenItem: vi.fn() }
})
vi.mock('../features/kitchen/kitchenRealtimeContext', () => ({
  useKitchenRealtimeState: vi.fn(),
}))

const queuedTicket: KitchenTicket = {
  id: 10,
  status: 'QUEUED',
  version: 0,
  orderId: 20,
  orderNumber: 'ORD-20300101-KITCHEN',
  restaurantTable: { id: 2, tableNumber: 'T-2', displayName: 'Two', section: 'Main' },
  reservation: { id: 3, reservationCode: 'RES-20300101-A' },
  submittedAt: '2030-01-01T10:00:00Z',
  createdAt: '2030-01-01T10:00:00Z',
  startedAt: null,
  readyAt: null,
  cancelledAt: null,
  items: [
    {
      id: 11,
      orderItemId: 30,
      itemCode: 'BURGER',
      itemName: 'Burger snapshot',
      quantity: 2,
      notes: 'No onions',
      displayOrder: 0,
      status: 'QUEUED',
      startedAt: null,
      readyAt: null,
      modifiers: [{ groupName: 'Size snapshot', optionName: 'Large snapshot' }],
    },
  ],
}

const mockedList = vi.mocked(listKitchenTickets)
const mockedTransition = vi.mocked(transitionKitchenItem)

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <KitchenPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('kitchen display', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useKitchenRealtimeState).mockReturnValue('connected')
    mockedList.mockResolvedValue([queuedTicket])
    mockedTransition.mockResolvedValue({
      ...queuedTicket,
      status: 'PREPARING',
      version: 1,
      startedAt: '2030-01-01T10:01:00Z',
      items: queuedTicket.items.map((item) => ({
        ...item,
        status: 'PREPARING',
        startedAt: '2030-01-01T10:01:00Z',
      })),
    })
  })

  it('renders the authoritative queue, snapshots, notes, modifiers, and accessible actions', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: queuedTicket.orderNumber })).toHaveAttribute(
      'href',
      '/orders/20',
    )
    expect(screen.getByText('Realtime: connected')).toBeInTheDocument()
    expect(
      screen.getByText((_, element) => element?.textContent === '2× Burger snapshot'),
    ).toBeInTheDocument()
    expect(screen.getByText('Size snapshot: Large snapshot')).toBeInTheDocument()
    expect(screen.getByText('Note: No onions')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start preparing' })).toBeEnabled()
    expect(screen.getByRole('heading', { name: 'PREPARING' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'READY' })).toBeInTheDocument()
  })

  it('progresses an item using the ticket version and refreshes the queue', async () => {
    renderPage()
    await screen.findByRole('link', { name: queuedTicket.orderNumber })
    await userEvent.click(screen.getByRole('button', { name: 'Start preparing' }))
    await waitFor(() =>
      expect(mockedTransition).toHaveBeenCalledWith(queuedTicket, 11, 'PREPARING'),
    )
    expect(await screen.findByText(/Kitchen state updated/)).toBeInTheDocument()
    expect(mockedList).toHaveBeenCalledTimes(2)
  })

  it('shows safe stale conflict feedback and reloads authoritative state', async () => {
    mockedTransition.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { message: 'Kitchen ticket changed by another request' } },
    })
    renderPage()
    await screen.findByRole('button', { name: 'Start preparing' })
    await userEvent.click(screen.getByRole('button', { name: 'Start preparing' }))
    expect(await screen.findByText(/ticket changed elsewhere/i)).toBeInTheDocument()
    expect(mockedList).toHaveBeenCalledTimes(2)
  })

  it('supports cancelled history and keeps cancelled tickets read-only', async () => {
    const cancelled = {
      ...queuedTicket,
      id: 40,
      status: 'CANCELLED' as const,
      cancelledAt: '2030-01-01T10:02:00Z',
    }
    mockedList.mockResolvedValue([cancelled])
    renderPage()
    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Queue view'), 'CANCELLED')
    const card = await screen.findByText('Cancelled — preparation controls are disabled.')
    expect(
      within(card.closest('article')!).queryByRole('button', { name: /preparing/i }),
    ).toBeNull()
    expect(mockedList).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'CANCELLED', includeCancelled: true }),
    )
  })

  it.each([
    ['empty', []],
    ['error', null],
  ])('renders the %s queue state', async (_label, data) => {
    if (data === null) mockedList.mockRejectedValue(new Error('offline'))
    else mockedList.mockResolvedValue(data)
    renderPage()
    if (data === null) {
      expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded')
    } else {
      expect(
        await screen.findByRole('heading', { name: 'No kitchen tickets match.' }),
      ).toBeInTheDocument()
    }
  })
})
