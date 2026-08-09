import { z } from 'zod'

export const selectionTypeSchema = z.enum(['SINGLE', 'MULTIPLE'])

export const categorySchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  description: z.string().nullable(),
  displayOrder: z.number().int().nonnegative(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
})

const assignedGroupSchema = z.object({
  modifierGroupId: z.number().int().positive(),
  name: z.string(),
  selectionType: selectionTypeSchema,
  minimumSelections: z.number().int().nonnegative(),
  maximumSelections: z.number().int().positive(),
  displayOrder: z.number().int().nonnegative(),
  active: z.boolean(),
})

export const menuItemSchema = z.object({
  id: z.number().int().positive(),
  category: z.object({ id: z.number().int().positive(), name: z.string(), active: z.boolean() }),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  basePrice: z.string(),
  displayOrder: z.number().int().nonnegative(),
  active: z.boolean(),
  availableForSale: z.boolean(),
  effectivelyAvailable: z.boolean(),
  modifierGroups: z.array(assignedGroupSchema),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
})

export const modifierOptionSchema = z.object({
  id: z.number().int().positive(),
  modifierGroupId: z.number().int().positive(),
  name: z.string(),
  priceAdjustment: z.string(),
  displayOrder: z.number().int().nonnegative(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
})

export const modifierGroupSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  description: z.string().nullable(),
  selectionType: selectionTypeSchema,
  minimumSelections: z.number().int().nonnegative(),
  maximumSelections: z.number().int().positive(),
  displayOrder: z.number().int().nonnegative(),
  active: z.boolean(),
  assignedItemCount: z.number().int().nonnegative(),
  options: z.array(modifierOptionSchema),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
})

export const categoriesSchema = z.array(categorySchema)
export const menuItemsSchema = z.array(menuItemSchema)
export const modifierGroupsSchema = z.array(modifierGroupSchema)

export type MenuCategory = z.infer<typeof categorySchema>
export type MenuItem = z.infer<typeof menuItemSchema>
export type ModifierGroup = z.infer<typeof modifierGroupSchema>
export type ModifierOption = z.infer<typeof modifierOptionSchema>
export type SelectionType = z.infer<typeof selectionTypeSchema>

export type CategoryFilters = {
  active?: boolean
  name?: string
  sortBy?: 'displayOrder' | 'name'
  direction?: 'ASC' | 'DESC'
}
export type ItemFilters = {
  categoryId?: number
  active?: boolean
  availableForSale?: boolean
  effectivelyAvailable?: boolean
  search?: string
  sortBy?: 'displayOrder' | 'name' | 'code' | 'basePrice'
  direction?: 'ASC' | 'DESC'
}
export type GroupFilters = {
  active?: boolean
  selectionType?: SelectionType
  name?: string
  assignedMenuItemId?: number
  sortBy?: 'displayOrder' | 'name'
  direction?: 'ASC' | 'DESC'
}
