import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { apiClient, setApiAccessToken } from '../../lib/apiClient'
import {
  downloadReport,
  getInventoryReport,
  getKitchenReport,
  getMenuPerformanceReport,
  getOverviewReport,
  getPaymentsReport,
  getReservationsReport,
  getSalesReport,
  getStaffReport,
} from './reportsApi'

const originalAdapter = apiClient.defaults.adapter
const period = { from: '2030-01-01T00:00:00Z', to: '2030-01-02T00:00:00Z' }

describe('reports API', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
    vi.restoreAllMocks()
  })

  it('loads every report through the authenticated client with the shared range', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setApiAccessToken('memory-only-report-token')
    apiClient.defaults.adapter = async (config) => {
      requests.push(config)
      const common = { period }
      const byUrl: Record<string, unknown> = {
        '/reports/overview': {
          ...common,
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
        },
        '/reports/sales': {
          ...common,
          groupBy: 'DAY',
          completedOrders: 0,
          completedOrderValue: '0.00',
          averageCompletedOrderValue: '0.00',
          series: [],
          byTable: [],
          topOrders: [],
        },
        '/reports/menu-performance': { ...common, top: 10, items: [] },
        '/reports/payments': {
          ...common,
          groupBy: 'DAY',
          paymentsReceived: '0.00',
          paymentCount: 0,
          averagePaymentAmount: '0.00',
          reconciledCount: 0,
          unreconciledCount: 0,
          reconciledAmount: '0.00',
          unreconciledAmount: '0.00',
          byMethod: [],
          series: [],
        },
        '/reports/reservations': {
          ...common,
          reservations: 0,
          plannedGuests: 0,
          averagePartySize: '0.00',
          byStatus: [],
          byTable: [],
        },
        '/reports/kitchen': {
          ...common,
          ticketsCreated: 0,
          readyTickets: 0,
          cancelledTickets: 0,
          averagePreparationMinutes: '0.00',
          byStatus: [],
          itemPreparation: [],
        },
        '/reports/inventory': {
          ...common,
          movementCount: 0,
          currentLowStockItems: 0,
          movementCounts: [],
          items: [],
        },
        '/reports/staff': {
          ...common,
          shiftCount: 0,
          scheduledCount: 0,
          completedCount: 0,
          cancelledCount: 0,
          scheduledHours: '0.00',
          completedShiftHours: '0.00',
          hoursByRole: [],
          hoursByEmployee: [],
        },
      }
      return {
        data: byUrl[String(config.url)],
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      } as AxiosResponse
    }

    await Promise.all([
      getOverviewReport(period),
      getSalesReport(period, 'DAY'),
      getMenuPerformanceReport(period),
      getPaymentsReport(period, 'DAY'),
      getReservationsReport(period),
      getKitchenReport(period),
      getInventoryReport(period),
      getStaffReport(period),
    ])

    expect(requests).toHaveLength(8)
    expect(
      requests.every(
        (request) => request.headers.Authorization === 'Bearer memory-only-report-token',
      ),
    ).toBe(true)
    expect(
      requests.every(
        (request) => request.params.from === period.from && request.params.to === period.to,
      ),
    ).toBe(true)
  })

  it('downloads the server CSV filename without putting authentication in the URL', async () => {
    const clicked = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:report')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    setApiAccessToken('memory-only-report-token')
    let request: InternalAxiosRequestConfig | undefined
    apiClient.defaults.adapter = async (config) => {
      request = config
      return {
        data: new Blob(['safe']),
        status: 200,
        statusText: 'OK',
        headers: { 'content-disposition': 'attachment; filename="restaurant-sales-report.csv"' },
        config,
      } as AxiosResponse
    }

    await downloadReport('sales', period, 'WEEK')

    expect(clicked).toHaveBeenCalledOnce()
    expect(request?.url).toBe('/reports/exports/sales.csv')
    expect(request?.params).toEqual({ ...period, groupBy: 'WEEK' })
    expect(request?.headers.Authorization).toBe('Bearer memory-only-report-token')
  })
})
