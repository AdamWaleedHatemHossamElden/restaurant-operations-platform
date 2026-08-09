import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createOrder, listOrders } from '../features/orders/ordersApi'
import type { RestaurantOrder } from '../features/orders/orderTypes'
import { listReservations } from '../features/reservations/reservationsApi'
import { listTables } from '../features/tables/tablesApi'
import { OrdersPage } from './OrdersPage'

vi.mock('../features/orders/ordersApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/orders/ordersApi')>()
  return { ...original, createOrder: vi.fn(), listOrders: vi.fn() }
})
vi.mock('../features/tables/tablesApi', () => ({ listTables: vi.fn() }))
vi.mock('../features/reservations/reservationsApi', () => ({ listReservations: vi.fn() }))

const mockedList = vi.mocked(listOrders)
const mockedCreate = vi.mocked(createOrder)
const mockedTables = vi.mocked(listTables)
const mockedReservations = vi.mocked(listReservations)

const order: RestaurantOrder = {
  id: 7,
  orderNumber: 'ORD-20300101-ABC123',
  status: 'OPEN',
  version: 0,
  restaurantTable: { id: 2, tableNumber: 'T-2', displayName: 'Two', section: 'Main' },
  reservation: null,
  notes: null,
  subtotal: '0.00',
  total: '0.00',
  itemCount: 0,
  createdAt: '2030-01-01T10:00:00Z',
  updatedAt: '2030-01-01T10:00:00Z',
  submittedAt: null,
  completedAt: null,
  cancelledAt: null,
  items: [],
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

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/orders']}>
        <Routes>
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:orderId" element={<h1>Order detail destination</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('orders page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue([order])
    mockedTables.mockResolvedValue([
      {
        id: 2,
        tableNumber: 'T-2',
        displayName: 'Two',
        capacity: 4,
        section: 'Main',
        status: 'AVAILABLE',
        active: true,
        createdAt: '',
        updatedAt: '',
        version: 0,
      },
    ])
    mockedReservations.mockResolvedValue([])
  })

  it('renders populated, empty, loading, and error states', async () => {
    const populated = renderPage()
    expect(await screen.findByRole('heading', { name: order.orderNumber })).toBeInTheDocument()
    expect(screen.getByText('\u20ac0.00')).toBeInTheDocument()
    populated.unmount()

    mockedList.mockResolvedValueOnce([])
    const empty = renderPage()
    expect(
      await screen.findByRole('heading', { name: 'No orders match these filters.' }),
    ).toBeInTheDocument()
    empty.unmount()

    mockedList.mockRejectedValueOnce(new Error('offline'))
    const error = renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Orders could not be loaded.')
    error.unmount()

    mockedList.mockReturnValueOnce(new Promise(() => undefined))
    renderPage()
    expect(screen.getByText(/Loading orders/)).toBeInTheDocument()
  })

  it('sends search, status, table, and sorting filters', async () => {
    renderPage()
    await screen.findByRole('heading', { name: order.orderNumber })
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Order number'), 'ABC')
    await user.selectOptions(screen.getByLabelText('Status'), 'SUBMITTED')
    await user.selectOptions(screen.getByLabelText('Table'), '2')
    await user.selectOptions(screen.getByLabelText('Sort by'), 'total')
    await user.selectOptions(screen.getByLabelText('Direction'), 'ASC')
    await waitFor(() =>
      expect(mockedList).toHaveBeenLastCalledWith(
        expect.objectContaining({
          orderNumber: 'ABC',
          status: 'SUBMITTED',
          tableId: 2,
          sortBy: 'total',
          direction: 'ASC',
        }),
      ),
    )
  })

  it('validates, creates, and navigates to the backend-created order', async () => {
    mockedCreate.mockResolvedValue(order)
    renderPage()
    await screen.findByRole('heading', { name: order.orderNumber })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Create order' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Create order' }))
    expect(await within(dialog).findByText('Select an operational table')).toBeInTheDocument()
    await user.selectOptions(within(dialog).getByLabelText('Restaurant table'), '2')
    await user.type(within(dialog).getByLabelText('Order notes (optional)'), 'Window guest')
    await user.click(within(dialog).getByRole('button', { name: 'Create order' }))
    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith({
        restaurantTableId: 2,
        reservationId: null,
        notes: 'Window guest',
      }),
    )
    expect(
      await screen.findByRole('heading', { name: 'Order detail destination' }),
    ).toBeInTheDocument()
  })
})
