import { canonicalize } from "./canonical.js";
import { displayExpression } from "./expression.js";
import { rate } from "./difficulty.js";

const ROUNDING = 9;
const TOLERANCE = 1e-5;

export function valuesEqual(a, b) {
  return Math.abs(a - b) <= TOLERANCE;
}

/**
 * @param {import('./expression-set.js').ExpressionSet|import('./expression-set.js').PlainExpressionList} expressionList
 * @param {number[]} values
 * @param {number} goal
 * @param {number} maxSolutions
 * @param {(p: object) => void} [onProgress]
 * @param {{ dedupeRepeated?: boolean }} [options] when set, reorder sums and products and keep one copy of each line
 */
export function findAllSolutions(expressionList, values, goal, maxSolutions = 200, onProgress, options) {
  const dedupe = Boolean(options && options.dedupeRepeated);
  const solutions = [];
  const seen = new Set();
  const total = expressionList.numExpressions;
  const reportEvery = Math.max(1, Math.floor(total / 20));
  let aborted = false;

  for (let i = 0; i < total; i++) {
    const expr = expressionList.get(i);
    const value = expr.evaluateWithValues(values, ROUNDING);
    if (valuesEqual(value, goal)) {
      const scored = dedupe ? canonicalize(expr, values) : expr;
      const text = displayExpression(scored, values);
      if (!seen.has(text)) {
        seen.add(text);
        solutions.push({
          expression: text,
          value,
          program: {
            valueOrder: scored.valueOrder.slice(),
            operations: scored.operations.slice(),
            order: scored.order.slice(),
          },
        });
        if (Number.isFinite(maxSolutions) && solutions.length >= maxSolutions) {
          aborted = true;
          break;
        }
      }
    }
    if (onProgress && i % reportEvery === 0) {
      onProgress({
        type: "solve-progress",
        evaluated: i,
        total,
        found: solutions.length,
        fraction: i / total,
      });
    }
  }

  if (!aborted && solutions.length > 0) {
    const rating = rate(solutions.map((solution) => solution.program), values, goal);
    solutions.difficulty = rating.difficulty;
    solutions.ideaCount = rating.ideaCount;
  }
  return solutions;
}

/**
 * Random search for value boards that hit the goal with solution count in range.
 */
export function findSolvableValues(
  expressionList,
  numValues,
  goal,
  valueRange,
  solutionRange,
  maxAttempts = 1000,
  onProgress
) {
  const [vmin, vmax] = valueRange;
  const [smin, smax] = solutionRange;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const values = Array.from({ length: numValues }, () =>
      Math.floor(Math.random() * (vmax - vmin)) + vmin
    );
    if (onProgress) {
      onProgress({
        type: "find-attempt",
        attempt,
        maxAttempts,
        values,
        message: `Attempt ${attempt}/${maxAttempts}: [${values.join(", ")}]`,
      });
    }
    const solutions = findAllSolutions(expressionList, values, goal, smax + 1);
    if (solutions.length >= smin && solutions.length <= smax) {
      return {
        values,
        solutions,
        attempts: attempt,
        difficulty: solutions.difficulty ?? null,
        ideaCount: solutions.ideaCount ?? 0,
      };
    }
  }
  throw new Error(`No board found within ${maxAttempts} attempts`);
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/** Scale value complexity with n: small n → simple; larger n → wider range (still modest). */
export function randomValueForN(n) {
  if (n <= 2) return randomInt(1, 9);
  if (n === 3) return randomInt(1, 12);
  if (n === 4) return randomInt(1, 20);
  if (n === 5) {
    return Math.random() < 0.75 ? randomInt(1, 40) : randomInt(-50, 100);
  }
  // n >= 6: -99 … 99, including negatives
  const roll = Math.random();
  if (roll < 0.5) return randomInt(1, 99);
  if (roll < 0.9) return randomInt(-99, -1);
  return 0;
}

export function randomValuesForN(n) {
  return Array.from({ length: n }, () => randomValueForN(n));
}

/** Random whole-number goal, scaled with n (not chosen from solutions). Cap goal in [-999, 999]. */
export function randomGoalForN(n) {
  if (n <= 2) return randomInt(1, 30);
  if (n === 3) return randomInt(-20, 50);
  if (n === 4) return randomInt(-50, 100);
  if (n === 5) return randomInt(-200, 500);
  // n >= 6
  return randomInt(-999, 999);
}

/**
 * Random board for n: values scaled by n, plus an independent random whole-number goal.
 */
export function makeRandomExample(numValues) {
  return {
    values: randomValuesForN(numValues),
    goal: randomGoalForN(numValues),
  };
}


