import { z } from 'zod'

export const reservationFormSchema = z.object({
  guestName: z.string().trim().min(1, 'Guest name is required').max(160),
  guestPhone: z
    .string()
    .trim()
    .regex(/^[+0-9() .-]{7,32}$/, 'Enter a valid phone number'),
  guestEmail: z.union([z.string().trim().email('Enter a valid email'), z.literal('')]),
  partySize: z
    .number({ error: 'Party size is required' })
    .int('Party size must be a whole number')
    .min(1, 'Party size must be at least 1')
    .max(100, 'Party size cannot exceed 100'),
  startLocal: z
    .string()
    .min(1, 'Date and time are required')
    .refine((value) => {
      return !Number.isNaN(new Date(value).getTime())
    }, 'Enter a valid date and time'),
  durationMinutes: z
    .number({ error: 'Duration is required' })
    .int()
    .min(15, 'Duration must be at least 15 minutes')
    .max(480, 'Duration cannot exceed 8 hours'),
  restaurantTableId: z.string(),
  notes: z.string().trim().max(2000, 'Use 2000 characters or fewer'),
})

export type ReservationFormValues = z.infer<typeof reservationFormSchema>
