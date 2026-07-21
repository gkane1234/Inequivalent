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
 * @param {(n: number, list: import('./expression-set.js').ExpressionSet) => void} [options.onLevelComplete]
 */
export function generateThroughN({
  targetN,
  rounding = 9,
  numTruncators = 20,
  onProgress = () => {},
  onLevelComplete = () => {},
}) {
  const lists = [];

  const first = new ExpressionSet(1, rounding, numTruncators);
  first.forceAdd(new Expression([0], [], [true]));
  first.cleanup();
  lists.push(first);
  onLevelComplete(1, first);

  onProgress({
    type: "level",
    n: 1,
    count: 1,
    expected: expectedCount(1),
    fraction: 1 / targetN,
    message: "Ready: 1 value (1 expression)",
  });

  for (let currentNumValues = 2; currentNumValues <= targetN; currentNumValues++) {
    onProgress({
      type: "level-start",
      n: currentNumValues,
      fraction: (currentNumValues - 1) / targetN,
      message: `Generating expressions for ${currentNumValues} values…`,
    });

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
          }
        }
      }
    }

    current.cleanup();
    lists.push(current);
    onLevelComplete(currentNumValues, current);

    onProgress({
      type: "level",
      n: currentNumValues,
      count: current.numExpressions,
      expected: expectedCount(currentNumValues),
      fraction: currentNumValues / targetN,
      message: `Done n=${currentNumValues}: ${current.numExpressions.toLocaleString()} expressions (expected ${expectedCount(currentNumValues).toLocaleString()})`,
    });
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
