import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { apiClient, setApiAccessToken } from '../../lib/apiClient'
import {
  addPurchaseOrderLine,
  createPurchaseOrder,
  getModifierIngredients,
  listInventoryItems,
  listMovements,
  listPurchaseOrders,
  listRecipes,
  listSuppliers,
  receivePurchaseOrder,
  recordMovement,
  removePurchaseOrderLine,
  saveInventoryItem,
  saveModifierIngredients,
  saveRecipeIngredients,
  saveSupplier,
  saveSupplierItem,
  setRecipeState,
  toggleInventoryItem,
  toggleSupplier,
  transitionPurchaseOrder,
  updatePurchaseOrder,
  updatePurchaseOrderLine,
} from './inventoryApi'
import type {
  InventoryItem,
  ModifierIngredients,
  PurchaseOrder,
  Recipe,
  StockMovement,
  Supplier,
} from './inventoryTypes'

const originalAdapter = apiClient.defaults.adapter
const timestamp = '2026-08-11T00:00:00Z'
const item: InventoryItem = {
  id: 1,
  code: 'BEEF',
  name: 'Beef',
  unit: 'GRAM',
  reorderThreshold: 1000,
  onHand: -50,
  lowStock: true,
  active: true,
  createdAt: timestamp,
  updatedAt: timestamp,
  version: 2,
}
const movement: StockMovement = {
  id: 2,
  inventoryItemId: 1,
  inventoryCode: 'BEEF',
  inventoryName: 'Beef',
  unit: 'GRAM',
  movementType: 'WASTE',
  quantity: 50,
  signedQuantity: -50,
  occurredAt: timestamp,
  referenceType: null,
  referenceId: null,
  reason: 'Trim',
  unitCost: null,
  totalCost: null,
}
const recipe: Recipe = {
  id: 3,
  menuItemId: 4,
  menuItemCode: 'BURGER',
  menuItemName: 'Burger',
  active: true,
  version: 1,
  ingredients: [
    {
      inventoryItemId: 1,
      inventoryCode: 'BEEF',
      inventoryName: 'Beef',
      unit: 'GRAM',
      quantity: 150,
      displayOrder: 0,
    },
  ],
}
const modifier: ModifierIngredients = {
  modifierOptionId: 5,
  modifierOptionName: 'Extra beef',
  optionVersion: 2,
  ingredients: recipe.ingredients,
}
const supplier: Supplier = {
  id: 6,
  code: 'SUP-1',
  name: 'Primary Foods',
  contactName: null,
  email: null,
  phone: null,
  notes: null,
  active: true,
  createdAt: timestamp,
  updatedAt: timestamp,
  version: 1,
  inventoryItems: [
    {
      id: 7,
      inventoryItemId: 1,
      inventoryCode: 'BEEF',
      inventoryName: 'Beef',
      unit: 'GRAM',
      supplierItemCode: 'BF-1',
      unitCost: 0.02,
      active: true,
      version: 0,
    },
  ],
}
const order: PurchaseOrder = {
  id: 8,
  purchaseOrderNumber: 'PO-20300101-ABC123',
  supplierId: 6,
  supplierCode: 'SUP-1',
  supplierName: 'Primary Foods',
  status: 'DRAFT',
  notes: null,
  subtotal: 20,
  total: 20,
  orderedAt: null,
  receivedAt: null,
  cancelledAt: null,
  createdAt: timestamp,
  updatedAt: timestamp,
  version: 3,
  items: [
    {
      id: 9,
      inventoryItemId: 1,
      inventoryCode: 'BEEF',
      inventoryName: 'Beef',
      unit: 'GRAM',
      orderedQuantity: 1000,
      receivedQuantity: 0,
      remainingQuantity: 1000,
      unitCost: 0.02,
      lineTotal: 20,
      displayOrder: 0,
    },
  ],
}

function response(config: InternalAxiosRequestConfig): AxiosResponse {
  const url = config.url ?? ''
  let data: unknown = order
  if (url.startsWith('/inventory/items')) data = config.method === 'get' ? [item] : item
  if (url === '/inventory/movements') data = movement
  if (url.includes('/movements') && config.method === 'get') data = [movement]
  if (url === '/recipes' && config.method === 'get') data = [recipe]
  if (url.includes('/modifier-options/')) data = modifier
  if (url.includes('/recipes/menu-items/')) data = recipe
  if (url === '/suppliers' && config.method === 'get') data = [supplier]
  if (url.includes('/suppliers/') && url.includes('/items/')) data = supplier.inventoryItems[0]
  if (url.startsWith('/suppliers') && data === order) data = supplier
  if (url === '/purchase-orders' && config.method === 'get') data = [order]
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}

describe('inventory API', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
  })

  it('uses the authenticated client and versioned authoritative writes for every Phase 6 area', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setApiAccessToken('memory-only-token')
    apiClient.defaults.adapter = async (config) => {
      requests.push(config)
      return response(config)
    }

    await listInventoryItems({ lowStock: true })
    await saveInventoryItem(
      { code: 'BEEF', name: 'Beef', unit: 'GRAM', reorderThreshold: 1000 },
      item,
    )
    await toggleInventoryItem(item)
    await listMovements(item.id)
    await recordMovement({ inventoryItemId: item.id, movementType: 'WASTE', quantity: 2 })
    await listRecipes()
    await setRecipeState(recipe.menuItemId, false, recipe)
    await saveRecipeIngredients(recipe, recipe.ingredients)
    await getModifierIngredients(modifier.modifierOptionId)
    await saveModifierIngredients(modifier, modifier.ingredients)
    await listSuppliers({ active: true })
    await saveSupplier({ code: supplier.code, name: supplier.name }, supplier)
    await toggleSupplier(supplier)
    await saveSupplierItem(supplier, item, {
      supplierItemCode: 'BF-1',
      unitCost: 0.02,
      active: true,
      version: 0,
    })
    await listPurchaseOrders({ status: 'DRAFT' })
    await createPurchaseOrder({ supplierId: supplier.id })
    await updatePurchaseOrder(order, { supplierId: supplier.id, notes: 'Restock' })
    await addPurchaseOrderLine(order, item.id, 1000)
    await updatePurchaseOrderLine(order, order.items[0].id, 1200)
    await removePurchaseOrderLine(order, order.items[0].id)
    await transitionPurchaseOrder(order, 'ORDERED')
    await receivePurchaseOrder(order, order.items[0].id, 500)

    expect(requests).toHaveLength(22)
    expect(
      requests.every(
        (request) => request.headers.get('Authorization') === 'Bearer memory-only-token',
      ),
    ).toBe(true)
    expect(JSON.parse(requests[1].data as string)).toMatchObject({ version: 2 })
    expect(JSON.parse(requests[7].data as string)).toMatchObject({ version: 1 })
    expect(JSON.parse(requests[18].data as string)).toMatchObject({ version: 3 })
    expect(requests[19].params).toEqual({ version: 3 })
    expect(JSON.parse(requests[20].data as string)).toEqual({
      status: 'ORDERED',
      version: 3,
    })
    expect(JSON.parse(requests[21].data as string)).toEqual({
      purchaseOrderItemId: 9,
      quantity: 500,
      version: 3,
    })
  })
})
