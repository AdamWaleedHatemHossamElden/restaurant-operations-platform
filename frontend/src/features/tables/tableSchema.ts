import { z } from 'zod'

export const tableFormSchema = z.object({
  tableNumber: z
    .string()
    .trim()
    .min(1, 'Table number is required')
    .max(32, 'Use 32 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use only letters, numbers, underscores, or hyphens'),
  displayName: z
    .string()
    .trim()
    .min(1, 'Display name is required')
    .max(120, 'Use 120 characters or fewer'),
  capacity: z
    .number({ error: 'Capacity is required' })
    .int('Capacity must be a whole number')
    .min(1, 'Capacity must be at least 1')
    .max(100, 'Capacity cannot exceed 100'),
  section: z.string().trim().min(1, 'Section is required').max(80, 'Use 80 characters or fewer'),
  status: z.enum(['AVAILABLE', 'OUT_OF_SERVICE']),
})

export type TableFormValues = z.infer<typeof tableFormSchema>
