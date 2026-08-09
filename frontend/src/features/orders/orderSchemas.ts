import { z } from 'zod'

export const orderFormSchema = z.object({
  restaurantTableId: z.string().min(1, 'Select an operational table'),
  reservationId: z.string(),
  notes: z.string().max(2000, 'Notes must be 2,000 characters or fewer'),
})

export const orderItemFormSchema = z.object({
  quantity: z.number().int().min(1, 'Quantity must be at least 1').max(99),
  notes: z.string().max(1000, 'Notes must be 1,000 characters or fewer'),
})

export type OrderFormValues = z.infer<typeof orderFormSchema>
export type OrderItemFormValues = z.infer<typeof orderItemFormSchema>
