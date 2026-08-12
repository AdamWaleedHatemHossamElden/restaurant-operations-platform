import { z } from 'zod'

export const operationalRoleSchema = z.enum([
  'HOST',
  'WAITER',
  'CASHIER',
  'KITCHEN',
  'INVENTORY',
  'MANAGER',
  'OTHER',
])

export const operationalRoles = operationalRoleSchema.options

export const shiftStatusSchema = z.enum(['SCHEDULED', 'COMPLETED', 'CANCELLED'])

export const employeeSchema = z.object({
  id: z.number().int().positive(),
  employeeCode: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string().nullable(),
  phone: z.string().nullable(),
  defaultOperationalRole: operationalRoleSchema,
  employmentStartDate: z.string().nullable(),
  active: z.boolean(),
  version: z.number().int().nonnegative(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const availabilitySchema = z.object({
  id: z.number().int().positive(),
  employeeId: z.number().int().positive(),
  startAt: z.string(),
  endAt: z.string(),
  notes: z.string().nullable(),
  version: z.number().int().nonnegative(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const employeeSummarySchema = employeeSchema.pick({
  id: true,
  employeeCode: true,
  firstName: true,
  lastName: true,
  defaultOperationalRole: true,
  active: true,
})

export const shiftSchema = z.object({
  id: z.number().int().positive(),
  employee: employeeSummarySchema,
  operationalRole: operationalRoleSchema,
  startAt: z.string(),
  endAt: z.string(),
  durationMinutes: z.number().int().positive(),
  status: shiftStatusSchema,
  notes: z.string().nullable(),
  completedAt: z.string().nullable(),
  cancelledAt: z.string().nullable(),
  version: z.number().int().nonnegative(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const employeesSchema = z.array(employeeSchema)
export const availabilityListSchema = z.array(availabilitySchema)
export const shiftsSchema = z.array(shiftSchema)

export type OperationalRole = z.infer<typeof operationalRoleSchema>
export type ShiftStatus = z.infer<typeof shiftStatusSchema>
export type Employee = z.infer<typeof employeeSchema>
export type Availability = z.infer<typeof availabilitySchema>
export type Shift = z.infer<typeof shiftSchema>

export type EmployeeValues = {
  employeeCode: string
  firstName: string
  lastName: string
  email?: string
  phone?: string
  defaultOperationalRole: OperationalRole
  employmentStartDate?: string
}

export type AvailabilityValues = {
  startLocal: string
  endLocal: string
  notes?: string
}

export type ShiftValues = {
  employeeId: number
  operationalRole: OperationalRole
  startLocal: string
  endLocal: string
  notes?: string
}
