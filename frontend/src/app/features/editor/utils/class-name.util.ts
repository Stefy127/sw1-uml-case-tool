/**
 * Normalizes a UML class name only for comparison.
 * The name stored in the canonical model is never changed.
 */
export function normalizeClassName(value: string | null | undefined): string {
  return (value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase();
}
