import { z } from 'zod'

export const paymentMethodSchema = z.enum(['CASH', 'CARD', 'BANK_TRANSFER', 'OTHER'])
export const paymentStateSchema = z.enum(['UNPAID', 'PARTIALLY_PAID', 'PAID'])

export const reconciliationSchema = z.object({
  id: z.number().int().positive(),
  paymentId: z.number().int().positive(),
  reconciliationReference: z.string().nullable(),
  reconciledAt: z.string(),
  actorUserId: z.number().int().positive(),
})

export const paymentSchema = z.object({
  id: z.number().int().positive(),
  paymentNumber: z.string(),
  orderId: z.number().int().positive(),
  orderNumber: z.string(),
  method: paymentMethodSchema,
  status: z.literal('SUCCEEDED'),
  amount: z.string(),
  currency: z.literal('EUR'),
  externalReference: z.string().nullable(),
  receivedAt: z.string(),
  actorUserId: z.number().int().positive(),
  reconciliation: reconciliationSchema.nullable(),
})

export const paymentSummarySchema = z.object({
  orderId: z.number().int().positive(),
  orderNumber: z.string(),
  orderStatus: z.string(),
  currency: z.literal('EUR'),
  orderTotal: z.string(),
  paidAmount: z.string(),
  outstandingAmount: z.string(),
  paymentState: paymentStateSchema,
  invoiceId: z.number().int().positive().nullable(),
  invoiceNumber: z.string().nullable(),
  payments: z.array(paymentSchema),
})

const invoiceModifierSchema = z.object({
  id: z.number().int().positive(),
  groupName: z.string(),
  optionName: z.string(),
  priceAdjustment: z.string(),
  displayOrder: z.number().int().nonnegative(),
})

const invoiceItemSchema = z.object({
  id: z.number().int().positive(),
  sourceOrderItemId: z.number().int().positive(),
  itemCode: z.string(),
  itemName: z.string(),
  quantity: z.number().int().positive(),
  basePrice: z.string(),
  unitTotal: z.string(),
  lineTotal: z.string(),
  displayOrder: z.number().int().nonnegative(),
  modifiers: z.array(invoiceModifierSchema),
})

export const invoiceSchema = z.object({
  id: z.number().int().positive(),
  invoiceNumber: z.string(),
  orderId: z.number().int().positive(),
  orderNumber: z.string(),
  currency: z.literal('EUR'),
  subtotal: z.string(),
  total: z.string(),
  paidTotal: z.string(),
  issuedAt: z.string(),
  actorUserId: z.number().int().positive(),
  items: z.array(invoiceItemSchema),
})

export type PaymentMethod = z.infer<typeof paymentMethodSchema>
export type Payment = z.infer<typeof paymentSchema>
export type PaymentSummary = z.infer<typeof paymentSummarySchema>
export type Invoice = z.infer<typeof invoiceSchema>

export type PaymentInput = {
  amount: string
  method: PaymentMethod
  externalReference: string | null
}

export type PaymentFilters = {
  method?: PaymentMethod
  reconciled?: boolean
  orderId?: number
  receivedFrom?: string
  receivedTo?: string
  search?: string
  sortBy?: 'receivedAt' | 'paymentNumber' | 'amount' | 'method'
  direction?: 'ASC' | 'DESC'
}

export type InvoiceFilters = {
  search?: string
  issuedFrom?: string
  issuedTo?: string
  sortBy?: 'issuedAt' | 'invoiceNumber' | 'total'
  direction?: 'ASC' | 'DESC'
}
