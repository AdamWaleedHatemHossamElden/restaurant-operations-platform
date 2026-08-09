import axios from 'axios'

import { apiClient } from '../../lib/apiClient'
import type {
  CategoryFormValues,
  GroupFormValues,
  ItemFormValues,
  OptionFormValues,
} from './menuSchemas'
import {
  categoriesSchema,
  categorySchema,
  menuItemSchema,
  menuItemsSchema,
  modifierGroupSchema,
  modifierGroupsSchema,
  modifierOptionSchema,
  type CategoryFilters,
  type GroupFilters,
  type ItemFilters,
  type MenuCategory,
  type MenuItem,
  type ModifierGroup,
  type ModifierOption,
} from './menuTypes'

export const menuKeys = {
  all: ['menu'] as const,
  categories: ['menu', 'categories'] as const,
  items: ['menu', 'items'] as const,
  groups: ['menu', 'modifier-groups'] as const,
}

export async function listCategories(filters: CategoryFilters): Promise<MenuCategory[]> {
  return categoriesSchema.parse((await apiClient.get('/menu/categories', { params: filters })).data)
}
export async function createCategory(values: CategoryFormValues): Promise<MenuCategory> {
  return categorySchema.parse((await apiClient.post('/menu/categories', values)).data)
}
export async function updateCategory(
  category: MenuCategory,
  values: CategoryFormValues,
): Promise<MenuCategory> {
  return categorySchema.parse(
    (
      await apiClient.put(`/menu/categories/${category.id}`, {
        ...values,
        version: category.version,
      })
    ).data,
  )
}
export async function toggleCategory(category: MenuCategory): Promise<MenuCategory> {
  return categorySchema.parse(
    (
      await apiClient.patch(`/menu/categories/${category.id}/activation`, {
        value: !category.active,
        version: category.version,
      })
    ).data,
  )
}

export async function listItems(filters: ItemFilters): Promise<MenuItem[]> {
  return menuItemsSchema.parse((await apiClient.get('/menu/items', { params: filters })).data)
}
export async function createItem(values: ItemFormValues): Promise<MenuItem> {
  return menuItemSchema.parse((await apiClient.post('/menu/items', values)).data)
}
export async function updateItem(item: MenuItem, values: ItemFormValues): Promise<MenuItem> {
  return menuItemSchema.parse(
    (await apiClient.put(`/menu/items/${item.id}`, { ...values, version: item.version })).data,
  )
}
export async function toggleItem(item: MenuItem): Promise<MenuItem> {
  return menuItemSchema.parse(
    (
      await apiClient.patch(`/menu/items/${item.id}/activation`, {
        value: !item.active,
        version: item.version,
      })
    ).data,
  )
}
export async function toggleAvailability(item: MenuItem): Promise<MenuItem> {
  return menuItemSchema.parse(
    (
      await apiClient.patch(`/menu/items/${item.id}/availability`, {
        value: !item.availableForSale,
        version: item.version,
      })
    ).data,
  )
}
export async function assignGroups(item: MenuItem, groupIds: number[]): Promise<MenuItem> {
  return menuItemSchema.parse(
    (
      await apiClient.put(`/menu/items/${item.id}/modifier-groups`, {
        version: item.version,
        assignments: groupIds.map((modifierGroupId, displayOrder) => ({
          modifierGroupId,
          displayOrder,
        })),
      })
    ).data,
  )
}

export async function listGroups(filters: GroupFilters): Promise<ModifierGroup[]> {
  return modifierGroupsSchema.parse(
    (await apiClient.get('/menu/modifier-groups', { params: filters })).data,
  )
}
export async function createGroup(values: GroupFormValues): Promise<ModifierGroup> {
  return modifierGroupSchema.parse((await apiClient.post('/menu/modifier-groups', values)).data)
}
export async function updateGroup(
  group: ModifierGroup,
  values: GroupFormValues,
): Promise<ModifierGroup> {
  return modifierGroupSchema.parse(
    (
      await apiClient.put(`/menu/modifier-groups/${group.id}`, {
        ...values,
        version: group.version,
      })
    ).data,
  )
}
export async function toggleGroup(group: ModifierGroup): Promise<ModifierGroup> {
  return modifierGroupSchema.parse(
    (
      await apiClient.patch(`/menu/modifier-groups/${group.id}/activation`, {
        value: !group.active,
        version: group.version,
      })
    ).data,
  )
}
export async function createOption(
  groupId: number,
  values: OptionFormValues,
): Promise<ModifierOption> {
  return modifierOptionSchema.parse(
    (await apiClient.post(`/menu/modifier-groups/${groupId}/options`, values)).data,
  )
}
export async function updateOption(
  option: ModifierOption,
  values: OptionFormValues,
): Promise<ModifierOption> {
  return modifierOptionSchema.parse(
    (
      await apiClient.put(`/menu/modifier-options/${option.id}`, {
        ...values,
        version: option.version,
      })
    ).data,
  )
}
export async function toggleOption(option: ModifierOption): Promise<ModifierOption> {
  return modifierOptionSchema.parse(
    (
      await apiClient.patch(`/menu/modifier-options/${option.id}/activation`, {
        value: !option.active,
        version: option.version,
      })
    ).data,
  )
}

export function menuRequestError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    const message =
      error.response.data &&
      typeof error.response.data === 'object' &&
      'message' in error.response.data
        ? String(error.response.data.message)
        : ''
    if (message.includes('selection rules'))
      return 'This change would make an assigned modifier group unusable.'
    if (message.includes('changed by another request'))
      return 'This record changed elsewhere. Reload and try again.'
    return message || 'A menu record with the same unique name or code already exists.'
  }
  if (axios.isAxiosError(error) && error.response?.status === 404)
    return 'A related menu record no longer exists. Reload and try again.'
  return 'The menu request could not be completed. Please try again.'
}
