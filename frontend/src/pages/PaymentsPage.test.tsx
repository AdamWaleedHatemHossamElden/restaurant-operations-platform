import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { listInvoices, listPayments, reconcilePayment } from '../features/payments/paymentsApi'
import type { Invoice, Payment } from '../features/payments/paymentTypes'
import { PaymentsPage } from './PaymentsPage'

vi.mock('../features/payments/paymentsApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/payments/paymentsApi')>()
  return { ...original, listInvoices: vi.fn(), listPayments: vi.fn(), reconcilePayment: vi.fn() }
})

const payment: Payment = {
  id: 1,
  paymentNumber: 'PAY-ONE',
  orderId: 7,
  orderNumber: 'ORD-ONE',
  method: 'CASH',
  status: 'SUCCEEDED',
  amount: '12.50',
  currency: 'EUR',
  externalReference: null,
  receivedAt: '2030-01-01T10:00:00Z',
  actorUserId: 1,
  reconciliation: null,
}
const invoice: Invoice = {
  id: 2,
  invoiceNumber: 'INV-ONE',
  orderId: 7,
  orderNumber: 'ORD-ONE',
  currency: 'EUR',
  subtotal: '12.50',
  total: '12.50',
  paidTotal: '12.50',
  issuedAt: '2030-01-01T10:05:00Z',
  actorUserId: 1,
  items: [
    {
      id: 3,
      sourceOrderItemId: 4,
      itemCode: 'MEAL',
      itemName: 'Meal snapshot',
      quantity: 1,
      basePrice: '12.50',
      unitTotal: '12.50',
      lineTotal: '12.50',
      displayOrder: 0,
      modifiers: [],
    },
  ],
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PaymentsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('payments page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listPayments).mockResolvedValue([payment])
    vi.mocked(listInvoices).mockResolvedValue([invoice])
    vi.mocked(reconcilePayment).mockResolvedValue({
      id: 4,
      paymentId: 1,
      reconciliationReference: 'BATCH',
      reconciledAt: '2030-01-01T10:06:00Z',
      actorUserId: 1,
    })
  })

  it('filters the payment ledger and reconciles an unreconciled payment', async () => {
    renderPage()
    expect(await screen.findByText('PAY-ONE')).toBeInTheDocument()
    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Method'), 'CASH')
    await waitFor(() =>
      expect(listPayments).toHaveBeenLastCalledWith(expect.objectContaining({ method: 'CASH' })),
    )
    await user.click(screen.getByRole('button', { name: 'Reconcile' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Reconciliation reference (optional)'), 'BATCH')
    await user.click(within(dialog).getByRole('button', { name: 'Confirm reconciliation' }))
    await waitFor(() => expect(reconcilePayment).toHaveBeenCalledWith(1, 'BATCH'))
  })

  it('shows immutable invoice detail and print controls', async () => {
    renderPage()
    const user = userEvent.setup()
    await user.click(screen.getByRole('tab', { name: 'Invoices' }))
    await user.click(await screen.findByRole('button', { name: 'View invoice' }))
    expect(screen.getByRole('heading', { name: 'INV-ONE' })).toBeInTheDocument()
    expect(screen.getByText((content) => content.includes('Meal snapshot'))).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Print invoice' })).toBeInTheDocument()
  })

  it('renders safe loading, empty, and error states', async () => {
    vi.mocked(listPayments).mockReturnValueOnce(new Promise(() => undefined))
    const loading = renderPage()
    expect(screen.getByText(/Loading records/)).toBeInTheDocument()
    loading.unmount()
    vi.mocked(listPayments).mockResolvedValueOnce([])
    const empty = renderPage()
    expect(
      await screen.findByRole('heading', { name: 'No payment records match.' }),
    ).toBeInTheDocument()
    empty.unmount()
    vi.mocked(listPayments).mockRejectedValueOnce(new Error('offline'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Payment records could not be loaded.',
    )
  })
})
