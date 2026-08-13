import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as reportsApi from '../features/reports/reportsApi'
import { localDateInput } from '../features/reports/reportTime'
import { ReportsPage } from './ReportsPage'

vi.mock('../features/reports/reportsApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/reports/reportsApi')>()
  return {
    ...original,
    getOverviewReport: vi.fn(),
    getSalesReport: vi.fn(),
    getMenuPerformanceReport: vi.fn(),
    getPaymentsReport: vi.fn(),
    getReservationsReport: vi.fn(),
    getKitchenReport: vi.fn(),
    getInventoryReport: vi.fn(),
    getStaffReport: vi.fn(),
    downloadReport: vi.fn(),
  }
})

const period = { from: '2030-01-01T00:00:00Z', to: '2030-02-01T00:00:00Z' }

function renderPage() {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <ReportsPage />
    </QueryClientProvider>,
  )
}

describe('reports page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(reportsApi.getOverviewReport).mockResolvedValue({
      period,
      completedOrders: 12,
      completedOrderValue: '360.00',
      averageCompletedOrderValue: '30.00',
      paymentsReceived: '300.00',
      paymentCount: 10,
      reconciledPaymentCount: 8,
      invoiceCount: 6,
      reservations: 14,
      readyKitchenTickets: 11,
      scheduledStaffHours: '72.50',
    })
    vi.mocked(reportsApi.getSalesReport).mockResolvedValue({
      period,
      groupBy: 'DAY',
      completedOrders: 12,
      completedOrderValue: '360.00',
      averageCompletedOrderValue: '30.00',
      series: [{ bucket: '2030-01-01', count: 2, amount: '60.00' }],
      byTable: [
        {
          tableId: 1,
          tableNumber: 'T1',
          displayName: 'Window',
          completedOrders: 2,
          completedOrderValue: '60.00',
        },
      ],
      topOrders: [],
    })
    vi.mocked(reportsApi.getMenuPerformanceReport).mockResolvedValue({
      period,
      top: 10,
      items: [
        {
          menuItemId: 1,
          itemCode: 'PASTA',
          itemName: 'Historical pasta',
          quantitySold: 5,
          completedOrders: 3,
          completedOrderLineValue: '75.00',
          averageQuantityPerOrder: '1.67',
        },
      ],
    })
    vi.mocked(reportsApi.getPaymentsReport).mockResolvedValue({
      period,
      groupBy: 'DAY',
      paymentsReceived: '300.00',
      paymentCount: 10,
      averagePaymentAmount: '30.00',
      reconciledCount: 8,
      unreconciledCount: 2,
      reconciledAmount: '250.00',
      unreconciledAmount: '50.00',
      byMethod: [{ method: 'CARD', count: 7, amount: '220.00' }],
      series: [],
    })
    vi.mocked(reportsApi.getReservationsReport).mockResolvedValue({
      period,
      reservations: 14,
      plannedGuests: 42,
      averagePartySize: '3.00',
      byStatus: [{ key: 'CONFIRMED', count: 8 }],
      byTable: [],
    })
    vi.mocked(reportsApi.getKitchenReport).mockResolvedValue({
      period,
      ticketsCreated: 11,
      readyTickets: 9,
      cancelledTickets: 1,
      averagePreparationMinutes: '18.50',
      byStatus: [],
      itemPreparation: [],
    })
    vi.mocked(reportsApi.getInventoryReport).mockResolvedValue({
      period,
      movementCount: 7,
      currentLowStockItems: 2,
      movementCounts: [],
      items: [
        {
          inventoryItemId: 1,
          code: 'FLOUR',
          name: 'Flour',
          unit: 'GRAM',
          receipt: '1000.0000',
          usage: '250.0000',
          waste: '0.0000',
          adjustmentIn: '0.0000',
          adjustmentOut: '0.0000',
          netMovement: '750.0000',
          currentOnHand: '750.0000',
          currentlyLowStock: false,
        },
      ],
    })
    vi.mocked(reportsApi.getStaffReport).mockResolvedValue({
      period,
      shiftCount: 4,
      scheduledCount: 2,
      completedCount: 1,
      cancelledCount: 1,
      scheduledHours: '18.00',
      completedShiftHours: '8.00',
      hoursByRole: [{ key: 'WAITER', shifts: 2, hours: '10.00' }],
      hoursByEmployee: [],
    })
    vi.mocked(reportsApi.downloadReport).mockResolvedValue(undefined)
  })

  it('loads the overview and switches to server-authoritative report sections', async () => {
    renderPage()
    expect(await screen.findByText('€360.00')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Sales' }))
    expect(await screen.findByText('T1 · Window')).toBeInTheDocument()
    expect(reportsApi.getSalesReport).toHaveBeenCalledWith(
      expect.objectContaining({ from: expect.any(String), to: expect.any(String) }),
      'DAY',
    )
    await userEvent.click(screen.getByRole('tab', { name: 'Inventory' }))
    expect(await screen.findByText('Flour')).toBeInTheDocument()
    expect(screen.getByText('GRAM')).toBeInTheDocument()
  })

  it('applies custom half-open dates and grouping to refreshed queries', async () => {
    renderPage()
    await screen.findByText('Operational overview')
    const user = userEvent.setup()
    await user.clear(screen.getByLabelText('Start date'))
    await user.type(screen.getByLabelText('Start date'), '2030-01-01')
    await user.clear(screen.getByLabelText('End date (exclusive)'))
    await user.type(screen.getByLabelText('End date (exclusive)'), '2030-02-01')
    expect(screen.getByRole('button', { name: 'Last 30 days' })).not.toHaveClass('is-active')
    await user.selectOptions(screen.getByLabelText('Group by'), 'MONTH')
    await user.click(screen.getByRole('button', { name: 'Apply dates' }))
    await user.click(screen.getByRole('tab', { name: 'Payments' }))
    await waitFor(() => expect(reportsApi.getPaymentsReport).toHaveBeenCalled())
    const [requestedRange, requestedGroup] = vi.mocked(reportsApi.getPaymentsReport).mock.calls[0]
    expect(localDateInput(requestedRange.from)).toBe('2030-01-01')
    expect(localDateInput(requestedRange.to)).toBe('2030-02-01')
    expect(requestedGroup).toBe('MONTH')
  })

  it('keeps report errors controlled and retries them', async () => {
    vi.mocked(reportsApi.getOverviewReport).mockRejectedValueOnce(new Error('Unavailable'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Operational overview')).toBeInTheDocument()
  })

  it('requests CSV export for the active range without synthesizing data locally', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('tab', { name: 'Menu' }))
    await screen.findByText('Historical pasta')
    await userEvent.click(screen.getByRole('button', { name: 'Export menu CSV' }))
    await waitFor(() =>
      expect(reportsApi.downloadReport).toHaveBeenCalledWith(
        'menu',
        expect.objectContaining({ from: expect.any(String), to: expect.any(String) }),
        'DAY',
      ),
    )
  })

  it('shows an empty overview without NaN or broken chart output', async () => {
    vi.mocked(reportsApi.getOverviewReport).mockResolvedValueOnce({
      period,
      completedOrders: 0,
      completedOrderValue: '0.00',
      averageCompletedOrderValue: '0.00',
      paymentsReceived: '0.00',
      paymentCount: 0,
      reconciledPaymentCount: 0,
      invoiceCount: 0,
      reservations: 0,
      readyKitchenTickets: 0,
      scheduledStaffHours: '0.00',
    })
    renderPage()
    expect(await screen.findByText('No operational activity in this period.')).toBeInTheDocument()
    expect(screen.queryByText('NaN')).not.toBeInTheDocument()
  })

  it('announces export failures and does not show false success', async () => {
    vi.mocked(reportsApi.downloadReport).mockRejectedValueOnce(new Error('Network unavailable'))
    renderPage()
    await userEvent.click(screen.getByRole('tab', { name: 'Sales' }))
    await screen.findByText('Completed-order sales')
    await userEvent.click(screen.getByRole('button', { name: 'Export sales CSV' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be downloaded')
  })
})
