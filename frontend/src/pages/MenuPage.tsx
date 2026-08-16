import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { ReactNode } from 'react'

import {
  AssignmentDialog,
  CategoryDialog,
  GroupDialog,
  ItemDialog,
  OptionDialog,
} from '../features/menu/MenuDialogs'
import {
  assignGroups,
  createCategory,
  createGroup,
  createItem,
  createOption,
  listCategories,
  listGroups,
  listItems,
  menuKeys,
  menuRequestError,
  toggleAvailability,
  toggleCategory,
  toggleGroup,
  toggleItem,
  toggleOption,
  updateCategory,
  updateGroup,
  updateItem,
  updateOption,
} from '../features/menu/menuApi'
import type {
  CategoryFormValues,
  GroupFormValues,
  ItemFormValues,
  OptionFormValues,
} from '../features/menu/menuSchemas'
import type {
  MenuCategory,
  MenuItem,
  ModifierGroup,
  ModifierOption,
} from '../features/menu/menuTypes'
import { formatEur } from '../features/menu/money'

type Tab = 'categories' | 'items' | 'modifiers'

export function MenuPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('categories')
  const [search, setSearch] = useState('')
  const [stateFilter, setStateFilter] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [availabilityFilter, setAvailabilityFilter] = useState('')
  const [sort, setSort] = useState('displayOrder')
  const [categoryEditor, setCategoryEditor] = useState<MenuCategory | null | undefined>()
  const [itemEditor, setItemEditor] = useState<MenuItem | null | undefined>()
  const [groupEditor, setGroupEditor] = useState<ModifierGroup | null | undefined>()
  const [optionEditor, setOptionEditor] = useState<{
    group: ModifierGroup
    option: ModifierOption | null
  }>()
  const [assignmentItem, setAssignmentItem] = useState<MenuItem>()
  const [message, setMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const active = stateFilter === '' ? undefined : stateFilter === 'true'
  const categoriesQuery = useQuery({
    queryKey: [
      ...menuKeys.categories,
      { active: tab === 'categories' ? active : undefined, search },
    ],
    queryFn: () =>
      listCategories({
        active: tab === 'categories' ? active : undefined,
        name: tab === 'categories' ? search || undefined : undefined,
        sortBy: 'displayOrder',
        direction: 'ASC',
      }),
  })
  const itemsQuery = useQuery({
    queryKey: [...menuKeys.items, { active, search, categoryFilter, availabilityFilter, sort }],
    queryFn: () =>
      listItems({
        active: tab === 'items' ? active : undefined,
        search: tab === 'items' ? search || undefined : undefined,
        categoryId: tab === 'items' && categoryFilter ? Number(categoryFilter) : undefined,
        availableForSale:
          tab === 'items' && availabilityFilter ? availabilityFilter === 'true' : undefined,
        sortBy: sort as 'displayOrder' | 'name' | 'code' | 'basePrice',
        direction: 'ASC',
      }),
  })
  const groupsQuery = useQuery({
    queryKey: [...menuKeys.groups, { active, search }],
    queryFn: () =>
      listGroups({
        active: tab === 'modifiers' ? active : undefined,
        name: tab === 'modifiers' ? search || undefined : undefined,
        sortBy: 'displayOrder',
        direction: 'ASC',
      }),
  })

  const invalidateMenu = async () => queryClient.invalidateQueries({ queryKey: menuKeys.all })
  const succeed = async (text: string) => {
    setMessage(text)
    setFormError(null)
    setCategoryEditor(undefined)
    setItemEditor(undefined)
    setGroupEditor(undefined)
    setOptionEditor(undefined)
    setAssignmentItem(undefined)
    await invalidateMenu()
  }
  const fail = (error: unknown) => setFormError(menuRequestError(error))

  const categorySave = useMutation({
    mutationFn: (values: CategoryFormValues) =>
      categoryEditor ? updateCategory(categoryEditor, values) : createCategory(values),
    onSuccess: (category) => succeed(`${category.name} was saved.`),
    onError: fail,
  })
  const itemSave = useMutation({
    mutationFn: (values: ItemFormValues) =>
      itemEditor ? updateItem(itemEditor, values) : createItem(values),
    onSuccess: (item) => succeed(`${item.name} was saved.`),
    onError: fail,
  })
  const groupSave = useMutation({
    mutationFn: (values: GroupFormValues) =>
      groupEditor ? updateGroup(groupEditor, values) : createGroup(values),
    onSuccess: (group) => succeed(`${group.name} was saved.`),
    onError: fail,
  })
  const optionSave = useMutation({
    mutationFn: (values: OptionFormValues) => {
      if (!optionEditor) throw new Error('Option editor is not open')
      return optionEditor.option
        ? updateOption(optionEditor.option, values)
        : createOption(optionEditor.group.id, values)
    },
    onSuccess: (option) => succeed(`${option.name} was saved.`),
    onError: fail,
  })
  const assignmentSave = useMutation({
    mutationFn: (ids: number[]) => {
      if (!assignmentItem) throw new Error('Assignment editor is not open')
      return assignGroups(assignmentItem, ids)
    },
    onSuccess: (item) => succeed(`${item.name} modifier assignments were saved.`),
    onError: fail,
  })
  const quickMutation = useMutation({
    mutationFn: async (action: {
      kind: 'category' | 'item' | 'availability' | 'group' | 'option'
      record: MenuCategory | MenuItem | ModifierGroup | ModifierOption
    }) => {
      switch (action.kind) {
        case 'category':
          return toggleCategory(action.record as MenuCategory)
        case 'item':
          return toggleItem(action.record as MenuItem)
        case 'availability':
          return toggleAvailability(action.record as MenuItem)
        case 'group':
          return toggleGroup(action.record as ModifierGroup)
        case 'option':
          return toggleOption(action.record as ModifierOption)
      }
    },
    onSuccess: async () => {
      setMessage('Menu state was updated.')
      setFormError(null)
      await invalidateMenu()
    },
    onError: (error) => setMessage(menuRequestError(error)),
  })

  const resetFilters = (next: Tab) => {
    setTab(next)
    setSearch('')
    setStateFilter('')
    setCategoryFilter('')
    setAvailabilityFilter('')
    setSort('displayOrder')
  }
  const open = (setter: (value: never) => void, value: unknown) => {
    setFormError(null)
    setter(value as never)
  }

  return (
    <div className="page menu-page">
      <section className="menu-hero" aria-labelledby="menu-title">
        <div>
          <p className="eyebrow">Catalog configuration</p>
          <h1 id="menu-title">Menu management</h1>
          <p>
            Build the categories, sellable items, and reusable modifiers used throughout service.
          </p>
        </div>
        <span className="currency-note">Prices displayed in EUR</span>
      </section>

      {message && (
        <div className="notice" role="status">
          <span>{message}</span>
          <button type="button" onClick={() => setMessage(null)} aria-label="Dismiss notification">
            &times;
          </button>
        </div>
      )}

      <div className="menu-tabs" role="tablist" aria-label="Menu management sections">
        {(['categories', 'items', 'modifiers'] as const).map((entry) => (
          <button
            key={entry}
            type="button"
            role="tab"
            aria-selected={tab === entry}
            className={tab === entry ? 'active' : ''}
            onClick={() => resetFilters(entry)}
          >
            {entry === 'items' ? 'Menu items' : entry.charAt(0).toUpperCase() + entry.slice(1)}
          </button>
        ))}
      </div>

      <section className="menu-toolbar" aria-label={`${tab} filters`}>
        <div className="form-field menu-toolbar__search">
          <label htmlFor="menu-search">Search {tab}</label>
          <input
            id="menu-search"
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={tab === 'items' ? 'Name or code' : 'Name'}
          />
        </div>
        <div className="form-field">
          <label htmlFor="menu-state">Record state</label>
          <select
            id="menu-state"
            value={stateFilter}
            onChange={(event) => setStateFilter(event.target.value)}
          >
            <option value="">All records</option>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </div>
        {tab === 'items' && (
          <>
            <div className="form-field">
              <label htmlFor="menu-category-filter">Category</label>
              <select
                id="menu-category-filter"
                value={categoryFilter}
                onChange={(event) => setCategoryFilter(event.target.value)}
              >
                <option value="">All categories</option>
                {categoriesQuery.data?.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label htmlFor="menu-availability">Sale availability</label>
              <select
                id="menu-availability"
                value={availabilityFilter}
                onChange={(event) => setAvailabilityFilter(event.target.value)}
              >
                <option value="">Any availability</option>
                <option value="true">Available</option>
                <option value="false">Unavailable</option>
              </select>
            </div>
            <div className="form-field">
              <label htmlFor="menu-sort">Sort by</label>
              <select id="menu-sort" value={sort} onChange={(event) => setSort(event.target.value)}>
                <option value="displayOrder">Display order</option>
                <option value="name">Name</option>
                <option value="code">Code</option>
                <option value="basePrice">Price</option>
              </select>
            </div>
          </>
        )}
        <button
          className="button button--primary menu-toolbar__action"
          type="button"
          onClick={() =>
            tab === 'categories'
              ? open(setCategoryEditor, null)
              : tab === 'items'
                ? open(setItemEditor, null)
                : open(setGroupEditor, null)
          }
        >
          Add {tab === 'categories' ? 'category' : tab === 'items' ? 'item' : 'group'}
        </button>
      </section>

      {tab === 'categories' && (
        <CategorySection
          query={categoriesQuery}
          onEdit={(category) => open(setCategoryEditor, category)}
          onToggle={(category) => {
            if (
              !category.active ||
              window.confirm(
                `Deactivate ${category.name}? Its items will become effectively unavailable.`,
              )
            )
              quickMutation.mutate({ kind: 'category', record: category })
          }}
        />
      )}
      {tab === 'items' && (
        <ItemSection
          query={itemsQuery}
          onEdit={(item) => open(setItemEditor, item)}
          onAssign={(item) => open(setAssignmentItem, item)}
          onToggle={(item) => {
            if (!item.active || window.confirm(`Deactivate ${item.name}?`))
              quickMutation.mutate({ kind: 'item', record: item })
          }}
          onAvailability={(item) => quickMutation.mutate({ kind: 'availability', record: item })}
        />
      )}
      {tab === 'modifiers' && (
        <ModifierSection
          query={groupsQuery}
          onEdit={(group) => open(setGroupEditor, group)}
          onOption={(group, option) => open(setOptionEditor, { group, option })}
          onToggle={(group) => {
            if (
              !group.active ||
              window.confirm(`Deactivate ${group.name}? Existing assignments remain visible.`)
            )
              quickMutation.mutate({ kind: 'group', record: group })
          }}
          onToggleOption={(option) => {
            if (!option.active || window.confirm(`Deactivate ${option.name}?`))
              quickMutation.mutate({ kind: 'option', record: option })
          }}
        />
      )}

      {categoryEditor !== undefined && (
        <CategoryDialog
          category={categoryEditor}
          isSaving={categorySave.isPending}
          error={formError}
          onClose={() => setCategoryEditor(undefined)}
          onSave={async (values) => {
            try {
              await categorySave.mutateAsync(values)
            } catch {
              /* displayed by mutation */
            }
          }}
        />
      )}
      {itemEditor !== undefined && (
        <ItemDialog
          item={itemEditor}
          categories={categoriesQuery.data ?? []}
          isSaving={itemSave.isPending}
          error={formError}
          onClose={() => setItemEditor(undefined)}
          onSave={async (values) => {
            try {
              await itemSave.mutateAsync(values)
            } catch {
              /* displayed by mutation */
            }
          }}
        />
      )}
      {groupEditor !== undefined && (
        <GroupDialog
          group={groupEditor}
          isSaving={groupSave.isPending}
          error={formError}
          onClose={() => setGroupEditor(undefined)}
          onSave={async (values) => {
            try {
              await groupSave.mutateAsync(values)
            } catch {
              /* displayed by mutation */
            }
          }}
        />
      )}
      {optionEditor && (
        <OptionDialog
          group={optionEditor.group}
          option={optionEditor.option}
          isSaving={optionSave.isPending}
          error={formError}
          onClose={() => setOptionEditor(undefined)}
          onSave={async (values) => {
            try {
              await optionSave.mutateAsync(values)
            } catch {
              /* displayed by mutation */
            }
          }}
        />
      )}
      {assignmentItem && (
        <AssignmentDialog
          item={assignmentItem}
          groups={groupsQuery.data ?? []}
          isSaving={assignmentSave.isPending}
          error={formError}
          onClose={() => setAssignmentItem(undefined)}
          onSave={async (ids) => {
            try {
              await assignmentSave.mutateAsync(ids)
            } catch {
              /* displayed by mutation */
            }
          }}
        />
      )}
    </div>
  )
}

type QueryState<T> = { isPending: boolean; isError: boolean; data?: T[]; refetch: () => unknown }
function State<T>({
  query,
  noun,
  children,
}: {
  query: QueryState<T>
  noun: string
  children: (data: T[]) => ReactNode
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
  if (!query.data?.length)
    return (
      <div className="table-state">
        <h2>No {noun} match these filters.</h2>
        <p>Create a record or adjust the current filters.</p>
      </div>
    )
  return <>{children(query.data)}</>
}

function CategorySection({
  query,
  onEdit,
  onToggle,
}: {
  query: QueryState<MenuCategory>
  onEdit: (value: MenuCategory) => void
  onToggle: (value: MenuCategory) => void
}) {
  return (
    <State query={query} noun="categories">
      {(categories) => (
        <div className="menu-grid">
          {categories.map((category) => (
            <article
              className={`menu-card${category.active ? '' : ' menu-card--inactive'}`}
              key={category.id}
            >
              <div className="menu-card__heading">
                <div>
                  <p className="eyebrow">Order {category.displayOrder}</p>
                  <h2>{category.name}</h2>
                </div>
                <Pill active={category.active} />
              </div>
              <p>{category.description || 'No description provided.'}</p>
              <div className="menu-card__actions">
                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => onEdit(category)}
                >
                  Edit
                </button>
                <button
                  className={`button ${category.active ? 'button--danger' : 'button--secondary'}`}
                  type="button"
                  onClick={() => onToggle(category)}
                >
                  {category.active ? 'Deactivate' : 'Reactivate'}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </State>
  )
}

function ItemSection({
  query,
  onEdit,
  onAssign,
  onToggle,
  onAvailability,
}: {
  query: QueryState<MenuItem>
  onEdit: (value: MenuItem) => void
  onAssign: (value: MenuItem) => void
  onToggle: (value: MenuItem) => void
  onAvailability: (value: MenuItem) => void
}) {
  return (
    <State query={query} noun="menu items">
      {(items) => (
        <div className="menu-grid menu-grid--items">
          {items.map((item) => (
            <article
              className={`menu-card${item.active ? '' : ' menu-card--inactive'}`}
              key={item.id}
            >
              <div className="menu-card__heading">
                <div>
                  <p className="menu-code">
                    {item.code} · {item.category.name}
                  </p>
                  <h2>{item.name}</h2>
                </div>
                <strong className="menu-price">{formatEur(item.basePrice)}</strong>
              </div>
              <p>{item.description || 'No description provided.'}</p>
              <dl className="menu-facts">
                <div>
                  <dt>Display order</dt>
                  <dd>{item.displayOrder}</dd>
                </div>
                <div>
                  <dt>Modifiers</dt>
                  <dd>{item.modifierGroups.length}</dd>
                </div>
                <div>
                  <dt>Sale flag</dt>
                  <dd>{item.availableForSale ? 'Available' : 'Unavailable'}</dd>
                </div>
                <div>
                  <dt>Effective</dt>
                  <dd>{item.effectivelyAvailable ? 'Sellable' : 'Unavailable'}</dd>
                </div>
              </dl>
              <div className="menu-pill-row">
                <Pill active={item.active} />
                {!item.category.active && <span className="status-pill">Category inactive</span>}
              </div>
              <div className="menu-card__actions menu-card__actions--wrap">
                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => onEdit(item)}
                >
                  Edit
                </button>
                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => onAssign(item)}
                >
                  Modifiers
                </button>
                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => onAvailability(item)}
                >
                  {item.availableForSale ? 'Pause sales' : 'Resume sales'}
                </button>
                <button
                  className={`button ${item.active ? 'button--danger' : 'button--secondary'}`}
                  type="button"
                  onClick={() => onToggle(item)}
                >
                  {item.active ? 'Deactivate' : 'Reactivate'}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </State>
  )
}

function ModifierSection({
  query,
  onEdit,
  onOption,
  onToggle,
  onToggleOption,
}: {
  query: QueryState<ModifierGroup>
  onEdit: (value: ModifierGroup) => void
  onOption: (group: ModifierGroup, option: ModifierOption | null) => void
  onToggle: (value: ModifierGroup) => void
  onToggleOption: (value: ModifierOption) => void
}) {
  return (
    <State query={query} noun="modifier groups">
      {(groups) => (
        <div className="modifier-list">
          {groups.map((group) => (
            <article
              className={`menu-card modifier-card${group.active ? '' : ' menu-card--inactive'}`}
              key={group.id}
            >
              <div className="menu-card__heading">
                <div>
                  <p className="eyebrow">
                    {group.selectionType} · {group.minimumSelections}–{group.maximumSelections}{' '}
                    selections
                  </p>
                  <h2>{group.name}</h2>
                  <p>
                    {group.assignedItemCount} assigned{' '}
                    {group.assignedItemCount === 1 ? 'item' : 'items'}
                  </p>
                </div>
                <Pill active={group.active} />
              </div>
              <div className="option-list">
                <div className="option-list__header">
                  <h3>Options</h3>
                  <button
                    className="button button--secondary"
                    type="button"
                    onClick={() => onOption(group, null)}
                  >
                    Add option
                  </button>
                </div>
                {group.options.length === 0 && <p>No options configured.</p>}
                {group.options.map((option) => (
                  <div
                    className={`option-row${option.active ? '' : ' option-row--inactive'}`}
                    key={option.id}
                  >
                    <div>
                      <strong>{option.name}</strong>
                      <span>
                        Order {option.displayOrder} · {formatEur(option.priceAdjustment)}
                      </span>
                    </div>
                    <div>
                      <button type="button" onClick={() => onOption(group, option)}>
                        Edit
                      </button>
                      <button type="button" onClick={() => onToggleOption(option)}>
                        {option.active ? 'Deactivate' : 'Reactivate'}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
              <div className="menu-card__actions">
                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => onEdit(group)}
                >
                  Edit group
                </button>
                <button
                  className={`button ${group.active ? 'button--danger' : 'button--secondary'}`}
                  type="button"
                  onClick={() => onToggle(group)}
                >
                  {group.active ? 'Deactivate' : 'Reactivate'}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </State>
  )
}

function Pill({ active }: { active: boolean }) {
  return (
    <span className={`status-pill ${active ? 'status-pill--available' : 'status-pill--inactive'}`}>
      {active ? 'Active' : 'Inactive'}
    </span>
  )
}
