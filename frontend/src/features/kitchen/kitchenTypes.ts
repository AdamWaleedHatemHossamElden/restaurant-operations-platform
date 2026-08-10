import { z } from 'zod'

export const kitchenTicketStatusSchema = z.enum(['QUEUED', 'PREPARING', 'READY', 'CANCELLED'])
export const kitchenItemStatusSchema = z.enum(['QUEUED', 'PREPARING', 'READY'])

const kitchenTableSchema = z.object({
  id: z.number().int().positive(),
  tableNumber: z.string(),
  displayName: z.string(),
  section: z.string(),
})

const kitchenReservationSchema = z.object({
  id: z.number().int().positive(),
  reservationCode: z.string(),
})

const kitchenModifierSchema = z.object({
  groupName: z.string(),
  optionName: z.string(),
})

export const kitchenItemSchema = z.object({
  id: z.number().int().positive(),
  orderItemId: z.number().int().positive(),
  itemCode: z.string(),
  itemName: z.string(),
  quantity: z.number().int().positive(),
  notes: z.string().nullable(),
  displayOrder: z.number().int().nonnegative(),
  status: kitchenItemStatusSchema,
  startedAt: z.string().nullable(),
  readyAt: z.string().nullable(),
  modifiers: z.array(kitchenModifierSchema),
})

export const kitchenTicketSchema = z.object({
  id: z.number().int().positive(),
  status: kitchenTicketStatusSchema,
  version: z.number().int().nonnegative(),
  orderId: z.number().int().positive(),
  orderNumber: z.string(),
  restaurantTable: kitchenTableSchema,
  reservation: kitchenReservationSchema.nullable(),
  submittedAt: z.string(),
  createdAt: z.string(),
  startedAt: z.string().nullable(),
  readyAt: z.string().nullable(),
  cancelledAt: z.string().nullable(),
  items: z.array(kitchenItemSchema),
})

export const kitchenTicketsSchema = z.array(kitchenTicketSchema)

export const kitchenRealtimeEventSchema = z.object({
  eventType: z.enum([
    'KITCHEN_TICKET_CREATED',
    'KITCHEN_ITEM_STATUS_CHANGED',
    'KITCHEN_TICKET_STATUS_CHANGED',
    'KITCHEN_TICKET_CANCELLED',
  ]),
  ticketId: z.number().int().positive(),
  orderId: z.number().int().positive(),
  orderNumber: z.string(),
  ticketStatus: kitchenTicketStatusSchema,
  kitchenItemId: z.number().int().positive().nullable(),
  kitchenItemStatus: kitchenItemStatusSchema.nullable(),
  timestamp: z.string(),
})

export type KitchenTicketStatus = z.infer<typeof kitchenTicketStatusSchema>
export type KitchenItemStatus = z.infer<typeof kitchenItemStatusSchema>
export type KitchenTicket = z.infer<typeof kitchenTicketSchema>
export type KitchenItem = z.infer<typeof kitchenItemSchema>
export type KitchenRealtimeEvent = z.infer<typeof kitchenRealtimeEventSchema>

export type KitchenFilters = {
  status?: KitchenTicketStatus
  tableId?: number
  orderNumber?: string
  submittedFrom?: string
  submittedTo?: string
  includeCancelled?: boolean
  sortBy?: 'createdAt' | 'submittedAt' | 'orderNumber' | 'table' | 'status'
  direction?: 'ASC' | 'DESC'
}
