import { Expression } from "./expression.js";
import { ExpressionSet } from "./expression-set.js";
import { expectedCount } from "./counter.js";

/**
 * Dynamic programming generation of inequivalent expressions (Java ExpressionDynamic).
 * @param {object} options
 * @param {number} options.targetN generate through this n (inclusive), max 6 recommended
 * @param {number} [options.rounding=9]
 * @param {number} [options.numTruncators=20]
 * @param {(msg: object) => void} [options.onProgress]
 * @param {(n: number, list: import('./expression-set.js').ExpressionSet) => void|Promise<void>} [options.onLevelComplete]
 * @param {() => void|Promise<void>} [options.onPause]
 *   Called between levels and during a level so a caller can run other work
 *   (for example solving an n that is already built) without waiting for targetN.
 * @param {number} [options.yieldEveryMs=40]
 *   Minimum gap between inner onPause calls. 0 pauses on the inner cadence immediately.
 */
export async function generateThroughN({
  targetN,
  rounding = 9,
  numTruncators = 20,
  onProgress = () => {},
  onLevelComplete = () => {},
  onPause = null,
  yieldEveryMs = 40,
}) {
  const lists = [];
  let steps = 0;
  let lastYield = 0;

  // Only await real promises. Awaiting every call would turn the hot loop into microtasks.
  async function settle(value) {
    if (value && typeof value.then === "function") await value;
  }

  function pauseIfDue() {
    if (!onPause) return null;
    steps++;
    if ((steps & 31) !== 0) return null;
    const now = Date.now();
    if (yieldEveryMs > 0 && now - lastYield < yieldEveryMs) return null;
    lastYield = now;
    return onPause();
  }

  const first = new ExpressionSet(1, rounding, numTruncators);
  first.forceAdd(new Expression([0], [], [true]));
  first.cleanup();
  lists.push(first);
  const firstReady = onLevelComplete(1, first);

  onProgress({
    type: "level",
    n: 1,
    count: 1,
    expected: expectedCount(1),
    fraction: 1 / targetN,
    message: "Ready: 1 value (1 expression)",
  });
  await settle(firstReady);

  for (let currentNumValues = 2; currentNumValues <= targetN; currentNumValues++) {
    onProgress({
      type: "level-start",
      n: currentNumValues,
      fraction: (currentNumValues - 1) / targetN,
      message: `Generating expressions for ${currentNumValues} values…`,
    });

    if (onPause) {
      lastYield = Date.now();
      await settle(onPause());
    }

    const current = new ExpressionSet(currentNumValues, rounding, numTruncators);
    let start = currentNumValues - 1;
    let end = currentNumValues >> 1;
    if (currentNumValues % 2 === 0) {
      end -= 1;
    }

    let addedSinceReport = 0;

    for (let i = start; i > end; i--) {
      onProgress({
        type: "group",
        n: currentNumValues,
        groupSize: i,
        fraction: (currentNumValues - 1) / targetN,
        message: `n=${currentNumValues}: combining group size ${i}…`,
      });
      if (onPause) await settle(onPause());

      const leftList = lists[i - 1];
      const rightList = lists[currentNumValues - i - 1];
      const combinations = generateCombinations(currentNumValues, i);

      for (const combination of combinations) {
        const remainder = [];
        for (let k = 0; k < currentNumValues; k++) {
          if (!combination.includes(k)) remainder.push(k);
        }

        const left = leftList.changeValueOrder(combination);
        const right = rightList.changeValueOrder(remainder);

        for (let a = 0; a < left.numExpressions; a++) {
          for (let b = 0; b < right.numExpressions; b++) {
            const combined = Expression.createCombinedExpressions(left.get(a), right.get(b));
            for (const expr of combined) {
              if (current.add(expr)) {
                addedSinceReport++;
                if (addedSinceReport >= 50000) {
                  addedSinceReport = 0;
                  onProgress({
                    type: "growth",
                    n: currentNumValues,
                    count: current.numExpressions,
                    expected: expectedCount(currentNumValues),
                    fraction:
                      (currentNumValues - 1) / targetN +
                      (current.numExpressions / expectedCount(currentNumValues)) / targetN,
                    message: `n=${currentNumValues}: ${current.numExpressions.toLocaleString()} / ${expectedCount(currentNumValues).toLocaleString()}`,
                  });
                }
              }
            }
            const pause = pauseIfDue();
            if (pause && typeof pause.then === "function") await pause;
          }
        }
      }
    }

    current.cleanup();
    lists.push(current);
    const levelReady = onLevelComplete(currentNumValues, current);

    onProgress({
      type: "level",
      n: currentNumValues,
      count: current.numExpressions,
      expected: expectedCount(currentNumValues),
      fraction: currentNumValues / targetN,
      message: `Done n=${currentNumValues}: ${current.numExpressions.toLocaleString()} expressions (expected ${expectedCount(currentNumValues).toLocaleString()})`,
    });
    await settle(levelReady);
  }

  return lists;
}

function generateCombinations(n, size) {
  const out = [];
  const combo = [];

  function rec(start) {
    if (combo.length === size) {
      out.push(combo.slice());
      return;
    }
    for (let i = start; i < n; i++) {
      combo.push(i);
      rec(i + 1);
      combo.pop();
    }
  }

  rec(0);
  return out;
}
