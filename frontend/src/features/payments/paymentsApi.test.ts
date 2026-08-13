import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { apiClient, setApiAccessToken } from '../../lib/apiClient'
import {
  getOrderPaymentSummary,
  issueInvoice,
  listInvoices,
  listPayments,
  reconcilePayment,
  recordPayment,
} from './paymentsApi'

const originalAdapter = apiClient.defaults.adapter
const payment = {
  id: 1,
  paymentNumber: 'PAY-20300101-ABC12345',
  orderId: 7,
  orderNumber: 'ORD-7',
  method: 'CARD',
  status: 'SUCCEEDED',
  amount: '10.00',
  currency: 'EUR',
  externalReference: 'TERM-1',
  receivedAt: '2030-01-01T10:00:00Z',
  actorUserId: 1,
  reconciliation: null,
} as const
const invoice = {
  id: 2,
  invoiceNumber: 'INV-20300101-ABC12345',
  orderId: 7,
  orderNumber: 'ORD-7',
  currency: 'EUR',
  subtotal: '10.00',
  total: '10.00',
  paidTotal: '10.00',
  issuedAt: '2030-01-01T10:01:00Z',
  actorUserId: 1,
  items: [],
} as const

describe('payments API', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
  })

  it('uses the authenticated client and keeps retry-safe payment keys in headers', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setApiAccessToken('memory-only-test-token')
    apiClient.defaults.adapter = async (config) => {
      requests.push(config)
      let data: unknown = payment
      if (config.url === '/payments') data = [payment]
      if (config.url?.endsWith('/summary')) {
        data = {
          orderId: 7,
          orderNumber: 'ORD-7',
          orderStatus: 'COMPLETED',
          currency: 'EUR',
          orderTotal: '10.00',
          paidAmount: '10.00',
          outstandingAmount: '0.00',
          paymentState: 'PAID',
          invoiceId: 2,
          invoiceNumber: invoice.invoiceNumber,
          payments: [payment],
        }
      }
      if (config.url?.endsWith('/reconciliation'))
        data = {
          id: 3,
          paymentId: 1,
          reconciliationReference: 'BATCH-1',
          reconciledAt: '2030-01-01T10:02:00Z',
          actorUserId: 1,
        }
      if (config.url === '/invoices') data = [invoice]
      if (config.url?.startsWith('/invoices/orders/')) data = invoice
      return { data, status: 200, statusText: 'OK', headers: {}, config } as AxiosResponse
    }

    await listPayments({ method: 'CARD' })
    await getOrderPaymentSummary(7)
    await recordPayment(
      7,
      { amount: '10.00', method: 'CARD', externalReference: 'TERM-1' },
      'stable-key-123456',
    )
    await reconcilePayment(1, 'BATCH-1')
    await listInvoices({ search: 'ORD-7' })
    await issueInvoice(7)

    expect(requests).toHaveLength(6)
    expect(
      requests.every(
        (request) => request.headers.Authorization === 'Bearer memory-only-test-token',
      ),
    ).toBe(true)
    expect(requests[2].headers['Idempotency-Key']).toBe('stable-key-123456')
    expect(JSON.parse(String(requests[2].data))).toEqual({
      amount: '10.00',
      method: 'CARD',
      externalReference: 'TERM-1',
    })
    expect(String(requests[2].data).toLowerCase()).not.toMatch(/cardnumber|cvv|pan/)
  })
})
