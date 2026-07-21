/**
 * Quick correctness check for generation counts (run with: node --experimental-vm-modules web/scripts/verify.mjs)
 * Or: node web/scripts/verify.mjs after ensuring "type":"module" — use dynamic import of relative paths.
 */
import { generateThroughN } from "../lib/expression-dynamic.js";
import { findAllSolutions } from "../lib/solver.js";
import { expectedCount } from "../lib/counter.js";

const maxN = Number(process.argv[2] || 4);

console.log(`Generating through n=${maxN}…`);
const start = Date.now();
const lists = generateThroughN({
  targetN: maxN,
  rounding: 9,
  numTruncators: 20,
  onProgress: (p) => {
    if (p.type === "level" || p.type === "level-start") console.log(p.message);
  },
});

let ok = true;
for (let n = 1; n <= maxN; n++) {
  const list = lists[n - 1];
  const expected = expectedCount(n);
  const count = list.numExpressions;
  const mark = count === expected ? "OK" : "MISMATCH";
  if (count !== expected) ok = false;
  console.log(`n=${n}: got ${count}, expected ${expected} [${mark}]`);
}

if (maxN >= 4) {
  const list4 = lists[3];
  const sols = findAllSolutions(list4, [2, 4, 7, 10], 24, 20);
  console.log(`24-game [2,4,7,10]: ${sols.length} solutions (sample: ${sols[0]?.expression ?? "none"})`);
  if (sols.length === 0) ok = false;
}

console.log(`Elapsed ${(Date.now() - start) / 1000}s`);
process.exit(ok ? 0 : 1);
