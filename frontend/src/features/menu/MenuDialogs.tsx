import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'

import { Dialog } from '../../components/ui/Dialog'
import {
  categoryFormSchema,
  groupFormSchema,
  itemFormSchema,
  optionFormSchema,
  type CategoryFormValues,
  type GroupFormValues,
  type ItemFormValues,
  type OptionFormValues,
} from './menuSchemas'
import type { MenuCategory, MenuItem, ModifierGroup, ModifierOption } from './menuTypes'

type CommonProps = { isSaving: boolean; error: string | null; onClose: () => void }

function DialogFrame({
  title,
  label,
  error,
  onClose,
  children,
}: CommonProps & { title: string; label: string; children: ReactNode }) {
  return (
    <Dialog className="table-dialog menu-dialog" labelledBy="menu-dialog-title" onClose={onClose}>
      <div className="table-dialog__heading">
        <div>
          <p className="eyebrow">{label}</p>
          <h2 id="menu-dialog-title">{title}</h2>
        </div>
        <button
          className="icon-button"
          type="button"
          onClick={onClose}
          aria-label="Close menu form"
        >
          &times;
        </button>
      </div>
      {error && (
        <div className="form-alert" role="alert">
          {error}
        </div>
      )}
      {children}
    </Dialog>
  )
}

function Actions({
  isSaving,
  onClose,
  action = 'Save',
}: Pick<CommonProps, 'isSaving' | 'onClose'> & { action?: string }) {
  return (
    <div className="dialog-actions">
      <button className="button button--secondary" type="button" onClick={onClose}>
        Cancel
      </button>
      <button className="button button--primary" type="submit" disabled={isSaving}>
        {isSaving ? 'Saving…' : action}
      </button>
    </div>
  )
}

export function CategoryDialog({
  category,
  onSave,
  ...common
}: CommonProps & {
  category: MenuCategory | null
  onSave: (values: CategoryFormValues) => Promise<void>
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CategoryFormValues>({
    resolver: zodResolver(categoryFormSchema),
    defaultValues: { name: '', description: '', displayOrder: 0 },
  })
  useEffect(
    () =>
      reset(
        category
          ? {
              name: category.name,
              description: category.description ?? '',
              displayOrder: category.displayOrder,
            }
          : { name: '', description: '', displayOrder: 0 },
      ),
    [category, reset],
  )
  return (
    <DialogFrame
      {...common}
      label={category ? 'Edit category' : 'New category'}
      title={category ? category.name : 'Create category'}
    >
      <form className="menu-form" onSubmit={handleSubmit(onSave)} noValidate>
        <Field label="Name" id="category-name" error={errors.name?.message}>
          <input id="category-name" {...register('name')} />
        </Field>
        <Field label="Description" id="category-description" error={errors.description?.message}>
          <textarea id="category-description" rows={3} {...register('description')} />
        </Field>
        <Field label="Display order" id="category-order" error={errors.displayOrder?.message}>
          <input
            id="category-order"
            type="number"
            min="0"
            {...register('displayOrder', { valueAsNumber: true })}
          />
        </Field>
        <Actions {...common} action={category ? 'Save changes' : 'Create category'} />
      </form>
    </DialogFrame>
  )
}

export function ItemDialog({
  item,
  categories,
  onSave,
  ...common
}: CommonProps & {
  item: MenuItem | null
  categories: MenuCategory[]
  onSave: (values: ItemFormValues) => Promise<void>
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ItemFormValues>({
    resolver: zodResolver(itemFormSchema),
    defaultValues: {
      categoryId: 0,
      code: '',
      name: '',
      description: '',
      basePrice: '0.00',
      displayOrder: 0,
    },
  })
  useEffect(
    () =>
      reset(
        item
          ? {
              categoryId: item.category.id,
              code: item.code,
              name: item.name,
              description: item.description ?? '',
              basePrice: item.basePrice,
              displayOrder: item.displayOrder,
            }
          : {
              categoryId: categories.find((entry) => entry.active)?.id ?? 0,
              code: '',
              name: '',
              description: '',
              basePrice: '0.00',
              displayOrder: 0,
            },
      ),
    [categories, item, reset],
  )
  return (
    <DialogFrame
      {...common}
      label={item ? 'Edit item' : 'New item'}
      title={item ? item.name : 'Create menu item'}
    >
      <form className="menu-form menu-form--two" onSubmit={handleSubmit(onSave)} noValidate>
        <Field label="Category" id="item-category" error={errors.categoryId?.message}>
          <select id="item-category" {...register('categoryId', { valueAsNumber: true })}>
            <option value="0">Choose category</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
                {category.active ? '' : ' (inactive)'}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Item code" id="item-code" error={errors.code?.message}>
          <input id="item-code" {...register('code')} />
        </Field>
        <Field label="Name" id="item-name" error={errors.name?.message}>
          <input id="item-name" {...register('name')} />
        </Field>
        <Field label="Base price (EUR)" id="item-price" error={errors.basePrice?.message}>
          <input id="item-price" inputMode="decimal" {...register('basePrice')} />
        </Field>
        <Field label="Description" id="item-description" error={errors.description?.message} wide>
          <textarea id="item-description" rows={3} {...register('description')} />
        </Field>
        <Field label="Display order" id="item-order" error={errors.displayOrder?.message}>
          <input
            id="item-order"
            type="number"
            min="0"
            {...register('displayOrder', { valueAsNumber: true })}
          />
        </Field>
        <Actions {...common} action={item ? 'Save changes' : 'Create item'} />
      </form>
    </DialogFrame>
  )
}

export function GroupDialog({
  group,
  onSave,
  ...common
}: CommonProps & {
  group: ModifierGroup | null
  onSave: (values: GroupFormValues) => Promise<void>
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<GroupFormValues>({
    resolver: zodResolver(groupFormSchema),
    defaultValues: {
      name: '',
      description: '',
      selectionType: 'SINGLE',
      minimumSelections: 0,
      maximumSelections: 1,
      displayOrder: 0,
    },
  })
  useEffect(
    () =>
      reset(
        group
          ? {
              name: group.name,
              description: group.description ?? '',
              selectionType: group.selectionType,
              minimumSelections: group.minimumSelections,
              maximumSelections: group.maximumSelections,
              displayOrder: group.displayOrder,
            }
          : {
              name: '',
              description: '',
              selectionType: 'SINGLE',
              minimumSelections: 0,
              maximumSelections: 1,
              displayOrder: 0,
            },
      ),
    [group, reset],
  )
  return (
    <DialogFrame
      {...common}
      label={group ? 'Edit modifier' : 'New modifier'}
      title={group ? group.name : 'Create modifier group'}
    >
      <form className="menu-form menu-form--two" onSubmit={handleSubmit(onSave)} noValidate>
        <Field label="Name" id="group-name" error={errors.name?.message}>
          <input id="group-name" {...register('name')} />
        </Field>
        <Field label="Selection type" id="group-type" error={errors.selectionType?.message}>
          <select id="group-type" {...register('selectionType')}>
            <option value="SINGLE">Single</option>
            <option value="MULTIPLE">Multiple</option>
          </select>
        </Field>
        <Field
          label="Minimum selections"
          id="group-minimum"
          error={errors.minimumSelections?.message}
        >
          <input
            id="group-minimum"
            type="number"
            min="0"
            max="20"
            {...register('minimumSelections', { valueAsNumber: true })}
          />
        </Field>
        <Field
          label="Maximum selections"
          id="group-maximum"
          error={errors.maximumSelections?.message}
        >
          <input
            id="group-maximum"
            type="number"
            min="1"
            max="20"
            {...register('maximumSelections', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Description" id="group-description" error={errors.description?.message} wide>
          <textarea id="group-description" rows={3} {...register('description')} />
        </Field>
        <Field label="Display order" id="group-order" error={errors.displayOrder?.message}>
          <input
            id="group-order"
            type="number"
            min="0"
            {...register('displayOrder', { valueAsNumber: true })}
          />
        </Field>
        <Actions {...common} action={group ? 'Save changes' : 'Create group'} />
      </form>
    </DialogFrame>
  )
}

export function OptionDialog({
  group,
  option,
  onSave,
  ...common
}: CommonProps & {
  group: ModifierGroup
  option: ModifierOption | null
  onSave: (values: OptionFormValues) => Promise<void>
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<OptionFormValues>({
    resolver: zodResolver(optionFormSchema),
    defaultValues: { name: '', priceAdjustment: '0.00', displayOrder: 0 },
  })
  useEffect(
    () =>
      reset(
        option
          ? {
              name: option.name,
              priceAdjustment: option.priceAdjustment,
              displayOrder: option.displayOrder,
            }
          : { name: '', priceAdjustment: '0.00', displayOrder: group.options.length },
      ),
    [group.options.length, option, reset],
  )
  return (
    <DialogFrame
      {...common}
      label={`Option in ${group.name}`}
      title={option ? `Edit ${option.name}` : 'Create modifier option'}
    >
      <form className="menu-form" onSubmit={handleSubmit(onSave)} noValidate>
        <Field label="Name" id="option-name" error={errors.name?.message}>
          <input id="option-name" {...register('name')} />
        </Field>
        <Field
          label="Price adjustment (EUR)"
          id="option-price"
          error={errors.priceAdjustment?.message}
        >
          <input id="option-price" inputMode="decimal" {...register('priceAdjustment')} />
        </Field>
        <Field label="Display order" id="option-order" error={errors.displayOrder?.message}>
          <input
            id="option-order"
            type="number"
            min="0"
            {...register('displayOrder', { valueAsNumber: true })}
          />
        </Field>
        <Actions {...common} action={option ? 'Save changes' : 'Create option'} />
      </form>
    </DialogFrame>
  )
}

export function AssignmentDialog({
  item,
  groups,
  onSave,
  ...common
}: CommonProps & {
  item: MenuItem
  groups: ModifierGroup[]
  onSave: (ids: number[]) => Promise<void>
}) {
  const [selected, setSelected] = useState(() =>
    item.modifierGroups.map((group) => group.modifierGroupId),
  )
  const available = groups.filter((group) => group.active && !selected.includes(group.id))
  const move = (index: number, change: number) =>
    setSelected((current) => {
      const next = [...current]
      const target = index + change
      if (target < 0 || target >= next.length) return current
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  return (
    <DialogFrame {...common} label="Ordered assignments" title={`Modifiers for ${item.name}`}>
      <div className="assignment-list">
        {selected.length === 0 && <p>No modifier groups assigned.</p>}
        {selected.map((id, index) => {
          const group =
            groups.find((entry) => entry.id === id) ??
            item.modifierGroups.find((entry) => entry.modifierGroupId === id)
          return (
            <div className="assignment-row" key={id}>
              <span>{group?.name ?? `Group ${id}`}</span>
              <div>
                <button
                  type="button"
                  aria-label={`Move ${group?.name} up`}
                  onClick={() => move(index, -1)}
                  disabled={index === 0}
                >
                  ↑
                </button>
                <button
                  type="button"
                  aria-label={`Move ${group?.name} down`}
                  onClick={() => move(index, 1)}
                  disabled={index === selected.length - 1}
                >
                  ↓
                </button>
                <button
                  type="button"
                  onClick={() => setSelected((current) => current.filter((entry) => entry !== id))}
                >
                  Remove
                </button>
              </div>
            </div>
          )
        })}
      </div>
      <div className="form-field">
        <label htmlFor="assignment-add">Add active group</label>
        <select
          id="assignment-add"
          value=""
          onChange={(event) => {
            if (event.target.value)
              setSelected((current) => [...current, Number(event.target.value)])
          }}
        >
          <option value="">Choose group</option>
          {available.map((group) => (
            <option key={group.id} value={group.id}>
              {group.name}
            </option>
          ))}
        </select>
      </div>
      <div className="dialog-actions">
        <button className="button button--secondary" type="button" onClick={common.onClose}>
          Cancel
        </button>
        <button
          className="button button--primary"
          type="button"
          disabled={common.isSaving}
          onClick={() => void onSave(selected)}
        >
          {common.isSaving ? 'Saving…' : 'Save assignments'}
        </button>
      </div>
    </DialogFrame>
  )
}

function Field({
  label,
  id,
  error,
  wide,
  children,
}: {
  label: string
  id: string
  error?: string
  wide?: boolean
  children: ReactNode
}) {
  return (
    <div className={`form-field${wide ? ' menu-form__wide' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error && <p className="field-error">{error}</p>}
    </div>
  )
}
