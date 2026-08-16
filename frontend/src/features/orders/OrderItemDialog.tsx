import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'

import { Dialog } from '../../components/ui/Dialog'
import { formatEur } from '../menu/money'
import type { MenuItem, ModifierGroup } from '../menu/menuTypes'
import { estimatedLineTotal } from './orderMoney'
import { orderItemFormSchema, type OrderItemFormValues } from './orderSchemas'
import type { ModifierSelectionInput, OrderItem } from './orderTypes'

type Props = {
  menuItem: MenuItem
  groups: ModifierGroup[]
  orderItem?: OrderItem | null
  isSaving: boolean
  error: string | null
  onClose: () => void
  onSave: (
    values: OrderItemFormValues,
    selections: ModifierSelectionInput[] | undefined,
  ) => Promise<void>
}

function initialSelections(orderItem?: OrderItem | null) {
  const selections: Record<number, number[]> = {}
  for (const modifier of orderItem?.modifiers ?? []) {
    selections[modifier.modifierGroupId] = [
      ...(selections[modifier.modifierGroupId] ?? []),
      modifier.modifierOptionId,
    ]
  }
  return selections
}

function optionIds(selections: Record<number, number[]>) {
  return Object.values(selections)
    .flat()
    .sort((left, right) => left - right)
}

export function OrderItemDialog({
  menuItem,
  groups,
  orderItem,
  isSaving,
  error,
  onClose,
  onSave,
}: Props) {
  const assignedGroups = useMemo(
    () =>
      menuItem.modifierGroups
        .slice()
        .sort(
          (left, right) =>
            left.displayOrder - right.displayOrder || left.modifierGroupId - right.modifierGroupId,
        )
        .map((assignment) => groups.find((group) => group.id === assignment.modifierGroupId))
        .filter((group): group is ModifierGroup => Boolean(group)),
    [groups, menuItem.modifierGroups],
  )
  const [selections, setSelections] = useState<Record<number, number[]>>(() =>
    initialSelections(orderItem),
  )
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const {
    control,
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<OrderItemFormValues>({
    resolver: zodResolver(orderItemFormSchema),
    defaultValues: { quantity: orderItem?.quantity ?? 1, notes: orderItem?.notes ?? '' },
  })
  const quantity = useWatch({ control, name: 'quantity' })

  const changed = orderItem
    ? JSON.stringify(optionIds(selections)) !==
      JSON.stringify(
        orderItem.modifiers.map((modifier) => modifier.modifierOptionId).sort((a, b) => a - b),
      )
    : true
  const selectedAdjustments = assignedGroups.flatMap((group) =>
    group.options
      .filter((option) => selections[group.id]?.includes(option.id))
      .map((option) => option.priceAdjustment),
  )
  const estimate =
    orderItem && !changed
      ? estimatedLineTotal(orderItem.unitTotal, [], quantity)
      : estimatedLineTotal(menuItem.basePrice, selectedAdjustments, quantity)

  const submit = async (values: OrderItemFormValues) => {
    if (!orderItem || changed) {
      for (const group of assignedGroups.filter((value) => value.active)) {
        const count = selections[group.id]?.length ?? 0
        if (count < group.minimumSelections || count > group.maximumSelections) {
          setSelectionError(
            `${group.name} requires ${group.minimumSelections} to ${group.maximumSelections} selections.`,
          )
          return
        }
      }
    }
    setSelectionError(null)
    const payload = assignedGroups
      .filter((group) => group.active)
      .map((group) => ({ modifierGroupId: group.id, optionIds: selections[group.id] ?? [] }))
    await onSave(values, !orderItem || changed ? payload : undefined)
  }

  const chooseSingle = (groupId: number, optionId: number | null) => {
    setSelections((current) => ({ ...current, [groupId]: optionId === null ? [] : [optionId] }))
  }
  const chooseMultiple = (group: ModifierGroup, optionId: number, checked: boolean) => {
    setSelections((current) => {
      const selected = current[group.id] ?? []
      const next = checked
        ? [...selected, optionId].slice(0, group.maximumSelections)
        : selected.filter((id) => id !== optionId)
      return { ...current, [group.id]: next }
    })
  }

  return (
    <Dialog
      className="table-dialog order-item-dialog"
      labelledBy="order-item-dialog-title"
      onClose={onClose}
    >
      <div className="table-dialog__heading">
        <div>
          <p className="eyebrow">{menuItem.code}</p>
          <h2 id="order-item-dialog-title">
            {orderItem ? 'Edit' : 'Add'} {menuItem.name}
          </h2>
        </div>
        <button
          className="icon-button"
          type="button"
          onClick={onClose}
          aria-label="Close item form"
        >
          &times;
        </button>
      </div>
      {(error || selectionError) && (
        <div className="form-alert" role="alert">
          {error ?? selectionError}
        </div>
      )}
      <form className="order-item-form" onSubmit={handleSubmit(submit)} noValidate>
        <div className="form-field">
          <label htmlFor="order-item-quantity">Quantity</label>
          <input
            id="order-item-quantity"
            type="number"
            min="1"
            max="99"
            {...register('quantity', { valueAsNumber: true })}
          />
          {errors.quantity && <p className="field-error">{errors.quantity.message}</p>}
        </div>
        <div className="form-field order-item-form__wide">
          <label htmlFor="order-item-notes">Item notes (optional)</label>
          <textarea id="order-item-notes" rows={2} {...register('notes')} />
          {errors.notes && <p className="field-error">{errors.notes.message}</p>}
        </div>
        {assignedGroups.map((group) => {
          const activeOptions = group.options.filter((option) => option.active)
          const selected = selections[group.id] ?? []
          return (
            <fieldset
              className="modifier-fieldset order-item-form__wide"
              key={group.id}
              disabled={!group.active}
            >
              <legend>
                {group.name}{' '}
                <span>
                  {group.minimumSelections}–{group.maximumSelections}
                </span>
              </legend>
              {!group.active && (
                <p className="field-hint">This historical group is currently inactive.</p>
              )}
              {group.selectionType === 'SINGLE' && group.minimumSelections === 0 && (
                <label className="modifier-choice">
                  <input
                    type="radio"
                    name={`modifier-${group.id}`}
                    checked={selected.length === 0}
                    onChange={() => chooseSingle(group.id, null)}
                  />
                  <span>No selection</span>
                </label>
              )}
              {activeOptions.map((option) => (
                <label className="modifier-choice" key={option.id}>
                  <input
                    type={group.selectionType === 'SINGLE' ? 'radio' : 'checkbox'}
                    name={`modifier-${group.id}`}
                    checked={selected.includes(option.id)}
                    disabled={
                      group.selectionType === 'MULTIPLE' &&
                      !selected.includes(option.id) &&
                      selected.length >= group.maximumSelections
                    }
                    onChange={(event) =>
                      group.selectionType === 'SINGLE'
                        ? chooseSingle(group.id, option.id)
                        : chooseMultiple(group, option.id, event.target.checked)
                    }
                  />
                  <span>{option.name}</span>
                  <strong>+{formatEur(option.priceAdjustment)}</strong>
                </label>
              ))}
            </fieldset>
          )
        })}
        <div className="order-item-estimate order-item-form__wide" aria-live="polite">
          <span>{orderItem && !changed ? 'Snapshot total' : 'Estimated total'}</span>
          <strong>{formatEur(estimate)}</strong>
          <small>The backend recalculates and returns the authoritative price.</small>
        </div>
        {orderItem && changed && (
          <p className="form-alert order-item-form__wide" role="status">
            Modifier changes refresh the full line snapshot from the current menu.
          </p>
        )}
        <div className="dialog-actions order-item-form__wide">
          <button className="button button--secondary" type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="button button--primary" type="submit" disabled={isSaving}>
            {isSaving ? 'Saving…' : orderItem ? 'Save item' : 'Add item'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
