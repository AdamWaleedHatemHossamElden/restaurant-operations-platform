import axios from 'axios'

import { apiClient } from '../../lib/apiClient'
import {
  invoiceSchema,
  paymentSchema,
  paymentSummarySchema,
  reconciliationSchema,
  type Invoice,
  type InvoiceFilters,
  type Payment,
  type PaymentFilters,
  type PaymentInput,
  type PaymentSummary,
} from './paymentTypes'

export const paymentKeys = {
  all: ['payments'] as const,
  summary: (orderId: number) => ['payments', 'orders', orderId, 'summary'] as const,
}
export const invoiceKeys = {
  all: ['invoices'] as const,
  detail: (id: number) => ['invoices', id] as const,
}

export async function listPayments(filters: PaymentFilters): Promise<Payment[]> {
  return paymentSchema.array().parse((await apiClient.get('/payments', { params: filters })).data)
}

export async function getOrderPaymentSummary(orderId: number): Promise<PaymentSummary> {
  return paymentSummarySchema.parse(
    (await apiClient.get(`/payments/orders/${orderId}/summary`)).data,
  )
}

export async function recordPayment(
  orderId: number,
  input: PaymentInput,
  idempotencyKey: string,
): Promise<Payment> {
  return paymentSchema.parse(
    (
      await apiClient.post(`/payments/orders/${orderId}`, input, {
        headers: { 'Idempotency-Key': idempotencyKey },
      })
    ).data,
  )
}

export async function reconcilePayment(paymentId: number, reference: string | null) {
  return reconciliationSchema.parse(
    (
      await apiClient.post(`/payments/${paymentId}/reconciliation`, {
        reconciliationReference: reference,
      })
    ).data,
  )
}

export async function listInvoices(filters: InvoiceFilters): Promise<Invoice[]> {
  return invoiceSchema.array().parse((await apiClient.get('/invoices', { params: filters })).data)
}

export async function getInvoice(id: number): Promise<Invoice> {
  return invoiceSchema.parse((await apiClient.get(`/invoices/${id}`)).data)
}

export async function issueInvoice(orderId: number): Promise<Invoice> {
  return invoiceSchema.parse((await apiClient.post(`/invoices/orders/${orderId}`)).data)
}

export function paymentRequestError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data
    const message =
      data && typeof data === 'object' && 'message' in data ? String(data.message) : ''
    if (error.response?.status === 409)
      return message || 'This payment action conflicts with current order state.'
    if (error.response?.status === 400) return message || 'Check the payment details and try again.'
  }
  return 'The payment request could not be completed. Please try again.'
}
