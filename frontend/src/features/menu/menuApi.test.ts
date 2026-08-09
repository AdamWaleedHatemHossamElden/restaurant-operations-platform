import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { apiClient, setApiAccessToken } from '../../lib/apiClient'
import {
  assignGroups,
  createCategory,
  createGroup,
  createItem,
  createOption,
  listCategories,
  listGroups,
  listItems,
  toggleAvailability,
  toggleCategory,
  toggleGroup,
  toggleItem,
  toggleOption,
  updateCategory,
  updateGroup,
  updateItem,
  updateOption,
} from './menuApi'
import type { MenuCategory, MenuItem, ModifierGroup, ModifierOption } from './menuTypes'

const originalAdapter = apiClient.defaults.adapter
const category: MenuCategory = {
  id: 1,
  name: 'Drinks',
  description: null,
  displayOrder: 0,
  active: true,
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  version: 0,
}
const item: MenuItem = {
  id: 2,
  category: { id: 1, name: 'Drinks', active: true },
  code: 'LATTE',
  name: 'Latte',
  description: null,
  basePrice: '4.20',
  displayOrder: 0,
  active: true,
  availableForSale: true,
  effectivelyAvailable: true,
  modifierGroups: [],
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  version: 0,
}
const option: ModifierOption = {
  id: 4,
  modifierGroupId: 3,
  name: 'Large',
  priceAdjustment: '1.00',
  displayOrder: 0,
  active: true,
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  version: 0,
}
const group: ModifierGroup = {
  id: 3,
  name: 'Size',
  description: null,
  selectionType: 'SINGLE',
  minimumSelections: 1,
  maximumSelections: 1,
  displayOrder: 0,
  active: true,
  assignedItemCount: 0,
  options: [option],
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  version: 0,
}

function response(config: InternalAxiosRequestConfig): AxiosResponse {
  const data =
    config.method === 'get'
      ? config.url?.includes('categories')
        ? [category]
        : config.url?.includes('items')
          ? [item]
          : [group]
      : config.url?.startsWith('/menu/items')
        ? item
        : config.url?.includes('categories')
          ? category
          : config.url?.includes('modifier-options') || config.url?.includes('/options')
            ? option
            : config.url?.includes('modifier-groups')
              ? group
              : item
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}

describe('menu API', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
  })

  it('reuses the protected client for every menu operation and sends versioned writes', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setApiAccessToken('memory-only-token')
    apiClient.defaults.adapter = async (config) => {
      requests.push(config)
      return response(config)
    }
    const categoryValues = { name: 'Drinks', description: '', displayOrder: 0 }
    const itemValues = {
      categoryId: 1,
      code: 'LATTE',
      name: 'Latte',
      description: '',
      basePrice: '4.20',
      displayOrder: 0,
    }
    const groupValues = {
      name: 'Size',
      description: '',
      selectionType: 'SINGLE' as const,
      minimumSelections: 1,
      maximumSelections: 1,
      displayOrder: 0,
    }
    const optionValues = { name: 'Large', priceAdjustment: '1.00', displayOrder: 0 }

    await listCategories({ active: true })
    await createCategory(categoryValues)
    await updateCategory(category, categoryValues)
    await toggleCategory(category)
    await listItems({ search: 'latte' })
    await createItem(itemValues)
    await updateItem(item, itemValues)
    await toggleItem(item)
    await toggleAvailability(item)
    await assignGroups(item, [3])
    await listGroups({ active: true })
    await createGroup(groupValues)
    await updateGroup(group, groupValues)
    await toggleGroup(group)
    await createOption(group.id, optionValues)
    await updateOption(option, optionValues)
    await toggleOption(option)

    expect(requests).toHaveLength(17)
    expect(
      requests.every(
        (request) => request.headers.get('Authorization') === 'Bearer memory-only-token',
      ),
    ).toBe(true)
    expect(requests.map((request) => request.url)).toContain('/menu/items/2/modifier-groups')
    expect(JSON.parse(requests[9].data as string)).toEqual({
      version: 0,
      assignments: [{ modifierGroupId: 3, displayOrder: 0 }],
    })
    expect(JSON.parse(requests[3].data as string)).toEqual({ value: false, version: 0 })
  })
})
