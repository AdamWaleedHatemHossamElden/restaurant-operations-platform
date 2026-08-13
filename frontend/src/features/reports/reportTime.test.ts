import { describe, expect, it } from 'vitest'

import { customRange, localDateInput, presetRange } from './reportTime'

describe('report period controls', () => {
  it('builds local-day half-open presets', () => {
    const now = new Date(2030, 0, 15, 14, 30)
    const range = presetRange('LAST_7_DAYS', now)
    expect(localDateInput(range.from)).toBe('2030-01-09')
    expect(localDateInput(range.to)).toBe('2030-01-16')
  })

  it('converts a valid custom local date range to instants', () => {
    const range = customRange('2030-02-01', '2030-03-01')
    expect(range).not.toBeNull()
    expect(localDateInput(range!.from)).toBe('2030-02-01')
    expect(localDateInput(range!.to)).toBe('2030-03-01')
  })

  it('rejects empty and reversed custom periods', () => {
    expect(customRange('', '2030-01-02')).toBeNull()
    expect(customRange('2030-01-02', '2030-01-02')).toBeNull()
    expect(customRange('2030-01-03', '2030-01-02')).toBeNull()
  })
})
