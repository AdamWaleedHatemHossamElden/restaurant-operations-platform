import type { InventoryUnit } from './inventoryTypes'

export function unitLabel(unit: InventoryUnit) {
  return unit === 'GRAM' ? 'g' : unit === 'MILLILITER' ? 'ml' : 'unit'
}
