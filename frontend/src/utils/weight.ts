export type WeightUnit = 'jin' | 'kg'

const STORAGE_KEY = 'fitapp_body_weight_unit'

export function getWeightUnit(): WeightUnit {
  try {
    return uni.getStorageSync(STORAGE_KEY) === 'kg' ? 'kg' : 'jin'
  } catch {
    return 'jin'
  }
}

export function setWeightUnit(unit: WeightUnit): void {
  uni.setStorageSync(STORAGE_KEY, unit)
}

export function weightUnitLabel(unit: WeightUnit): string {
  return unit === 'kg' ? '千克' : '斤'
}

export function weightUnitShort(unit: WeightUnit): string {
  return unit === 'kg' ? 'kg' : '斤'
}

export function kgToDisplay(kg: number, unit: WeightUnit): number {
  return unit === 'kg' ? kg : kg * 2
}

export function displayToKg(value: number, unit: WeightUnit): number {
  return unit === 'kg' ? value : value / 2
}

export function weightPrecision(unit: WeightUnit): number {
  return unit === 'kg' ? 2 : 1
}

export function formatWeight(kg: number | null | undefined, unit: WeightUnit): string {
  if (kg == null || !Number.isFinite(Number(kg))) return '–'
  return kgToDisplay(Number(kg), unit).toFixed(weightPrecision(unit))
}

export function parseWeightInput(input: string, unit: WeightUnit): number | null {
  const value = input.trim()
  if (!value) return null
  if (!/^\d+(?:\.\d+)?$/.test(value)) return Number.NaN
  const fraction = value.split('.')[1]?.length ?? 0
  if (fraction > weightPrecision(unit)) return Number.NaN
  const display = Number(value)
  if (!Number.isFinite(display)) return Number.NaN
  return Number(displayToKg(display, unit).toFixed(2))
}
