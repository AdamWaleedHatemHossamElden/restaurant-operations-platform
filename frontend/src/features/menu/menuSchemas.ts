import { z } from 'zod'

const decimal = z
  .string()
  .trim()
  .regex(/^\d{1,10}(\.\d{1,2})?$/, 'Use a non-negative amount with up to two decimals')

export const categoryFormSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(120),
  description: z.string().trim().max(1000),
  displayOrder: z.number().int().nonnegative(),
})

export const itemFormSchema = z.object({
  categoryId: z.number().int().positive('Choose a category'),
  code: z
    .string()
    .trim()
    .min(1, 'Code is required')
    .max(40)
    .regex(/^[A-Za-z0-9 _-]+$/, 'Use letters, numbers, spaces, hyphens, or underscores'),
  name: z.string().trim().min(1, 'Name is required').max(160),
  description: z.string().trim().max(2000),
  basePrice: decimal,
  displayOrder: z.number().int().nonnegative(),
})

export const groupFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(120),
    description: z.string().trim().max(1000),
    selectionType: z.enum(['SINGLE', 'MULTIPLE']),
    minimumSelections: z.number().int().min(0).max(20),
    maximumSelections: z.number().int().min(1).max(20),
    displayOrder: z.number().int().nonnegative(),
  })
  .superRefine((value, context) => {
    if (value.minimumSelections > value.maximumSelections) {
      context.addIssue({
        code: 'custom',
        path: ['minimumSelections'],
        message: 'Minimum cannot exceed maximum',
      })
    }
    if (value.selectionType === 'SINGLE' && value.maximumSelections !== 1) {
      context.addIssue({
        code: 'custom',
        path: ['maximumSelections'],
        message: 'Single selection requires a maximum of 1',
      })
    }
  })

export const optionFormSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(120),
  priceAdjustment: decimal,
  displayOrder: z.number().int().nonnegative(),
})

export type CategoryFormValues = z.infer<typeof categoryFormSchema>
export type ItemFormValues = z.infer<typeof itemFormSchema>
export type GroupFormValues = z.infer<typeof groupFormSchema>
export type OptionFormValues = z.infer<typeof optionFormSchema>
