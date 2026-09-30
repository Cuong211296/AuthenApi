export function sizesOf(variants) {
  return [...new Set(variants.map((v) => v.size))];
}

export function colorsFor(variants, size) {
  return variants
    .filter((v) => v.size === size)
    .map((v) => ({ color: v.color, available: v.stock > 0 }));
}

export function findVariant(variants, size, color) {
  return variants.find((v) => v.size === size && v.color === color);
}
