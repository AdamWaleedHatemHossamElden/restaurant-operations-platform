import { z } from 'zod'

export const inventoryUnitSchema = z.enum(['GRAM', 'MILLILITER', 'UNIT'])
export const movementTypeSchema = z.enum([
  'RECEIPT',
  'USAGE',
  'WASTE',
  'ADJUSTMENT_IN',
  'ADJUSTMENT_OUT',
])
export const purchaseOrderStatusSchema = z.enum([
  'DRAFT',
  'ORDERED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'CANCELLED',
])

export const inventoryItemSchema = z.object({
  id: z.number().int().positive(),
  code: z.string(),
  name: z.string(),
  unit: inventoryUnitSchema,
  reorderThreshold: z.number(),
  onHand: z.number(),
  lowStock: z.boolean(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
})
export const inventoryItemsSchema = z.array(inventoryItemSchema)

export const stockMovementSchema = z.object({
  id: z.number().int().positive(),
  inventoryItemId: z.number().int().positive(),
  inventoryCode: z.string(),
  inventoryName: z.string(),
  unit: inventoryUnitSchema,
  movementType: movementTypeSchema,
  quantity: z.number(),
  signedQuantity: z.number(),
  occurredAt: z.string(),
  referenceType: z.string().nullable(),
  referenceId: z.number().int().positive().nullable(),
  reason: z.string().nullable(),
  unitCost: z.number().nullable(),
  totalCost: z.number().nullable(),
})
export const stockMovementsSchema = z.array(stockMovementSchema)

export const ingredientSchema = z.object({
  inventoryItemId: z.number().int().positive(),
  inventoryCode: z.string(),
  inventoryName: z.string(),
  unit: inventoryUnitSchema,
  quantity: z.number().positive(),
  displayOrder: z.number().int().nonnegative(),
})
export const recipeSchema = z.object({
  id: z.number().int().positive(),
  menuItemId: z.number().int().positive(),
  menuItemCode: z.string(),
  menuItemName: z.string(),
  active: z.boolean(),
  version: z.number().int().nonnegative(),
  ingredients: z.array(ingredientSchema),
})
export const recipesSchema = z.array(recipeSchema)
export const modifierIngredientsSchema = z.object({
  modifierOptionId: z.number().int().positive(),
  modifierOptionName: z.string(),
  optionVersion: z.number().int().nonnegative(),
  ingredients: z.array(ingredientSchema),
})

export const supplierItemSchema = z.object({
  id: z.number().int().positive(),
  inventoryItemId: z.number().int().positive(),
  inventoryCode: z.string(),
  inventoryName: z.string(),
  unit: inventoryUnitSchema,
  supplierItemCode: z.string().nullable(),
  unitCost: z.number().nonnegative(),
  active: z.boolean(),
  version: z.number().int().nonnegative(),
})
export const supplierSchema = z.object({
  id: z.number().int().positive(),
  code: z.string(),
  name: z.string(),
  contactName: z.string().nullable(),
  email: z.string().nullable(),
  phone: z.string().nullable(),
  notes: z.string().nullable(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
  inventoryItems: z.array(supplierItemSchema),
})
export const suppliersSchema = z.array(supplierSchema)

export const purchaseOrderLineSchema = z.object({
  id: z.number().int().positive(),
  inventoryItemId: z.number().int().positive(),
  inventoryCode: z.string(),
  inventoryName: z.string(),
  unit: inventoryUnitSchema,
  orderedQuantity: z.number().positive(),
  receivedQuantity: z.number().nonnegative(),
  remainingQuantity: z.number().nonnegative(),
  unitCost: z.number().nonnegative(),
  lineTotal: z.number().nonnegative(),
  displayOrder: z.number().int().nonnegative(),
})
export const purchaseOrderSchema = z.object({
  id: z.number().int().positive(),
  purchaseOrderNumber: z.string(),
  supplierId: z.number().int().positive(),
  supplierCode: z.string(),
  supplierName: z.string(),
  status: purchaseOrderStatusSchema,
  notes: z.string().nullable(),
  subtotal: z.number().nonnegative(),
  total: z.number().nonnegative(),
  orderedAt: z.string().nullable(),
  receivedAt: z.string().nullable(),
  cancelledAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
  items: z.array(purchaseOrderLineSchema),
})
export const purchaseOrdersSchema = z.array(purchaseOrderSchema)

export type InventoryUnit = z.infer<typeof inventoryUnitSchema>
export type StockMovementType = z.infer<typeof movementTypeSchema>
export type PurchaseOrderStatus = z.infer<typeof purchaseOrderStatusSchema>
export type InventoryItem = z.infer<typeof inventoryItemSchema>
export type StockMovement = z.infer<typeof stockMovementSchema>
export type Ingredient = z.infer<typeof ingredientSchema>
export type Recipe = z.infer<typeof recipeSchema>
export type ModifierIngredients = z.infer<typeof modifierIngredientsSchema>
export type Supplier = z.infer<typeof supplierSchema>
export type SupplierItem = z.infer<typeof supplierItemSchema>
export type PurchaseOrder = z.infer<typeof purchaseOrderSchema>
export type PurchaseOrderLine = z.infer<typeof purchaseOrderLineSchema>

export type InventoryFilters = {
  active?: boolean
  lowStock?: boolean
  unit?: InventoryUnit
  search?: string
  sortBy?: 'name' | 'code' | 'onHand' | 'reorderThreshold' | 'updatedAt'
  direction?: 'ASC' | 'DESC'
}
