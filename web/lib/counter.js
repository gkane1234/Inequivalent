/**
 * OEIS A140606 expected counts (same algorithm as Java Counter).
 * Used to size sets and validate generation.
 */

const EXPECTED = Object.freeze({
  1: 1,
  2: 6,
  3: 68,
  4: 1170,
  5: 27142,
  6: 793002,
  7: 27914126,
});

export function expectedCount(n) {
  if (!(n in EXPECTED)) {
    throw new Error(`No expected count for n=${n}`);
  }
  return EXPECTED[n];
}
