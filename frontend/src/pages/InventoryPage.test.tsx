import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as inventoryApi from '../features/inventory/inventoryApi'
import type {
  InventoryItem,
  PurchaseOrder,
  Recipe,
  StockMovement,
  Supplier,
} from '../features/inventory/inventoryTypes'
import * as menuApi from '../features/menu/menuApi'
import { InventoryPage } from './InventoryPage'

vi.mock('../features/inventory/inventoryApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/inventory/inventoryApi')>()
  return {
    ...original,
    listInventoryItems: vi.fn(),
    listMovements: vi.fn(),
    listRecipes: vi.fn(),
    listSuppliers: vi.fn(),
    listPurchaseOrders: vi.fn(),
    getModifierIngredients: vi.fn(),
    saveInventoryItem: vi.fn(),
    toggleInventoryItem: vi.fn(),
    recordMovement: vi.fn(),
    setRecipeState: vi.fn(),
    saveRecipeIngredients: vi.fn(),
    saveModifierIngredients: vi.fn(),
    saveSupplier: vi.fn(),
    toggleSupplier: vi.fn(),
    saveSupplierItem: vi.fn(),
    createPurchaseOrder: vi.fn(),
    addPurchaseOrderLine: vi.fn(),
    updatePurchaseOrderLine: vi.fn(),
    removePurchaseOrderLine: vi.fn(),
    transitionPurchaseOrder: vi.fn(),
    receivePurchaseOrder: vi.fn(),
  }
})

vi.mock('../features/menu/menuApi', () => ({
  listItems: vi.fn(),
  listGroups: vi.fn(),
}))

const timestamp = '2026-08-11T00:00:00Z'
const beef: InventoryItem = {
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
  version: 0,
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
  reason: 'Trim waste',
  unitCost: null,
  totalCost: null,
}
const recipe: Recipe = {
  id: 3,
  menuItemId: 4,
  menuItemCode: 'BURGER',
  menuItemName: 'Burger',
  active: true,
  version: 0,
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
const supplier: Supplier = {
  id: 5,
  code: 'SUP-1',
  name: 'Primary Foods',
  contactName: 'Operations',
  email: null,
  phone: null,
  notes: null,
  active: true,
  createdAt: timestamp,
  updatedAt: timestamp,
  version: 0,
  inventoryItems: [],
}
const purchaseOrder: PurchaseOrder = {
  id: 6,
  purchaseOrderNumber: 'PO-20300101-ABC123',
  supplierId: 5,
  supplierCode: 'SUP-1',
  supplierName: 'Primary Foods',
  status: 'PARTIALLY_RECEIVED',
  notes: 'Weekly restock',
  subtotal: 20,
  total: 20,
  orderedAt: timestamp,
  receivedAt: null,
  cancelledAt: null,
  createdAt: timestamp,
  updatedAt: timestamp,
  version: 2,
  items: [
    {
      id: 7,
      inventoryItemId: 1,
      inventoryCode: 'BEEF',
      inventoryName: 'Beef',
      unit: 'GRAM',
      orderedQuantity: 1000,
      receivedQuantity: 400,
      remainingQuantity: 600,
      unitCost: 0.02,
      lineTotal: 20,
      displayOrder: 0,
    },
  ],
}

function renderPage() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <InventoryPage />
    </QueryClientProvider>,
  )
}

describe('inventory page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(inventoryApi.listInventoryItems).mockResolvedValue([beef])
    vi.mocked(inventoryApi.listMovements).mockResolvedValue([movement])
    vi.mocked(inventoryApi.listRecipes).mockResolvedValue([recipe])
    vi.mocked(inventoryApi.listSuppliers).mockResolvedValue([supplier])
    vi.mocked(inventoryApi.listPurchaseOrders).mockResolvedValue([purchaseOrder])
    vi.mocked(menuApi.listItems).mockResolvedValue([
      {
        id: 4,
        category: { id: 8, name: 'Mains', active: true },
        code: 'BURGER',
        name: 'Burger',
        description: null,
        basePrice: '10.00',
        displayOrder: 0,
        active: true,
        availableForSale: true,
        effectivelyAvailable: true,
        modifierGroups: [],
        createdAt: timestamp,
        updatedAt: timestamp,
        version: 0,
      },
    ])
    vi.mocked(menuApi.listGroups).mockResolvedValue([])
  })

  it('makes negative and low stock explicit and renders immutable local-time history', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Beef' })).toBeInTheDocument()
    expect(screen.getByText('Negative stock · reconciliation required')).toBeInTheDocument()
    expect(screen.getByText('-50.000')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }))
    const editor = screen.getByRole('dialog')
    expect(within(editor).getByLabelText('Canonical unit')).toBeDisabled()
    expect(within(editor).getByText('Canonical units cannot change after creation.')).toBeVisible()
    await userEvent.click(within(editor).getByRole('button', { name: 'Close dialog' }))

    await userEvent.click(screen.getByRole('button', { name: 'History' }))
    const history = await screen.findByRole('dialog')
    expect(within(history).getByText('WASTE')).toBeInTheDocument()
    expect(within(history).getByText(/Trim waste/)).toBeInTheDocument()
    expect(within(history).queryByRole('button', { name: /edit|delete/i })).not.toBeInTheDocument()
  })

  it('records a manual waste movement without client-controlled actor or timestamp', async () => {
    vi.mocked(inventoryApi.recordMovement).mockResolvedValue(movement)
    renderPage()
    await screen.findByRole('heading', { name: 'Beef' })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Movement' }))
    const dialog = screen.getByRole('dialog')
    await user.selectOptions(within(dialog).getByLabelText('Movement type'), 'WASTE')
    await user.type(within(dialog).getByLabelText('Quantity (g)'), '2.5')
    await user.type(within(dialog).getByLabelText('Reason (optional)'), 'Trim')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(inventoryApi.recordMovement).toHaveBeenCalledWith({
        inventoryItemId: 1,
        movementType: 'WASTE',
        quantity: 2.5,
        reason: 'Trim',
      }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Immutable stock movement recorded.',
    )
  })

  it('handles a rejected dialog mutation without an unhandled promise rejection', async () => {
    vi.mocked(inventoryApi.saveInventoryItem).mockRejectedValueOnce(new Error('stale update'))
    renderPage()
    await screen.findByRole('heading', { name: 'Beef' })

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'The inventory request could not be completed. Please try again.',
    )
  })

  it('supports recipe search and supplier search and active filtering', async () => {
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Beef' })
    await user.click(screen.getByRole('button', { name: 'Recipes' }))
    expect(await screen.findByRole('heading', { name: 'Burger' })).toBeInTheDocument()
    expect(screen.getByText(/Beef — 150/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Search menu items'), 'missing')
    expect(screen.queryByRole('heading', { name: 'Burger' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Suppliers' }))
    expect(await screen.findByRole('heading', { name: 'Primary Foods' })).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Active'), 'INACTIVE')
    expect(screen.getByRole('heading', { name: 'No suppliers match.' })).toBeInTheDocument()
  })

  it('shows receiving progress and sends only the remaining receipt quantity', async () => {
    vi.mocked(inventoryApi.receivePurchaseOrder).mockResolvedValue({
      ...purchaseOrder,
      status: 'RECEIVED',
      version: 3,
      items: [{ ...purchaseOrder.items[0], receivedQuantity: 1000, remainingQuantity: 0 }],
    })
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Beef' })
    await user.click(screen.getByRole('button', { name: 'Purchasing' }))
    await user.click(await screen.findByRole('button', { name: /PO-20300101-ABC123/ }))
    expect(screen.getByText('0 / 1 lines fully received')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Receive' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('600')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    await waitFor(() =>
      expect(inventoryApi.receivePurchaseOrder).toHaveBeenCalledWith(purchaseOrder, 7, 600),
    )
  })
})
