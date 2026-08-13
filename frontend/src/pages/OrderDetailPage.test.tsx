import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { listCategories, listGroups, listItems } from '../features/menu/menuApi'
import type { MenuCategory, MenuItem, ModifierGroup } from '../features/menu/menuTypes'
import { getKitchenTicketByOrder } from '../features/kitchen/kitchenApi'
import type { KitchenTicket } from '../features/kitchen/kitchenTypes'
import {
  addOrderItem,
  getOrder,
  removeOrderItem,
  transitionOrder,
  updateOrder,
  updateOrderItem,
} from '../features/orders/ordersApi'
import type { RestaurantOrder } from '../features/orders/orderTypes'
import {
  getOrderPaymentSummary,
  issueInvoice,
  recordPayment,
} from '../features/payments/paymentsApi'
import { listReservations } from '../features/reservations/reservationsApi'
import { listTables } from '../features/tables/tablesApi'
import { OrderDetailPage } from './OrderDetailPage'

vi.mock('../features/orders/ordersApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/orders/ordersApi')>()
  return {
    ...original,
    addOrderItem: vi.fn(),
    getOrder: vi.fn(),
    removeOrderItem: vi.fn(),
    transitionOrder: vi.fn(),
    updateOrder: vi.fn(),
    updateOrderItem: vi.fn(),
  }
})
vi.mock('../features/menu/menuApi', () => ({
  listCategories: vi.fn(),
  listGroups: vi.fn(),
  listItems: vi.fn(),
}))
vi.mock('../features/tables/tablesApi', () => ({ listTables: vi.fn() }))
vi.mock('../features/reservations/reservationsApi', () => ({ listReservations: vi.fn() }))
vi.mock('../features/kitchen/kitchenApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/kitchen/kitchenApi')>()
  return { ...original, getKitchenTicketByOrder: vi.fn() }
})
vi.mock('../features/payments/paymentsApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/payments/paymentsApi')>()
  return {
    ...original,
    getInvoice: vi.fn(),
    getOrderPaymentSummary: vi.fn(),
    issueInvoice: vi.fn(),
    recordPayment: vi.fn(),
  }
})

const category: MenuCategory = {
  id: 1,
  name: 'Mains',
  description: null,
  displayOrder: 0,
  active: true,
  createdAt: '',
  updatedAt: '',
  version: 0,
}
const group: ModifierGroup = {
  id: 4,
  name: 'Size',
  description: null,
  selectionType: 'SINGLE',
  minimumSelections: 1,
  maximumSelections: 1,
  displayOrder: 0,
  active: true,
  assignedItemCount: 1,
  options: [
    {
      id: 5,
      modifierGroupId: 4,
      name: 'Regular',
      priceAdjustment: '0.00',
      displayOrder: 0,
      active: true,
      createdAt: '',
      updatedAt: '',
      version: 0,
    },
    {
      id: 6,
      modifierGroupId: 4,
      name: 'Large',
      priceAdjustment: '2.00',
      displayOrder: 1,
      active: true,
      createdAt: '',
      updatedAt: '',
      version: 0,
    },
  ],
  createdAt: '',
  updatedAt: '',
  version: 0,
}
const menuItem: MenuItem = {
  id: 3,
  category: { id: 1, name: 'Mains', active: true },
  code: 'BURGER',
  name: 'Burger',
  description: 'House burger',
  basePrice: '10.00',
  displayOrder: 0,
  active: true,
  availableForSale: true,
  effectivelyAvailable: true,
  modifierGroups: [
    {
      modifierGroupId: 4,
      name: 'Size',
      selectionType: 'SINGLE',
      minimumSelections: 1,
      maximumSelections: 1,
      displayOrder: 0,
      active: true,
    },
  ],
  createdAt: '',
  updatedAt: '',
  version: 0,
}
const order: RestaurantOrder = {
  id: 7,
  orderNumber: 'ORD-20300101-ABC123',
  status: 'OPEN',
  version: 2,
  restaurantTable: { id: 2, tableNumber: 'T-2', displayName: 'Two', section: 'Main' },
  reservation: null,
  notes: null,
  subtotal: '10.00',
  total: '10.00',
  itemCount: 1,
  createdAt: '2030-01-01T10:00:00Z',
  updatedAt: '2030-01-01T10:00:00Z',
  submittedAt: null,
  completedAt: null,
  cancelledAt: null,
  items: [
    {
      id: 8,
      menuItemId: 3,
      itemCode: 'BURGER',
      itemName: 'Burger snapshot',
      basePrice: '10.00',
      quantity: 1,
      notes: null,
      unitTotal: '10.00',
      lineTotal: '10.00',
      displayOrder: 0,
      modifiers: [
        {
          id: 9,
          modifierGroupId: 4,
          modifierOptionId: 5,
          groupName: 'Size snapshot',
          optionName: 'Regular snapshot',
          priceAdjustment: '0.00',
          displayOrder: 0,
        },
      ],
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

const mockedGet = vi.mocked(getOrder)
const mockedAdd = vi.mocked(addOrderItem)
const mockedUpdateItem = vi.mocked(updateOrderItem)
const mockedRemove = vi.mocked(removeOrderItem)
const mockedTransition = vi.mocked(transitionOrder)
const mockedKitchen = vi.mocked(getKitchenTicketByOrder)

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const rendered = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/orders/7']}>
        <Routes>
          <Route path="/orders/:orderId" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...rendered, client }
}

describe('order detail page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedGet.mockResolvedValue(order)
    vi.mocked(listCategories).mockResolvedValue([category])
    vi.mocked(listItems).mockResolvedValue([menuItem])
    vi.mocked(listGroups).mockResolvedValue([group])
    vi.mocked(listTables).mockResolvedValue([])
    vi.mocked(listReservations).mockResolvedValue([])
    mockedAdd.mockResolvedValue({ ...order, version: 3 })
    mockedUpdateItem.mockResolvedValue({ ...order, version: 3 })
    mockedRemove.mockResolvedValue({
      ...order,
      items: [],
      itemCount: 0,
      subtotal: '0.00',
      total: '0.00',
      version: 3,
    })
    mockedTransition.mockResolvedValue({
      ...order,
      status: 'SUBMITTED',
      version: 3,
      submittedAt: '2030-01-01T10:10:00Z',
    })
    vi.mocked(updateOrder).mockResolvedValue({ ...order, version: 3 })
    mockedKitchen.mockResolvedValue(null)
    vi.mocked(getOrderPaymentSummary).mockResolvedValue({
      orderId: 7,
      orderNumber: order.orderNumber,
      orderStatus: 'COMPLETED',
      currency: 'EUR',
      orderTotal: '10.00',
      paidAmount: '0.00',
      outstandingAmount: '10.00',
      paymentState: 'UNPAID',
      invoiceId: null,
      invoiceNumber: null,
      payments: [],
    })
  })

  it('renders stored snapshots, menu browser, total, and local status history', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: order.orderNumber })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Burger snapshot' })).toBeInTheDocument()
    expect(screen.getByText(/Size snapshot: Regular snapshot/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Menu browser' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Status timeline' })).toBeInTheDocument()
  })

  it('validates required modifiers and adds only business inputs', async () => {
    renderPage()
    await screen.findByRole('heading', { name: order.orderNumber })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Add' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Add item' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Size requires 1 to 1 selections',
    )
    await user.click(within(dialog).getByRole('radio', { name: /Large/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Add item' }))
    await waitFor(() =>
      expect(mockedAdd).toHaveBeenCalledWith(order, {
        menuItemId: 3,
        quantity: 1,
        notes: null,
        modifierSelections: [{ modifierGroupId: 4, optionIds: [6] }],
      }),
    )
  })

  it('preserves snapshots for quantity-only edits and reprices modifier changes', async () => {
    renderPage()
    await screen.findByRole('heading', { name: order.orderNumber })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    let dialog = screen.getByRole('dialog')
    const quantity = within(dialog).getByLabelText('Quantity')
    await user.clear(quantity)
    await user.type(quantity, '2')
    await user.click(within(dialog).getByRole('button', { name: 'Save item' }))
    await waitFor(() =>
      expect(mockedUpdateItem).toHaveBeenLastCalledWith(order, order.items[0], {
        quantity: 2,
        notes: null,
      }),
    )

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('radio', { name: /Large/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Save item' }))
    await waitFor(() =>
      expect(mockedUpdateItem).toHaveBeenLastCalledWith(
        expect.anything(),
        expect.anything(),
        expect.objectContaining({ modifierSelections: [{ modifierGroupId: 4, optionIds: [6] }] }),
      ),
    )
  })

  it('allows a quantity-only edit when the current menu gained a required group', async () => {
    const newRequiredGroup: ModifierGroup = {
      ...group,
      id: 7,
      name: 'Preparation',
      options: group.options.map((option) => ({
        ...option,
        id: option.id + 10,
        modifierGroupId: 7,
      })),
    }
    vi.mocked(listItems).mockResolvedValue([
      {
        ...menuItem,
        modifierGroups: [
          ...menuItem.modifierGroups,
          {
            modifierGroupId: 7,
            name: 'Preparation',
            selectionType: 'SINGLE',
            minimumSelections: 1,
            maximumSelections: 1,
            displayOrder: 1,
            active: true,
          },
        ],
      },
    ])
    vi.mocked(listGroups).mockResolvedValue([group, newRequiredGroup])

    renderPage()
    await screen.findByRole('heading', { name: order.orderNumber })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog')
    const quantity = within(dialog).getByLabelText('Quantity')
    await user.clear(quantity)
    await user.type(quantity, '2')
    await user.click(within(dialog).getByRole('button', { name: 'Save item' }))

    await waitFor(() =>
      expect(mockedUpdateItem).toHaveBeenCalledWith(order, order.items[0], {
        quantity: 2,
        notes: null,
      }),
    )
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('confirms item removal and submission', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()
    await screen.findByRole('heading', { name: order.orderNumber })
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))
    await waitFor(() => expect(mockedRemove).toHaveBeenCalledWith(order, order.items[0]))
    await userEvent.click(screen.getByRole('button', { name: 'Submit order' }))
    await waitFor(() =>
      expect(mockedTransition).toHaveBeenCalledWith(
        expect.objectContaining({ id: 7 }),
        'SUBMITTED',
      ),
    )
  })

  it('shows authoritative kitchen state and enables completion only when ready', async () => {
    const submittedOrder = {
      ...order,
      status: 'SUBMITTED' as const,
      submittedAt: '2030-01-01T10:05:00Z',
    }
    const ticket: KitchenTicket = {
      id: 12,
      status: 'PREPARING',
      version: 2,
      orderId: order.id,
      orderNumber: order.orderNumber,
      restaurantTable: order.restaurantTable,
      reservation: null,
      submittedAt: '2030-01-01T10:05:00Z',
      createdAt: '2030-01-01T10:05:00Z',
      startedAt: '2030-01-01T10:06:00Z',
      readyAt: null,
      cancelledAt: null,
      items: [
        {
          id: 13,
          orderItemId: 8,
          itemCode: 'BURGER',
          itemName: 'Burger snapshot',
          quantity: 1,
          notes: null,
          displayOrder: 0,
          status: 'PREPARING',
          startedAt: '2030-01-01T10:06:00Z',
          readyAt: null,
          modifiers: [],
        },
      ],
    }
    mockedGet.mockResolvedValue(submittedOrder)
    mockedKitchen.mockResolvedValue(ticket)
    const rendered = renderPage()

    expect(await screen.findByText('PREPARING')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Complete order' })).toBeDisabled()
    expect(screen.getByText(/must be READY before completion/)).toBeInTheDocument()

    mockedKitchen.mockResolvedValue({
      ...ticket,
      status: 'READY',
      readyAt: '2030-01-01T10:07:00Z',
      items: ticket.items.map((item) => ({ ...item, status: 'READY', readyAt: ticket.readyAt })),
    })
    await rendered.client.invalidateQueries({ queryKey: ['kitchen', 'orders', order.id] })
    expect(await screen.findByText('READY')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Complete order' })).toBeEnabled()
  })

  it('records a completed-order payment with one stable idempotency key', async () => {
    mockedGet.mockResolvedValue({
      ...order,
      status: 'COMPLETED',
      completedAt: '2030-01-01T10:10:00Z',
    })
    vi.mocked(recordPayment).mockResolvedValue({
      id: 20,
      paymentNumber: 'PAY-ONE',
      orderId: 7,
      orderNumber: order.orderNumber,
      method: 'CASH',
      status: 'SUCCEEDED',
      amount: '10.00',
      currency: 'EUR',
      externalReference: null,
      receivedAt: '2030-01-01T10:11:00Z',
      actorUserId: 1,
      reconciliation: null,
    })
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111')

    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Record confirmed payment' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByLabelText(/card number/i)).not.toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))
    await waitFor(() =>
      expect(recordPayment).toHaveBeenCalledWith(
        7,
        { amount: '10.00', method: 'CASH', externalReference: null },
        '11111111-1111-4111-8111-111111111111',
      ),
    )
    expect(issueInvoice).not.toHaveBeenCalled()
  })

  it('refetches authoritative payment state after a payment conflict', async () => {
    mockedGet.mockResolvedValue({
      ...order,
      status: 'COMPLETED',
      subtotal: '30.00',
      total: '30.00',
      completedAt: '2030-01-01T10:10:00Z',
    })
    vi.mocked(getOrderPaymentSummary)
      .mockResolvedValueOnce({
        orderId: 7,
        orderNumber: order.orderNumber,
        orderStatus: 'COMPLETED',
        currency: 'EUR',
        orderTotal: '30.00',
        paidAmount: '10.00',
        outstandingAmount: '20.00',
        paymentState: 'PARTIALLY_PAID',
        invoiceId: null,
        invoiceNumber: null,
        payments: [],
      })
      .mockResolvedValue({
        orderId: 7,
        orderNumber: order.orderNumber,
        orderStatus: 'COMPLETED',
        currency: 'EUR',
        orderTotal: '30.00',
        paidAmount: '25.00',
        outstandingAmount: '5.00',
        paymentState: 'PARTIALLY_PAID',
        invoiceId: null,
        invoiceNumber: null,
        payments: [],
      })
    vi.mocked(recordPayment).mockRejectedValueOnce({
      isAxiosError: true,
      response: {
        status: 409,
        data: { message: 'The outstanding amount changed. Review the latest payment summary.' },
      },
    })
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('22222222-2222-4222-8222-222222222222')

    renderPage()
    const user = userEvent.setup()
    const paymentSummary = (
      await screen.findByRole('heading', { name: 'Payment summary' })
    ).closest('section')!
    expect(await within(paymentSummary).findByText('€10.00')).toBeInTheDocument()
    expect(within(paymentSummary).getByText('€20.00')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Record confirmed payment' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))

    expect(
      await within(dialog).findByText(
        'The outstanding amount changed. Review the latest payment summary.',
      ),
    ).toBeInTheDocument()
    expect(await within(paymentSummary).findByText('€25.00')).toBeInTheDocument()
    expect(within(paymentSummary).getByText('€5.00')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Amount (EUR)')).toHaveValue('20.00')
    expect(recordPayment).toHaveBeenCalledTimes(1)
    expect(recordPayment).toHaveBeenCalledWith(
      7,
      { amount: '20.00', method: 'CASH', externalReference: null },
      '22222222-2222-4222-8222-222222222222',
    )
    expect(getOrderPaymentSummary).toHaveBeenCalledTimes(2)
    expect(issueInvoice).not.toHaveBeenCalled()
  })
})
