import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState, type ReactNode } from 'react'

import { Dialog } from '../components/ui/Dialog'
import {
  IngredientDialog,
  InventoryItemDialog,
  MovementDialog,
  PurchaseLineDialog,
  PurchaseOrderDialog,
  ReceiptDialog,
  SupplierDialog,
  SupplierItemDialog,
} from '../features/inventory/InventoryDialogs'
import {
  addPurchaseOrderLine,
  createPurchaseOrder,
  getModifierIngredients,
  inventoryKeys,
  inventoryRequestError,
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
  updatePurchaseOrderLine,
  type InventoryItemValues,
  type SupplierValues,
} from '../features/inventory/inventoryApi'
import type {
  Ingredient,
  InventoryItem,
  InventoryUnit,
  ModifierIngredients,
  PurchaseOrder,
  PurchaseOrderLine,
  PurchaseOrderStatus,
  Recipe,
  StockMovementType,
  Supplier,
  SupplierItem,
} from '../features/inventory/inventoryTypes'
import { unitLabel } from '../features/inventory/inventoryUnits'
import { listGroups, listItems } from '../features/menu/menuApi'
import { formatEur } from '../features/menu/money'

type Tab = 'stock' | 'recipes' | 'suppliers' | 'purchasing'

export function InventoryPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('stock')
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [active, setActive] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL')
  const [lowOnly, setLowOnly] = useState(false)
  const [unit, setUnit] = useState<'ALL' | InventoryUnit>('ALL')
  const [itemEditor, setItemEditor] = useState<InventoryItem | null | undefined>(undefined)
  const [movementItem, setMovementItem] = useState<InventoryItem>()
  const [historyItem, setHistoryItem] = useState<InventoryItem>()
  const [recipeEditor, setRecipeEditor] = useState<Recipe>()
  const [modifierEditor, setModifierEditor] = useState<ModifierIngredients>()
  const [supplierEditor, setSupplierEditor] = useState<Supplier | null | undefined>(undefined)
  const [supplierItemEditor, setSupplierItemEditor] = useState<{
    supplier: Supplier
    relationship?: SupplierItem
  }>()
  const [purchaseCreator, setPurchaseCreator] = useState(false)
  const [selectedOrder, setSelectedOrder] = useState<PurchaseOrder>()
  const [lineEditor, setLineEditor] = useState<PurchaseOrderLine | null | undefined>(undefined)
  const [receiptLine, setReceiptLine] = useState<PurchaseOrderLine>()

  const itemFilters = useMemo(
    () => ({
      search: search || undefined,
      active: active === 'ALL' ? undefined : active === 'ACTIVE',
      lowStock: lowOnly || undefined,
      unit: unit === 'ALL' ? undefined : unit,
      sortBy: 'name' as const,
      direction: 'ASC' as const,
    }),
    [active, lowOnly, search, unit],
  )
  const itemsQuery = useQuery({
    queryKey: [...inventoryKeys.items, itemFilters],
    queryFn: () => listInventoryItems(itemFilters),
  })
  const allItemsQuery = useQuery({
    queryKey: [...inventoryKeys.items, 'all-configuration-items'],
    queryFn: () => listInventoryItems({ sortBy: 'name', direction: 'ASC' }),
  })
  const recipesQuery = useQuery({ queryKey: inventoryKeys.recipes, queryFn: listRecipes })
  const suppliersQuery = useQuery({
    queryKey: inventoryKeys.suppliers,
    queryFn: () => listSuppliers({}),
  })
  const ordersQuery = useQuery({
    queryKey: inventoryKeys.purchaseOrders,
    queryFn: () => listPurchaseOrders({}),
  })
  const menuItemsQuery = useQuery({
    queryKey: ['menu', 'items', 'inventory'],
    queryFn: () => listItems({}),
  })
  const groupsQuery = useQuery({
    queryKey: ['menu', 'modifier-groups', 'inventory'],
    queryFn: () => listGroups({}),
  })
  const movementQuery = useQuery({
    queryKey: inventoryKeys.movements(historyItem?.id ?? 0),
    queryFn: () => listMovements(historyItem!.id),
    enabled: Boolean(historyItem),
  })

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
    await queryClient.invalidateQueries({ queryKey: ['kitchen'] })
  }
  const fail = async (caught: unknown) => {
    setError(inventoryRequestError(caught))
    await refresh()
  }
  const succeed = async (message: string) => {
    setError(null)
    setNotice(message)
    await refresh()
  }

  const itemSave = useMutation({
    mutationFn: ({ values, current }: { values: InventoryItemValues; current?: InventoryItem }) =>
      saveInventoryItem(values, current),
    onSuccess: async () => {
      setItemEditor(undefined)
      await succeed('Inventory item saved.')
    },
    onError: fail,
  })
  const itemToggle = useMutation({
    mutationFn: toggleInventoryItem,
    onSuccess: async () => succeed('Inventory activation updated.'),
    onError: fail,
  })
  const movementSave = useMutation({
    mutationFn: ({
      item,
      movementType,
      quantity,
      reason,
    }: {
      item: InventoryItem
      movementType: StockMovementType
      quantity: number
      reason?: string
    }) => recordMovement({ inventoryItemId: item.id, movementType, quantity, reason }),
    onSuccess: async () => {
      setMovementItem(undefined)
      await succeed('Immutable stock movement recorded.')
    },
    onError: fail,
  })
  const recipeState = useMutation({
    mutationFn: ({
      menuItemId,
      active,
      recipe,
    }: {
      menuItemId: number
      active: boolean
      recipe?: Recipe
    }) => setRecipeState(menuItemId, active, recipe),
    onSuccess: async () => succeed('Recipe state updated.'),
    onError: fail,
  })
  const recipeSave = useMutation({
    mutationFn: ({
      recipe,
      ingredients,
    }: {
      recipe: Recipe
      ingredients: Pick<Ingredient, 'inventoryItemId' | 'quantity' | 'displayOrder'>[]
    }) => saveRecipeIngredients(recipe, ingredients),
    onSuccess: async () => {
      setRecipeEditor(undefined)
      await succeed('Recipe ingredients saved.')
    },
    onError: fail,
  })
  const modifierSave = useMutation({
    mutationFn: ({
      current,
      ingredients,
    }: {
      current: ModifierIngredients
      ingredients: Pick<Ingredient, 'inventoryItemId' | 'quantity' | 'displayOrder'>[]
    }) => saveModifierIngredients(current, ingredients),
    onSuccess: async () => {
      setModifierEditor(undefined)
      await succeed('Modifier ingredients saved.')
    },
    onError: fail,
  })
  const supplierSave = useMutation({
    mutationFn: ({ values, current }: { values: SupplierValues; current?: Supplier }) =>
      saveSupplier(values, current),
    onSuccess: async () => {
      setSupplierEditor(undefined)
      await succeed('Supplier saved.')
    },
    onError: fail,
  })
  const supplierToggle = useMutation({
    mutationFn: toggleSupplier,
    onSuccess: async () => succeed('Supplier activation updated.'),
    onError: fail,
  })
  const supplierItemSave = useMutation({
    mutationFn: ({
      supplier,
      item,
      values,
    }: {
      supplier: Supplier
      item: InventoryItem
      values: { supplierItemCode?: string; unitCost: number; active: boolean; version?: number }
    }) => saveSupplierItem(supplier, item, values),
    onSuccess: async () => {
      setSupplierItemEditor(undefined)
      await succeed('Supplier item pricing saved.')
    },
    onError: fail,
  })
  const poCreate = useMutation({
    mutationFn: ({ supplierId, notes }: { supplierId: number; notes?: string }) =>
      createPurchaseOrder({ supplierId, notes }),
    onSuccess: async (order) => {
      setPurchaseCreator(false)
      setSelectedOrder(order)
      await succeed('Draft purchase order created.')
    },
    onError: fail,
  })
  const poMutation = useMutation({
    mutationFn: async (
      operation:
        | { kind: 'line'; itemId: number; quantity: number; line?: PurchaseOrderLine }
        | { kind: 'remove'; line: PurchaseOrderLine }
        | { kind: 'status'; status: PurchaseOrderStatus }
        | { kind: 'receive'; line: PurchaseOrderLine; quantity: number },
    ) => {
      if (!selectedOrder) throw new Error('No purchase order selected')
      if (operation.kind === 'line')
        return operation.line
          ? updatePurchaseOrderLine(selectedOrder, operation.line.id, operation.quantity)
          : addPurchaseOrderLine(selectedOrder, operation.itemId, operation.quantity)
      if (operation.kind === 'remove')
        return removePurchaseOrderLine(selectedOrder, operation.line.id)
      if (operation.kind === 'receive')
        return receivePurchaseOrder(selectedOrder, operation.line.id, operation.quantity)
      return transitionPurchaseOrder(selectedOrder, operation.status)
    },
    onSuccess: async (order) => {
      setSelectedOrder(order)
      setLineEditor(undefined)
      setReceiptLine(undefined)
      await succeed('Purchase order updated.')
    },
    onError: fail,
  })

  const latestSelected =
    ordersQuery.data?.find((order) => order.id === selectedOrder?.id) ?? selectedOrder
  const allItems = allItemsQuery.data ?? []
  const lowCount = allItems.filter((item) => item.lowStock).length

  return (
    <div className="page inventory-page">
      <section className="tables-hero inventory-hero" aria-labelledby="inventory-title">
        <div>
          <p className="eyebrow">Stock & purchasing</p>
          <h1 id="inventory-title">Inventory</h1>
          <p>Ledger-backed stock, recipes, suppliers, and purchasing.</p>
        </div>
        <div className="inventory-summary">
          <strong>{lowCount}</strong>
          <span>low-stock alerts</span>
        </div>
      </section>
      <nav className="menu-tabs" aria-label="Inventory sections">
        {(['stock', 'recipes', 'suppliers', 'purchasing'] as const).map((value) => (
          <button
            className={tab === value ? 'active' : ''}
            type="button"
            aria-current={tab === value ? 'page' : undefined}
            onClick={() => setTab(value)}
            key={value}
          >
            {value[0].toUpperCase() + value.slice(1)}
          </button>
        ))}
      </nav>
      {notice && (
        <div className="notice" role="status">
          <span>{notice}</span>
          <button type="button" aria-label="Dismiss notification" onClick={() => setNotice(null)}>
            &times;
          </button>
        </div>
      )}
      {tab === 'stock' && (
        <StockTab
          items={itemsQuery.data ?? []}
          query={itemsQuery}
          filters={{ search, active, lowOnly, unit }}
          setFilters={{ setSearch, setActive, setLowOnly, setUnit }}
          onCreate={() => {
            setError(null)
            setItemEditor(null)
          }}
          onEdit={(item) => {
            setError(null)
            setItemEditor(item)
          }}
          onToggle={(item) => itemToggle.mutate(item)}
          onMovement={(item) => {
            setError(null)
            setMovementItem(item)
          }}
          onHistory={setHistoryItem}
        />
      )}
      {tab === 'recipes' && (
        <RecipesTab
          recipes={recipesQuery.data ?? []}
          menuItems={menuItemsQuery.data ?? []}
          groups={groupsQuery.data ?? []}
          items={allItems}
          isLoading={
            recipesQuery.isPending ||
            menuItemsQuery.isPending ||
            groupsQuery.isPending ||
            allItemsQuery.isPending
          }
          onState={(menuItemId, active, recipe) =>
            recipeState.mutate({ menuItemId, active, recipe })
          }
          onIngredients={(recipe) => {
            setError(null)
            setRecipeEditor(recipe)
          }}
          onModifier={async (optionId) => {
            setError(null)
            try {
              setModifierEditor(await getModifierIngredients(optionId))
            } catch (caught) {
              await fail(caught)
            }
          }}
        />
      )}
      {tab === 'suppliers' && (
        <SuppliersTab
          suppliers={suppliersQuery.data ?? []}
          isLoading={suppliersQuery.isPending}
          onCreate={() => {
            setError(null)
            setSupplierEditor(null)
          }}
          onEdit={(supplier) => {
            setError(null)
            setSupplierEditor(supplier)
          }}
          onToggle={(supplier) => supplierToggle.mutate(supplier)}
          onItem={(supplier, relationship) => {
            setError(null)
            setSupplierItemEditor({ supplier, relationship })
          }}
        />
      )}
      {tab === 'purchasing' && (
        <PurchasingTab
          orders={ordersQuery.data ?? []}
          selected={latestSelected}
          isLoading={ordersQuery.isPending}
          onCreate={() => {
            setError(null)
            setPurchaseCreator(true)
          }}
          onSelect={setSelectedOrder}
          onLine={(line) => {
            setError(null)
            setLineEditor(line)
          }}
          onRemove={(line) => poMutation.mutate({ kind: 'remove', line })}
          onStatus={(status) => poMutation.mutate({ kind: 'status', status })}
          onReceive={(line) => {
            setError(null)
            setReceiptLine(line)
          }}
        />
      )}

      {itemEditor !== undefined && (
        <InventoryItemDialog
          item={itemEditor}
          error={error}
          onClose={() => setItemEditor(undefined)}
          onSave={async (values) => {
            await itemSave.mutateAsync({ values, current: itemEditor ?? undefined })
          }}
        />
      )}
      {movementItem && (
        <MovementDialog
          item={movementItem}
          error={error}
          onClose={() => setMovementItem(undefined)}
          onSave={async (values) => {
            await movementSave.mutateAsync({ item: movementItem, ...values })
          }}
        />
      )}
      {historyItem && (
        <HistoryDialog
          item={historyItem}
          query={movementQuery}
          onClose={() => setHistoryItem(undefined)}
        />
      )}
      {recipeEditor && (
        <IngredientDialog
          title={`Recipe · ${recipeEditor.menuItemName}`}
          ingredients={recipeEditor.ingredients}
          items={allItems}
          error={error}
          onClose={() => setRecipeEditor(undefined)}
          onSave={async (ingredients) => {
            await recipeSave.mutateAsync({ recipe: recipeEditor, ingredients })
          }}
        />
      )}
      {modifierEditor && (
        <IngredientDialog
          title={`Modifier · ${modifierEditor.modifierOptionName}`}
          ingredients={modifierEditor.ingredients}
          items={allItems}
          error={error}
          onClose={() => setModifierEditor(undefined)}
          onSave={async (ingredients) => {
            await modifierSave.mutateAsync({ current: modifierEditor, ingredients })
          }}
        />
      )}
      {supplierEditor !== undefined && (
        <SupplierDialog
          supplier={supplierEditor}
          error={error}
          onClose={() => setSupplierEditor(undefined)}
          onSave={async (values) => {
            await supplierSave.mutateAsync({ values, current: supplierEditor ?? undefined })
          }}
        />
      )}
      {supplierItemEditor && (
        <SupplierItemDialog
          supplier={supplierItemEditor.supplier}
          relationship={supplierItemEditor.relationship}
          items={allItems}
          error={error}
          onClose={() => setSupplierItemEditor(undefined)}
          onSave={async (item, values) => {
            await supplierItemSave.mutateAsync({
              supplier: supplierItemEditor.supplier,
              item,
              values,
            })
          }}
        />
      )}
      {purchaseCreator && (
        <PurchaseOrderDialog
          suppliers={suppliersQuery.data ?? []}
          error={error}
          onClose={() => setPurchaseCreator(false)}
          onSave={async (supplierId, notes) => {
            await poCreate.mutateAsync({ supplierId, notes })
          }}
        />
      )}
      {lineEditor !== undefined && latestSelected && (
        <PurchaseLineDialog
          items={allItems.filter(
            (item) =>
              latestSelected.items.every((line) => line.inventoryItemId !== item.id) ||
              item.id === lineEditor?.inventoryItemId,
          )}
          line={lineEditor ?? undefined}
          error={error}
          onClose={() => setLineEditor(undefined)}
          onSave={async (itemId, quantity) => {
            await poMutation.mutateAsync({
              kind: 'line',
              itemId,
              quantity,
              line: lineEditor ?? undefined,
            })
          }}
        />
      )}
      {receiptLine && (
        <ReceiptDialog
          line={receiptLine}
          error={error}
          onClose={() => setReceiptLine(undefined)}
          onSave={async (quantity) => {
            await poMutation.mutateAsync({ kind: 'receive', line: receiptLine, quantity })
          }}
        />
      )}
    </div>
  )
}

type InventoryQuery = { isPending: boolean; isError: boolean; refetch: () => unknown }
function StockTab({
  items,
  query,
  filters,
  setFilters,
  onCreate,
  onEdit,
  onToggle,
  onMovement,
  onHistory,
}: {
  items: InventoryItem[]
  query: InventoryQuery
  filters: { search: string; active: string; lowOnly: boolean; unit: string }
  setFilters: {
    setSearch: (value: string) => void
    setActive: (value: 'ALL' | 'ACTIVE' | 'INACTIVE') => void
    setLowOnly: (value: boolean) => void
    setUnit: (value: 'ALL' | InventoryUnit) => void
  }
  onCreate: () => void
  onEdit: (item: InventoryItem) => void
  onToggle: (item: InventoryItem) => void
  onMovement: (item: InventoryItem) => void
  onHistory: (item: InventoryItem) => void
}) {
  return (
    <section>
      <div className="workspace-toolbar">
        <div className="inventory-filters">
          <label>
            Search
            <input
              type="search"
              value={filters.search}
              onChange={(event) => setFilters.setSearch(event.target.value)}
            />
          </label>
          <label>
            Active
            <select
              value={filters.active}
              onChange={(event) =>
                setFilters.setActive(event.target.value as 'ALL' | 'ACTIVE' | 'INACTIVE')
              }
            >
              <option value="ALL">All</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </label>
          <label>
            Unit
            <select
              value={filters.unit}
              onChange={(event) => setFilters.setUnit(event.target.value as 'ALL' | InventoryUnit)}
            >
              <option value="ALL">All</option>
              <option value="GRAM">Gram</option>
              <option value="MILLILITER">Milliliter</option>
              <option value="UNIT">Unit</option>
            </select>
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={filters.lowOnly}
              onChange={(event) => setFilters.setLowOnly(event.target.checked)}
            />
            Low stock only
          </label>
        </div>
        <button
          className="button button--primary inventory-create-button"
          type="button"
          onClick={onCreate}
        >
          Create item
        </button>
      </div>
      <QueryState query={query} empty={!items.length} noun="inventory items">
        {' '}
        <div className="inventory-grid">
          {items.map((item) => (
            <article
              className={`inventory-card${item.lowStock ? ' inventory-card--low' : ''}`}
              key={item.id}
            >
              <header>
                <div>
                  <p className="menu-code">
                    {item.code} · {unitLabel(item.unit)}
                  </p>
                  <h2>{item.name}</h2>
                </div>
                <span
                  className={`status-pill ${item.active ? 'status-pill--available' : 'status-pill--inactive'}`}
                >
                  {item.active ? 'Active' : 'Inactive'}
                </span>
              </header>
              <div className="stock-balance">
                <strong>{item.onHand.toFixed(3)}</strong>
                <span>{unitLabel(item.unit)} on hand</span>
              </div>
              <p className={item.lowStock ? 'stock-alert' : 'stock-ok'}>
                {item.onHand < 0
                  ? 'Negative stock · reconciliation required'
                  : item.lowStock
                    ? 'Low stock'
                    : 'Stock above threshold'}
              </p>
              <p>
                Reorder at ≤ {item.reorderThreshold.toFixed(3)} {unitLabel(item.unit)}
              </p>
              <div className="menu-card__actions menu-card__actions--wrap">
                <button
                  className="button button--secondary button--compact"
                  type="button"
                  onClick={() => onEdit(item)}
                >
                  Edit
                </button>
                <button
                  className="button button--secondary button--compact"
                  type="button"
                  onClick={() => onMovement(item)}
                >
                  Movement
                </button>
                <button
                  className="button button--ghost button--compact"
                  type="button"
                  onClick={() => onHistory(item)}
                >
                  History
                </button>
                <button
                  className={`button button--compact ${item.active ? 'button--danger-muted' : 'button--secondary'}`}
                  type="button"
                  onClick={() => onToggle(item)}
                >
                  {item.active ? 'Deactivate' : 'Reactivate'}
                </button>
              </div>
            </article>
          ))}
        </div>
      </QueryState>
    </section>
  )
}

function RecipesTab({
  recipes,
  menuItems,
  groups,
  items,
  isLoading,
  onState,
  onIngredients,
  onModifier,
}: {
  recipes: Recipe[]
  menuItems: { id: number; code: string; name: string }[]
  groups: { name: string; options: { id: number; name: string; active: boolean }[] }[]
  items: InventoryItem[]
  isLoading: boolean
  onState: (menuItemId: number, active: boolean, recipe?: Recipe) => void
  onIngredients: (recipe: Recipe) => void
  onModifier: (optionId: number) => void
}) {
  const [search, setSearch] = useState('')
  const term = search.trim().toLocaleLowerCase()
  const visibleMenuItems = menuItems.filter(
    (item) =>
      !term ||
      item.code.toLocaleLowerCase().includes(term) ||
      item.name.toLocaleLowerCase().includes(term),
  )
  if (isLoading) return <div className="table-state">Loading recipes…</div>
  return (
    <section className="inventory-stack">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Menu consumption</p>
          <h2>Base recipes</h2>
        </div>
        <span>{items.filter((item) => item.active).length} ingredients available</span>
      </div>
      <div className="inventory-filters">
        <label>
          Search menu items
          <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} />
        </label>
      </div>
      <div className="inventory-grid">
        {visibleMenuItems.map((menuItem) => {
          const recipe = recipes.find((value) => value.menuItemId === menuItem.id)
          return (
            <article className="inventory-card" key={menuItem.id}>
              <header>
                <div>
                  <p className="menu-code">{menuItem.code}</p>
                  <h3>{menuItem.name}</h3>
                </div>
                <span className="status-pill">
                  {recipe ? (recipe.active ? 'Active recipe' : 'Inactive recipe') : 'No recipe'}
                </span>
              </header>
              {recipe?.ingredients.length ? (
                <ul className="ingredient-list">
                  {recipe.ingredients.map((ingredient) => (
                    <li key={ingredient.inventoryItemId}>
                      {ingredient.inventoryName} — {ingredient.quantity}{' '}
                      {unitLabel(ingredient.unit)}
                    </li>
                  ))}
                </ul>
              ) : (
                <p>No base ingredients configured. Preparation remains allowed.</p>
              )}
              <div className="menu-card__actions">
                {recipe && (
                  <button
                    className="button button--secondary button--compact"
                    type="button"
                    onClick={() => onIngredients(recipe)}
                  >
                    Ingredients
                  </button>
                )}
                <button
                  className={`button button--compact ${recipe?.active ? 'button--danger-muted' : 'button--secondary'}`}
                  type="button"
                  onClick={() => onState(menuItem.id, !recipe?.active, recipe)}
                >
                  {recipe?.active ? 'Deactivate' : recipe ? 'Activate' : 'Create recipe'}
                </button>
              </div>
            </article>
          )
        })}
      </div>
      <div className="section-heading">
        <div>
          <p className="eyebrow">Selected options</p>
          <h2>Modifier ingredients</h2>
        </div>
      </div>
      <div className="inventory-grid">
        {groups.flatMap((group) =>
          group.options
            .filter((option) => option.active)
            .map((option) => (
              <article className="inventory-card" key={option.id}>
                <p className="eyebrow">{group.name}</p>
                <h3>{option.name}</h3>
                <p>Configure optional ingredient consumption for this selected modifier.</p>
                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => onModifier(option.id)}
                >
                  Configure ingredients
                </button>
              </article>
            )),
        )}
      </div>
    </section>
  )
}

function SuppliersTab({
  suppliers,
  isLoading,
  onCreate,
  onEdit,
  onToggle,
  onItem,
}: {
  suppliers: Supplier[]
  isLoading: boolean
  onCreate: () => void
  onEdit: (supplier: Supplier) => void
  onToggle: (supplier: Supplier) => void
  onItem: (supplier: Supplier, relationship?: SupplierItem) => void
}) {
  const [search, setSearch] = useState('')
  const [active, setActive] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL')
  const term = search.trim().toLocaleLowerCase()
  const visibleSuppliers = suppliers.filter(
    (supplier) =>
      (active === 'ALL' || supplier.active === (active === 'ACTIVE')) &&
      (!term ||
        supplier.code.toLocaleLowerCase().includes(term) ||
        supplier.name.toLocaleLowerCase().includes(term)),
  )
  if (isLoading) return <div className="table-state">Loading suppliers…</div>
  return (
    <section>
      <div className="workspace-toolbar">
        <div className="inventory-filters">
          <label>
            Search
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <label>
            Active
            <select
              value={active}
              onChange={(event) => setActive(event.target.value as 'ALL' | 'ACTIVE' | 'INACTIVE')}
            >
              <option value="ALL">All</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </label>
        </div>
        <button className="button button--primary" type="button" onClick={onCreate}>
          Create supplier
        </button>
      </div>
      {!visibleSuppliers.length ? (
        <div className="table-state">
          <h2>No suppliers match.</h2>
        </div>
      ) : (
        <div className="inventory-grid">
          {visibleSuppliers.map((supplier) => (
            <article className="inventory-card supplier-card" key={supplier.id}>
              <header>
                <div>
                  <p className="menu-code">{supplier.code}</p>
                  <h3>{supplier.name}</h3>
                </div>
                <span
                  className={`status-pill ${supplier.active ? 'status-pill--available' : 'status-pill--inactive'}`}
                >
                  {supplier.active ? 'Active' : 'Inactive'}
                </span>
              </header>
              <p>
                {supplier.contactName || 'No contact'}
                {supplier.email ? ` · ${supplier.email}` : ''}
              </p>
              <div className="supplier-prices">
                <h4>Inventory pricing</h4>
                {supplier.inventoryItems.length ? (
                  supplier.inventoryItems.map((relationship) => (
                    <button
                      type="button"
                      className="supplier-price-row"
                      onClick={() => onItem(supplier, relationship)}
                      key={relationship.id}
                    >
                      <span>
                        {relationship.inventoryName} ·{' '}
                        {relationship.supplierItemCode || 'No supplier code'}
                      </span>
                      <strong>
                        €{relationship.unitCost.toFixed(4)} / {unitLabel(relationship.unit)}
                      </strong>
                    </button>
                  ))
                ) : (
                  <p>No linked inventory items.</p>
                )}
              </div>
              <div className="menu-card__actions menu-card__actions--wrap">
                <button
                  className="button button--secondary button--compact"
                  type="button"
                  onClick={() => onEdit(supplier)}
                >
                  Edit
                </button>
                <button
                  className="button button--secondary button--compact"
                  type="button"
                  onClick={() => onItem(supplier)}
                >
                  Add price
                </button>
                <button
                  className={`button button--compact ${supplier.active ? 'button--danger-muted' : 'button--secondary'}`}
                  type="button"
                  onClick={() => onToggle(supplier)}
                >
                  {supplier.active ? 'Deactivate' : 'Reactivate'}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function PurchasingTab({
  orders,
  selected,
  isLoading,
  onCreate,
  onSelect,
  onLine,
  onRemove,
  onStatus,
  onReceive,
}: {
  orders: PurchaseOrder[]
  selected?: PurchaseOrder
  isLoading: boolean
  onCreate: () => void
  onSelect: (order: PurchaseOrder) => void
  onLine: (line: PurchaseOrderLine | null) => void
  onRemove: (line: PurchaseOrderLine) => void
  onStatus: (status: PurchaseOrderStatus) => void
  onReceive: (line: PurchaseOrderLine) => void
}) {
  if (isLoading) return <div className="table-state">Loading purchase orders…</div>
  return (
    <section>
      <div className="workspace-toolbar">
        <div>
          <p className="eyebrow">Stock replenishment</p>
          <h2>Purchase orders</h2>
        </div>
        <button className="button button--primary" type="button" onClick={onCreate}>
          Create draft
        </button>
      </div>
      <div className="purchasing-layout">
        <div className="purchase-list">
          {orders.map((order) => (
            <button
              className={
                selected?.id === order.id ? 'purchase-list__item active' : 'purchase-list__item'
              }
              type="button"
              onClick={() => onSelect(order)}
              key={order.id}
            >
              <strong>{order.purchaseOrderNumber}</strong>
              <span>{order.supplierName}</span>
              <span>
                {order.status} · {formatEur(order.total.toFixed(2))}
              </span>
              <span>
                {order.orderedAt
                  ? `Ordered ${new Date(order.orderedAt).toLocaleString()}`
                  : 'Draft not ordered'}
              </span>
              <span>
                {order.items.filter((line) => line.remainingQuantity === 0).length} /{' '}
                {order.items.length} lines fully received
              </span>
            </button>
          ))}
          {!orders.length && <div className="table-state">No purchase orders yet.</div>}
        </div>
        {selected && (
          <article className="purchase-detail">
            <header>
              <div>
                <p className="menu-code">{selected.purchaseOrderNumber}</p>
                <h3>{selected.supplierName}</h3>
              </div>
              <span className="status-pill">{selected.status}</span>
            </header>
            <p>{selected.notes || 'No notes.'}</p>
            <div className="purchase-lines">
              {selected.items.map((line) => (
                <div className="purchase-line" key={line.id}>
                  <div>
                    <strong>{line.inventoryName}</strong>
                    <span>
                      {line.inventoryCode} · {line.orderedQuantity} {unitLabel(line.unit)} ordered
                    </span>
                    <span>
                      {line.receivedQuantity} received · {line.remainingQuantity} remaining
                    </span>
                  </div>
                  <div>
                    <strong>{formatEur(line.lineTotal.toFixed(2))}</strong>
                    <small>
                      €{line.unitCost.toFixed(4)} / {unitLabel(line.unit)}
                    </small>
                  </div>
                  <div className="purchase-line__actions">
                    {selected.status === 'DRAFT' && (
                      <>
                        <button
                          className="button button--secondary button--compact"
                          type="button"
                          onClick={() => onLine(line)}
                        >
                          Edit
                        </button>
                        <button
                          className="button button--danger-muted button--compact"
                          type="button"
                          onClick={() => onRemove(line)}
                        >
                          Remove
                        </button>
                      </>
                    )}
                    {(selected.status === 'ORDERED' || selected.status === 'PARTIALLY_RECEIVED') &&
                      line.remainingQuantity > 0 && (
                        <button
                          className="button button--secondary button--compact"
                          type="button"
                          onClick={() => onReceive(line)}
                        >
                          Receive
                        </button>
                      )}
                  </div>
                </div>
              ))}
            </div>
            <footer>
              <strong>Total {formatEur(selected.total.toFixed(2))}</strong>
              <div className="menu-card__actions">
                {selected.status === 'DRAFT' && (
                  <>
                    <button
                      className="button button--secondary button--compact"
                      type="button"
                      onClick={() => onLine(null)}
                    >
                      Add item
                    </button>
                    <button
                      className="button button--primary button--compact"
                      type="button"
                      disabled={!selected.items.length}
                      onClick={() => onStatus('ORDERED')}
                    >
                      Order PO
                    </button>
                  </>
                )}
                {!['RECEIVED', 'CANCELLED'].includes(selected.status) && (
                  <button
                    className="button button--danger-muted button--compact"
                    type="button"
                    onClick={() => onStatus('CANCELLED')}
                  >
                    Cancel
                  </button>
                )}
              </div>
            </footer>
          </article>
        )}
      </div>
    </section>
  )
}

function HistoryDialog({
  item,
  query,
  onClose,
}: {
  item: InventoryItem
  query: { isPending: boolean; isError: boolean; data?: Awaited<ReturnType<typeof listMovements>> }
  onClose: () => void
}) {
  return (
    <Dialog
      className="dialog inventory-dialog inventory-dialog--wide"
      labelledBy="history-title"
      onClose={onClose}
    >
      <div className="dialog__header">
        <h2 id="history-title">Movement history · {item.name}</h2>
        <button className="icon-button" type="button" aria-label="Close dialog" onClick={onClose}>
          &times;
        </button>
      </div>
      {query.isPending && <p>Loading history…</p>}
      {query.isError && <p role="alert">Movement history could not be loaded.</p>}{' '}
      {!query.isPending && !query.isError && !query.data?.length && <p>No movements recorded.</p>}
      <div className="movement-list">
        {query.data?.map((movement) => (
          <div className="movement-row" key={movement.id}>
            <time dateTime={movement.occurredAt}>
              {new Date(movement.occurredAt).toLocaleString()}
            </time>
            <strong>{movement.movementType.replaceAll('_', ' ')}</strong>
            <span className={movement.signedQuantity < 0 ? 'stock-negative' : 'stock-positive'}>
              {movement.signedQuantity > 0 ? '+' : ''}
              {movement.signedQuantity.toFixed(3)} {unitLabel(movement.unit)}
            </span>
            <span>
              {movement.referenceType || 'Manual'}
              {movement.reason ? ` · ${movement.reason}` : ''}
            </span>
          </div>
        ))}
      </div>
    </Dialog>
  )
}

function QueryState({
  query,
  empty,
  noun,
  children,
}: {
  query: InventoryQuery
  empty: boolean
  noun: string
  children: ReactNode
}) {
  if (query.isPending) return <div className="table-state">Loading {noun}…</div>
  if (query.isError)
    return (
      <div className="table-state table-state--error" role="alert">
        <p>{noun} could not be loaded.</p>
        <button className="button button--secondary" type="button" onClick={() => query.refetch()}>
          Try again
        </button>
      </div>
    )
  if (empty)
    return (
      <div className="table-state">
        <h2>No {noun} match.</h2>
      </div>
    )
  return <>{children}</>
}
