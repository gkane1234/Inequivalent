/**
 * Solving an n that is already built must run while a larger n is still generating.
 * Run: node web/scripts/solve-while-generating.mjs
 */
import { generateThroughN } from "../lib/expression-dynamic.js";
import { findAllSolutions } from "../lib/solver.js";
import { expectedCount } from "../lib/counter.js";

let list4 = null;
let building = 0;
let n5Finished = false;
let pausesDuring5 = 0;
let solvedDuring5 = false;
let sample = "";

const started = Date.now();
const lists = await generateThroughN({
  targetN: 5,
  yieldEveryMs: 0,
  onProgress(p) {
    if (p.type === "level-start") building = p.n;
    if (p.type === "level" && p.n === 5) {
      building = 0;
      n5Finished = true;
    }
  },
  onLevelComplete(n, list) {
    if (n === 4) list4 = list;
  },
  onPause() {
    if (building !== 5 || !list4 || n5Finished) return;
    pausesDuring5++;
    if (solvedDuring5) return;
    const sols = findAllSolutions(list4, [3, 3, 7, 7], 24, 5);
    solvedDuring5 = sols.length > 0 && !n5Finished;
    sample = sols[0]?.expression ?? "";
  },
});

let countsOk = true;
for (let n = 1; n <= 5; n++) {
  const got = lists[n - 1].numExpressions;
  const expected = expectedCount(n);
  const mark = got === expected ? "OK" : "MISMATCH";
  if (got !== expected) countsOk = false;
  console.log(`n=${n}: got ${got}, expected ${expected} [${mark}]`);
}

console.log(
  JSON.stringify(
    {
      pausesDuring5,
      solvedDuring5,
      sample,
      elapsedMs: Date.now() - started,
    },
    null,
    2
  )
);

if (!countsOk || pausesDuring5 < 1 || !solvedDuring5 || !sample) {
  process.exit(1);
}
