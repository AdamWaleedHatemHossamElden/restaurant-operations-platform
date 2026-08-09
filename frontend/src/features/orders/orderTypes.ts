import { z } from 'zod'

export const orderStatusSchema = z.enum(['OPEN', 'SUBMITTED', 'COMPLETED', 'CANCELLED'])

const tableSummarySchema = z.object({
  id: z.number().int().positive(),
  tableNumber: z.string(),
  displayName: z.string(),
  section: z.string(),
})

const reservationSummarySchema = z.object({
  id: z.number().int().positive(),
  reservationCode: z.string(),
  guestName: z.string(),
})

const modifierSnapshotSchema = z.object({
  id: z.number().int().positive(),
  modifierGroupId: z.number().int().positive(),
  modifierOptionId: z.number().int().positive(),
  groupName: z.string(),
  optionName: z.string(),
  priceAdjustment: z.string(),
  displayOrder: z.number().int().nonnegative(),
})

export const orderItemSchema = z.object({
  id: z.number().int().positive(),
  menuItemId: z.number().int().positive(),
  itemCode: z.string(),
  itemName: z.string(),
  basePrice: z.string(),
  quantity: z.number().int().positive(),
  notes: z.string().nullable(),
  unitTotal: z.string(),
  lineTotal: z.string(),
  displayOrder: z.number().int().nonnegative(),
  modifiers: z.array(modifierSnapshotSchema),
  createdAt: z.string(),
  updatedAt: z.string(),
})

const statusHistorySchema = z.object({
  id: z.number().int().positive(),
  fromStatus: orderStatusSchema.nullable(),
  toStatus: orderStatusSchema,
  changedAt: z.string(),
  changedByUserId: z.number().int().positive().nullable(),
})

export const orderSchema = z.object({
  id: z.number().int().positive(),
  orderNumber: z.string(),
  status: orderStatusSchema,
  version: z.number().int().nonnegative(),
  restaurantTable: tableSummarySchema,
  reservation: reservationSummarySchema.nullable(),
  notes: z.string().nullable(),
  subtotal: z.string(),
  total: z.string(),
  itemCount: z.number().int().nonnegative(),
  createdAt: z.string(),
  updatedAt: z.string(),
  submittedAt: z.string().nullable(),
  completedAt: z.string().nullable(),
  cancelledAt: z.string().nullable(),
  items: z.array(orderItemSchema),
  history: z.array(statusHistorySchema),
})

export const ordersSchema = z.array(orderSchema)

export type OrderStatus = z.infer<typeof orderStatusSchema>
export type RestaurantOrder = z.infer<typeof orderSchema>
export type OrderItem = z.infer<typeof orderItemSchema>
export type ModifierSelectionInput = { modifierGroupId: number; optionIds: number[] }

export type OrderFilters = {
  status?: OrderStatus
  tableId?: number
  reservationId?: number
  orderNumber?: string
  createdFrom?: string
  createdTo?: string
  sortBy?: 'createdAt' | 'orderNumber' | 'status' | 'total'
  direction?: 'ASC' | 'DESC'
}

export type OrderCreateInput = {
  restaurantTableId: number
  reservationId: number | null
  notes: string | null
}

export type OrderItemInput = {
  menuItemId: number
  quantity: number
  notes: string | null
  modifierSelections: ModifierSelectionInput[]
}
