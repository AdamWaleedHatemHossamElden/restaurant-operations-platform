import { describe, expect, it } from 'vitest'

import { formatEur } from './money'

describe('EUR money formatting', () => {
  it('formats decimal strings without floating-point arithmetic', () => {
    expect(formatEur('0')).toBe('€0.00')
    expect(formatEur('4.2')).toBe('€4.20')
    expect(formatEur('1234567890.09')).toBe('€1,234,567,890.09')
  })
})
