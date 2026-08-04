import { z } from 'zod'

export const tableStatusSchema = z.enum(['AVAILABLE', 'OUT_OF_SERVICE'])

export const restaurantTableSchema = z.object({
  id: z.number().int().positive(),
  tableNumber: z.string().min(1),
  displayName: z.string().min(1),
  capacity: z.number().int().positive(),
  section: z.string().min(1),
  status: tableStatusSchema,
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
})

export const restaurantTablesSchema = z.array(restaurantTableSchema)

export type TableStatus = z.infer<typeof tableStatusSchema>
export type RestaurantTable = z.infer<typeof restaurantTableSchema>

export type TableFilters = {
  active?: boolean
  section?: string
  status?: TableStatus
  tableNumber?: string
  sortBy?: 'tableNumber' | 'displayName' | 'capacity' | 'section' | 'status' | 'active'
  direction?: 'ASC' | 'DESC'
}

export type TableWriteRequest = {
  tableNumber: string
  displayName: string
  capacity: number
  section: string
  status: TableStatus
}
