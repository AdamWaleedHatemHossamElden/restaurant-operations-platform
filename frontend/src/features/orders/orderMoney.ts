function decimalToCents(decimal: string): bigint {
  const match = decimal.match(/^(\d+)(?:\.(\d{0,2}))?$/)
  if (!match) return 0n
  return BigInt(match[1]) * 100n + BigInt((match[2] ?? '').padEnd(2, '0'))
}

export function estimatedLineTotal(
  basePrice: string,
  adjustments: string[],
  quantity: number,
): string {
  const unit = adjustments.reduce(
    (total, value) => total + decimalToCents(value),
    decimalToCents(basePrice),
  )
  const cents = unit * BigInt(Number.isInteger(quantity) && quantity > 0 ? quantity : 0)
  return `${cents / 100n}.${(cents % 100n).toString().padStart(2, '0')}`
}
