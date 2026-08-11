import { useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'

import type {
  Ingredient,
  InventoryItem,
  InventoryUnit,
  PurchaseOrderLine,
  StockMovementType,
  Supplier,
  SupplierItem,
} from './inventoryTypes'
import { unitLabel } from './inventoryUnits'

function Dialog({
  title,
  children,
  onClose,
}: {
  title: string
  children: ReactNode
  onClose: () => void
}) {
  return (
    <div className="dialog-backdrop" role="presentation">
      <section
        className="dialog inventory-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="inventory-dialog-title"
      >
        <div className="dialog__header">
          <h2 id="inventory-dialog-title">{title}</h2>
          <button type="button" aria-label="Close dialog" onClick={onClose}>
            &times;
          </button>
        </div>
        {children}
      </section>
    </div>
  )
}

export function InventoryItemDialog({
  item,
  error,
  onClose,
  onSave,
}: {
  item: InventoryItem | null
  error: string | null
  onClose: () => void
  onSave: (values: {
    code: string
    name: string
    unit: InventoryUnit
    reorderThreshold: number
  }) => Promise<void>
}) {
  const [code, setCode] = useState(item?.code ?? '')
  const [name, setName] = useState(item?.name ?? '')
  const [unit, setUnit] = useState<InventoryUnit>(item?.unit ?? 'GRAM')
  const [threshold, setThreshold] = useState(String(item?.reorderThreshold ?? 0))
  return (
    <Dialog title={item ? `Edit ${item.name}` : 'Create inventory item'} onClose={onClose}>
      <form
        className="dialog-form"
        onSubmit={(event) =>
          submit(event, () => onSave({ code, name, unit, reorderThreshold: Number(threshold) }))
        }
      >
        <Field label="Code">
          <input
            value={code}
            maxLength={40}
            required
            onChange={(event) => setCode(event.target.value)}
          />
        </Field>
        <Field label="Name">
          <input
            value={name}
            maxLength={160}
            required
            onChange={(event) => setName(event.target.value)}
          />
        </Field>
        <Field label="Canonical unit">
          <select
            aria-label="Canonical unit"
            value={unit}
            disabled={Boolean(item)}
            onChange={(event) => setUnit(event.target.value as InventoryUnit)}
          >
            <option value="GRAM">Gram</option>
            <option value="MILLILITER">Milliliter</option>
            <option value="UNIT">Unit</option>
          </select>
          {item && <small>Canonical units cannot change after creation.</small>}
        </Field>
        <Field label="Reorder threshold">
          <input
            type="number"
            min="0"
            step="0.001"
            required
            value={threshold}
            onChange={(event) => setThreshold(event.target.value)}
          />
        </Field>
        <Actions error={error} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function MovementDialog({
  item,
  error,
  onClose,
  onSave,
}: {
  item: InventoryItem
  error: string | null
  onClose: () => void
  onSave: (values: {
    movementType: StockMovementType
    quantity: number
    reason?: string
  }) => Promise<void>
}) {
  const [type, setType] = useState<StockMovementType>('ADJUSTMENT_IN')
  const [quantity, setQuantity] = useState('')
  const [reason, setReason] = useState('')
  return (
    <Dialog title={`Record movement · ${item.name}`} onClose={onClose}>
      <form
        className="dialog-form"
        onSubmit={(event) =>
          submit(event, () =>
            onSave({ movementType: type, quantity: Number(quantity), reason: reason || undefined }),
          )
        }
      >
        <Field label="Movement type">
          <select
            value={type}
            onChange={(event) => setType(event.target.value as StockMovementType)}
          >
            <option value="ADJUSTMENT_IN">Adjustment in</option>
            <option value="ADJUSTMENT_OUT">Adjustment out</option>
            <option value="WASTE">Waste</option>
          </select>
        </Field>
        <Field label={`Quantity (${unitLabel(item.unit)})`}>
          <input
            type="number"
            min="0.001"
            step="0.001"
            required
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
          />
        </Field>
        <Field label="Reason (optional)">
          <textarea
            maxLength={500}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
        <p className="field-hint">
          This creates an immutable ledger entry. Corrections use a compensating movement.
        </p>
        <Actions error={error} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function SupplierDialog({
  supplier,
  error,
  onClose,
  onSave,
}: {
  supplier: Supplier | null
  error: string | null
  onClose: () => void
  onSave: (values: {
    code: string
    name: string
    contactName?: string
    email?: string
    phone?: string
    notes?: string
  }) => Promise<void>
}) {
  const [values, setValues] = useState({
    code: supplier?.code ?? '',
    name: supplier?.name ?? '',
    contactName: supplier?.contactName ?? '',
    email: supplier?.email ?? '',
    phone: supplier?.phone ?? '',
    notes: supplier?.notes ?? '',
  })
  const input =
    (key: keyof typeof values) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setValues((current) => ({ ...current, [key]: event.target.value }))
  return (
    <Dialog title={supplier ? `Edit ${supplier.name}` : 'Create supplier'} onClose={onClose}>
      <form className="dialog-form" onSubmit={(event) => submit(event, () => onSave(values))}>
        <Field label="Code">
          <input required maxLength={40} value={values.code} onChange={input('code')} />
        </Field>
        <Field label="Name">
          <input required maxLength={160} value={values.name} onChange={input('name')} />
        </Field>
        <Field label="Contact name">
          <input maxLength={160} value={values.contactName} onChange={input('contactName')} />
        </Field>
        <Field label="Email">
          <input type="email" maxLength={254} value={values.email} onChange={input('email')} />
        </Field>
        <Field label="Phone">
          <input maxLength={40} value={values.phone} onChange={input('phone')} />
        </Field>
        <Field label="Notes">
          <textarea maxLength={1000} value={values.notes} onChange={input('notes')} />
        </Field>
        <Actions error={error} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function SupplierItemDialog({
  supplier,
  items,
  relationship,
  error,
  onClose,
  onSave,
}: {
  supplier: Supplier
  items: InventoryItem[]
  relationship?: SupplierItem
  error: string | null
  onClose: () => void
  onSave: (
    item: InventoryItem,
    values: { supplierItemCode?: string; unitCost: number; active: boolean; version?: number },
  ) => Promise<void>
}) {
  const [itemId, setItemId] = useState(
    String(relationship?.inventoryItemId ?? items.find((item) => item.active)?.id ?? ''),
  )
  const [code, setCode] = useState(relationship?.supplierItemCode ?? '')
  const [cost, setCost] = useState(String(relationship?.unitCost ?? ''))
  const [active, setActive] = useState(relationship?.active ?? true)
  return (
    <Dialog title={`Supplier item · ${supplier.name}`} onClose={onClose}>
      <form
        className="dialog-form"
        onSubmit={(event) =>
          submit(event, () => {
            const item = items.find((value) => value.id === Number(itemId))
            if (!item) return Promise.reject(new Error('Select an item'))
            return onSave(item, {
              supplierItemCode: code || undefined,
              unitCost: Number(cost),
              active,
              version: relationship?.version,
            })
          })
        }
      >
        <Field label="Inventory item">
          <select
            disabled={Boolean(relationship)}
            required
            value={itemId}
            onChange={(event) => setItemId(event.target.value)}
          >
            <option value="">Select item</option>
            {items
              .filter((item) => item.active || item.id === relationship?.inventoryItemId)
              .map((item) => (
                <option value={item.id} key={item.id}>
                  {item.code} · {item.name} ({unitLabel(item.unit)})
                </option>
              ))}
          </select>
        </Field>
        <Field label="Supplier item code">
          <input maxLength={80} value={code} onChange={(event) => setCode(event.target.value)} />
        </Field>
        <Field label="Unit cost (EUR per canonical unit)">
          <input
            type="number"
            min="0"
            step="0.0001"
            required
            value={cost}
            onChange={(event) => setCost(event.target.value)}
          />
        </Field>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={active}
            onChange={(event) => setActive(event.target.checked)}
          />
          Active relationship
        </label>
        <Actions error={error} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function IngredientDialog({
  title,
  ingredients,
  items,
  error,
  onClose,
  onSave,
}: {
  title: string
  ingredients: Ingredient[]
  items: InventoryItem[]
  error: string | null
  onClose: () => void
  onSave: (
    values: { inventoryItemId: number; quantity: number; displayOrder: number }[],
  ) => Promise<void>
}) {
  const [rows, setRows] = useState(
    ingredients.map((ingredient) => ({
      inventoryItemId: ingredient.inventoryItemId,
      quantity: String(ingredient.quantity),
    })),
  )
  const available = items.filter(
    (item) => item.active || rows.some((row) => row.inventoryItemId === item.id),
  )
  const hasUnusedActiveItem = items.some(
    (item) => item.active && !rows.some((row) => row.inventoryItemId === item.id),
  )
  return (
    <Dialog title={title} onClose={onClose}>
      <form
        className="dialog-form"
        onSubmit={(event) =>
          submit(event, () =>
            onSave(
              rows.map((row, displayOrder) => ({
                inventoryItemId: row.inventoryItemId,
                quantity: Number(row.quantity),
                displayOrder,
              })),
            ),
          )
        }
      >
        <div className="ingredient-editor">
          {rows.map((row, index) => {
            const item = items.find((value) => value.id === row.inventoryItemId)
            return (
              <div className="ingredient-editor__row" key={`${row.inventoryItemId}-${index}`}>
                <select
                  aria-label={`Ingredient ${index + 1}`}
                  value={row.inventoryItemId}
                  onChange={(event) =>
                    setRows((current) =>
                      current.map((value, currentIndex) =>
                        currentIndex === index
                          ? { ...value, inventoryItemId: Number(event.target.value) }
                          : value,
                      ),
                    )
                  }
                >
                  {available.map((value) => (
                    <option value={value.id} key={value.id}>
                      {value.name} · {unitLabel(value.unit)}
                      {value.active ? '' : ' · inactive; remove before saving'}
                    </option>
                  ))}
                </select>
                <input
                  aria-label={`Quantity ${index + 1}`}
                  type="number"
                  min="0.001"
                  step="0.001"
                  required
                  value={row.quantity}
                  onChange={(event) =>
                    setRows((current) =>
                      current.map((value, currentIndex) =>
                        currentIndex === index ? { ...value, quantity: event.target.value } : value,
                      ),
                    )
                  }
                />
                <span>{item ? unitLabel(item.unit) : ''}</span>
                <button
                  type="button"
                  aria-label={`Remove ingredient ${index + 1}`}
                  onClick={() =>
                    setRows((current) =>
                      current.filter((_, currentIndex) => currentIndex !== index),
                    )
                  }
                >
                  Remove
                </button>
              </div>
            )
          })}
        </div>
        <button
          className="button button--secondary"
          type="button"
          disabled={!hasUnusedActiveItem}
          onClick={() => {
            const unused = available.find(
              (item) => item.active && !rows.some((row) => row.inventoryItemId === item.id),
            )
            if (unused)
              setRows((current) => [...current, { inventoryItemId: unused.id, quantity: '1' }])
          }}
        >
          Add ingredient
        </button>
        <Actions error={error} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function PurchaseOrderDialog({
  suppliers,
  error,
  onClose,
  onSave,
}: {
  suppliers: Supplier[]
  error: string | null
  onClose: () => void
  onSave: (supplierId: number, notes?: string) => Promise<void>
}) {
  const [supplierId, setSupplierId] = useState(
    String(suppliers.find((supplier) => supplier.active)?.id ?? ''),
  )
  const [notes, setNotes] = useState('')
  return (
    <Dialog title="Create purchase order" onClose={onClose}>
      <form
        className="dialog-form"
        onSubmit={(event) => submit(event, () => onSave(Number(supplierId), notes || undefined))}
      >
        <Field label="Supplier">
          <select
            required
            value={supplierId}
            onChange={(event) => setSupplierId(event.target.value)}
          >
            <option value="">Select supplier</option>
            {suppliers
              .filter((supplier) => supplier.active)
              .map((supplier) => (
                <option value={supplier.id} key={supplier.id}>
                  {supplier.code} · {supplier.name}
                </option>
              ))}
          </select>
        </Field>
        <Field label="Notes">
          <textarea
            maxLength={1000}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
          />
        </Field>
        <Actions error={error} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function PurchaseLineDialog({
  items,
  line,
  error,
  onClose,
  onSave,
}: {
  items: InventoryItem[]
  line?: PurchaseOrderLine
  error: string | null
  onClose: () => void
  onSave: (itemId: number, quantity: number) => Promise<void>
}) {
  const [itemId, setItemId] = useState(
    String(line?.inventoryItemId ?? items.find((item) => item.active)?.id ?? ''),
  )
  const [quantity, setQuantity] = useState(String(line?.orderedQuantity ?? ''))
  return (
    <Dialog
      title={line ? `Edit ${line.inventoryName}` : 'Add purchase-order item'}
      onClose={onClose}
    >
      <form
        className="dialog-form"
        onSubmit={(event) => submit(event, () => onSave(Number(itemId), Number(quantity)))}
      >
        <Field label="Inventory item">
          <select
            disabled={Boolean(line)}
            required
            value={itemId}
            onChange={(event) => setItemId(event.target.value)}
          >
            <option value="">Select item</option>
            {items
              .filter((item) => item.active)
              .map((item) => (
                <option value={item.id} key={item.id}>
                  {item.code} · {item.name}
                </option>
              ))}
          </select>
        </Field>
        <Field label="Ordered quantity">
          <input
            type="number"
            min="0.001"
            step="0.001"
            required
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
          />
        </Field>
        <Actions error={error} onClose={onClose} />
      </form>
    </Dialog>
  )
}

export function ReceiptDialog({
  line,
  error,
  onClose,
  onSave,
}: {
  line: PurchaseOrderLine
  error: string | null
  onClose: () => void
  onSave: (quantity: number) => Promise<void>
}) {
  const [quantity, setQuantity] = useState(String(line.remainingQuantity))
  return (
    <Dialog title={`Receive ${line.inventoryName}`} onClose={onClose}>
      <form
        className="dialog-form"
        onSubmit={(event) => submit(event, () => onSave(Number(quantity)))}
      >
        <dl className="inventory-facts">
          <div>
            <dt>Ordered</dt>
            <dd>{line.orderedQuantity}</dd>
          </div>
          <div>
            <dt>Already received</dt>
            <dd>{line.receivedQuantity}</dd>
          </div>
          <div>
            <dt>Remaining</dt>
            <dd>{line.remainingQuantity}</dd>
          </div>
        </dl>
        <Field label="Quantity receiving now">
          <input
            type="number"
            min="0.001"
            max={line.remainingQuantity}
            step="0.001"
            required
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
          />
        </Field>
        <Actions error={error} onClose={onClose} />
      </form>
    </Dialog>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="form-field">
      <span>{label}</span>
      {children}
    </label>
  )
}
function Actions({ error, onClose }: { error: string | null; onClose: () => void }) {
  return (
    <>
      <div className="form-alert" role="alert">
        {error}
      </div>
      <div className="dialog__actions">
        <button className="button button--secondary" type="button" onClick={onClose}>
          Cancel
        </button>
        <button className="button button--primary" type="submit">
          Save
        </button>
      </div>
    </>
  )
}
function submit(event: FormEvent, action: () => Promise<void>) {
  event.preventDefault()
  void action().catch(() => undefined)
}
