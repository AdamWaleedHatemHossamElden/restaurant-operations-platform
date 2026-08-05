import { z } from 'zod'

export const reservationStatusSchema = z.enum([
  'PENDING',
  'CONFIRMED',
  'SEATED',
  'COMPLETED',
  'CANCELLED',
  'NO_SHOW',
])

export const assignedTableSchema = z.object({
  id: z.number().int().positive(),
  tableNumber: z.string().min(1),
  displayName: z.string().min(1),
  section: z.string().min(1),
  capacity: z.number().int().positive(),
})

export const reservationSchema = z.object({
  id: z.number().int().positive(),
  reservationCode: z.string().min(1),
  guestName: z.string().min(1),
  guestPhone: z.string().min(1),
  guestEmail: z.string().nullable(),
  partySize: z.number().int().positive(),
  startAt: z.string(),
  endAt: z.string(),
  durationMinutes: z.number().int().positive(),
  restaurantTable: assignedTableSchema.nullable(),
  status: reservationStatusSchema,
  notes: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
})

export const reservationsSchema = z.array(reservationSchema)
export const availableTablesSchema = z.array(assignedTableSchema)

export type ReservationStatus = z.infer<typeof reservationStatusSchema>
export type AssignedTable = z.infer<typeof assignedTableSchema>
export type Reservation = z.infer<typeof reservationSchema>

export type ReservationFilters = {
  startFrom?: string
  startTo?: string
  status?: ReservationStatus
  tableId?: number
  assigned?: boolean
  guestName?: string
  reservationCode?: string
  sortBy?: 'reservationCode' | 'guestName' | 'partySize' | 'startAt' | 'durationMinutes' | 'status'
  direction?: 'ASC' | 'DESC'
}

export type ReservationWriteRequest = {
  guestName: string
  guestPhone: string
  guestEmail: string | null
  partySize: number
  startAt: string
  durationMinutes: number
  restaurantTableId: number | null
  notes: string | null
}

export type AvailabilityRequest = {
  startAt: string
  durationMinutes: number
  partySize: number
  excludeReservationId?: number
}
