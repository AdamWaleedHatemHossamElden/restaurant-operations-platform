import axios from 'axios'

import { apiClient } from '../../lib/apiClient'
import {
  inventoryItemSchema,
  inventoryItemsSchema,
  modifierIngredientsSchema,
  purchaseOrderSchema,
  purchaseOrdersSchema,
  recipeSchema,
  recipesSchema,
  stockMovementSchema,
  stockMovementsSchema,
  supplierItemSchema,
  supplierSchema,
  suppliersSchema,
  type Ingredient,
  type InventoryFilters,
  type InventoryItem,
  type InventoryUnit,
  type ModifierIngredients,
  type PurchaseOrder,
  type PurchaseOrderStatus,
  type Recipe,
  type StockMovement,
  type StockMovementType,
  type Supplier,
  type SupplierItem,
} from './inventoryTypes'

export const inventoryKeys = {
  all: ['inventory'] as const,
  items: ['inventory', 'items'] as const,
  movements: (id: number) => ['inventory', 'items', id, 'movements'] as const,
  recipes: ['inventory', 'recipes'] as const,
  suppliers: ['inventory', 'suppliers'] as const,
  purchaseOrders: ['inventory', 'purchase-orders'] as const,
}

export type InventoryItemValues = {
  code: string
  name: string
  unit: InventoryUnit
  reorderThreshold: number
}
export async function listInventoryItems(filters: InventoryFilters): Promise<InventoryItem[]> {
  return inventoryItemsSchema.parse(
    (await apiClient.get('/inventory/items', { params: filters })).data,
  )
}
export async function saveInventoryItem(
  values: InventoryItemValues,
  current?: InventoryItem,
): Promise<InventoryItem> {
  const response = current
    ? await apiClient.put(`/inventory/items/${current.id}`, { ...values, version: current.version })
    : await apiClient.post('/inventory/items', values)
  return inventoryItemSchema.parse(response.data)
}
export async function toggleInventoryItem(item: InventoryItem): Promise<InventoryItem> {
  return inventoryItemSchema.parse(
    (
      await apiClient.patch(`/inventory/items/${item.id}/activation`, {
        value: !item.active,
        version: item.version,
      })
    ).data,
  )
}
export async function listMovements(itemId: number): Promise<StockMovement[]> {
  return stockMovementsSchema.parse(
    (await apiClient.get(`/inventory/items/${itemId}/movements`)).data,
  )
}
export async function recordMovement(values: {
  inventoryItemId: number
  movementType: StockMovementType
  quantity: number
  reason?: string
}): Promise<StockMovement> {
  return stockMovementSchema.parse((await apiClient.post('/inventory/movements', values)).data)
}

export async function listRecipes(): Promise<Recipe[]> {
  return recipesSchema.parse((await apiClient.get('/recipes')).data)
}
export async function setRecipeState(
  menuItemId: number,
  active: boolean,
  recipe?: Recipe,
): Promise<Recipe> {
  return recipeSchema.parse(
    (
      await apiClient.put(`/recipes/menu-items/${menuItemId}`, {
        active,
        version: recipe?.version,
      })
    ).data,
  )
}
export async function saveRecipeIngredients(
  recipe: Recipe,
  ingredients: Pick<Ingredient, 'inventoryItemId' | 'quantity' | 'displayOrder'>[],
): Promise<Recipe> {
  return recipeSchema.parse(
    (
      await apiClient.put(`/recipes/menu-items/${recipe.menuItemId}/ingredients`, {
        version: recipe.version,
        ingredients,
      })
    ).data,
  )
}
export async function getModifierIngredients(optionId: number): Promise<ModifierIngredients> {
  return modifierIngredientsSchema.parse(
    (await apiClient.get(`/recipes/modifier-options/${optionId}/ingredients`)).data,
  )
}
export async function saveModifierIngredients(
  current: ModifierIngredients,
  ingredients: Pick<Ingredient, 'inventoryItemId' | 'quantity' | 'displayOrder'>[],
): Promise<ModifierIngredients> {
  return modifierIngredientsSchema.parse(
    (
      await apiClient.put(`/recipes/modifier-options/${current.modifierOptionId}/ingredients`, {
        optionVersion: current.optionVersion,
        ingredients,
      })
    ).data,
  )
}

export type SupplierValues = {
  code: string
  name: string
  contactName?: string
  email?: string
  phone?: string
  notes?: string
}
export async function listSuppliers(filters: {
  active?: boolean
  search?: string
}): Promise<Supplier[]> {
  return suppliersSchema.parse((await apiClient.get('/suppliers', { params: filters })).data)
}
export async function saveSupplier(values: SupplierValues, current?: Supplier): Promise<Supplier> {
  const response = current
    ? await apiClient.put(`/suppliers/${current.id}`, { ...values, version: current.version })
    : await apiClient.post('/suppliers', values)
  return supplierSchema.parse(response.data)
}
export async function toggleSupplier(supplier: Supplier): Promise<Supplier> {
  return supplierSchema.parse(
    (
      await apiClient.patch(`/suppliers/${supplier.id}/activation`, {
        value: !supplier.active,
        version: supplier.version,
      })
    ).data,
  )
}
export async function saveSupplierItem(
  supplier: Supplier,
  item: InventoryItem,
  values: { supplierItemCode?: string; unitCost: number; active: boolean; version?: number },
): Promise<SupplierItem> {
  return supplierItemSchema.parse(
    (
      await apiClient.put(`/suppliers/${supplier.id}/items/${item.id}`, {
        inventoryItemId: item.id,
        ...values,
      })
    ).data,
  )
}

export async function listPurchaseOrders(filters: {
  status?: PurchaseOrderStatus
  supplierId?: number
  search?: string
}): Promise<PurchaseOrder[]> {
  return purchaseOrdersSchema.parse(
    (await apiClient.get('/purchase-orders', { params: filters })).data,
  )
}
export async function createPurchaseOrder(values: {
  supplierId: number
  notes?: string
}): Promise<PurchaseOrder> {
  return purchaseOrderSchema.parse((await apiClient.post('/purchase-orders', values)).data)
}
export async function updatePurchaseOrder(
  order: PurchaseOrder,
  values: { supplierId: number; notes?: string },
): Promise<PurchaseOrder> {
  return purchaseOrderSchema.parse(
    (await apiClient.put(`/purchase-orders/${order.id}`, { ...values, version: order.version }))
      .data,
  )
}
export async function addPurchaseOrderLine(
  order: PurchaseOrder,
  inventoryItemId: number,
  quantity: number,
): Promise<PurchaseOrder> {
  return purchaseOrderSchema.parse(
    (
      await apiClient.post(`/purchase-orders/${order.id}/items`, {
        inventoryItemId,
        quantity,
        version: order.version,
      })
    ).data,
  )
}
export async function updatePurchaseOrderLine(
  order: PurchaseOrder,
  itemId: number,
  quantity: number,
): Promise<PurchaseOrder> {
  return purchaseOrderSchema.parse(
    (
      await apiClient.put(`/purchase-orders/${order.id}/items/${itemId}`, {
        quantity,
        version: order.version,
      })
    ).data,
  )
}
export async function removePurchaseOrderLine(
  order: PurchaseOrder,
  itemId: number,
): Promise<PurchaseOrder> {
  return purchaseOrderSchema.parse(
    (
      await apiClient.delete(`/purchase-orders/${order.id}/items/${itemId}`, {
        params: { version: order.version },
      })
    ).data,
  )
}
export async function transitionPurchaseOrder(
  order: PurchaseOrder,
  status: PurchaseOrderStatus,
): Promise<PurchaseOrder> {
  return purchaseOrderSchema.parse(
    (
      await apiClient.patch(`/purchase-orders/${order.id}/status`, {
        status,
        version: order.version,
      })
    ).data,
  )
}
export async function receivePurchaseOrder(
  order: PurchaseOrder,
  purchaseOrderItemId: number,
  quantity: number,
): Promise<PurchaseOrder> {
  return purchaseOrderSchema.parse(
    (
      await apiClient.post(`/purchase-orders/${order.id}/receipts`, {
        purchaseOrderItemId,
        quantity,
        version: order.version,
      })
    ).data,
  )
}

export function inventoryRequestError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    const data = error.response.data
    const message =
      data && typeof data === 'object' && 'message' in data ? String(data.message) : ''
    if (message.includes('changed by another request'))
      return 'This record changed elsewhere. The latest data is being loaded.'
    return message || 'This inventory operation conflicts with current server state.'
  }
  if (axios.isAxiosError(error) && error.response?.status === 400)
    return 'Check the entered quantities and required fields.'
  return 'The inventory request could not be completed. Please try again.'
}
