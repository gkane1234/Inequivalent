/**
 * Difficulty of one solved line, and of a list of numbers with a goal.
 * Same rules as Java Difficulty. A line costs the sum of its steps, and at
 * least 1. Lines that share an identity key are one idea and the idea keeps
 * its cheapest line. The difficulty is the parallel sum of those ideas, then
 * scaled by how often the input numbers repeat.
 */

import { identitySignature } from "./identity-dedupe.js";

const ADD = 0;
const SUB = 1;
const MUL = 2;
const DIV = 3;

/** Numeric factors. The functions below are the editable parts of the score. */
export const parameters = {
  startingNumberFactor: 0.5,
  goalFactor: 0.5,
  addCost: 1,
  subtractCost: 1,
  multiplyCost: 1,
  divideCost: 1,
  subtractLeft: 1,
  subtractRight: 1,
  divideLeft: 1,
  divideRight: 1,
};

const UNIT_TOLERANCE = 1e-9;
const LOG_2 = Math.log(2);

export function solutionCost(expression, values) {
  const stack = [];
  let valuesPointer = 0;
  let operationsPointer = 0;
  let total = 0;
  for (const isNumber of expression.order) {
    if (isNumber) {
      stack.push(leafItem(values[expression.valueOrder[valuesPointer++]]));
    } else {
      const right = stack.pop();
      const left = stack.pop();
      if (!left.value || !right.value) return Infinity;
      const op = expression.operations[operationsPointer++];
      const result = apply(op, left.value, right.value);
      if (!result) return Infinity;
      total += combineOperands(
        operandCost(left, op, true),
        operandCost(right, op, false)
      );
      stack.push({ value: result, computed: true });
    }
  }
  if (stack.length !== 1) throw new Error("Invalid expression");
  return total;
}

export function solutionDifficulty(expression, values, goal) {
  const steps = solutionCost(expression, values);
  if (steps === Infinity) return Infinity;
  return floorCost(lineCost(steps, goalTerm(goal)));
}

/**
 * @returns {{ difficulty: number, ideaCount: number, lineCount: number }}
 * difficulty is NaN when there are no lines.
 */
export function rate(lines, values, goal) {
  const best = new Map();
  for (const line of lines) {
    const key = identitySignature(line, values);
    const difficulty = solutionDifficulty(line, values, goal);
    const previous = best.get(key);
    if (previous == null || difficulty < previous) best.set(key, difficulty);
  }
  if (best.size === 0) return { difficulty: NaN, ideaCount: 0, lineCount: 0 };
  let difficulty = combineIdeas([...best.values()]);
  if (Number.isFinite(difficulty)) difficulty = finish(difficulty, repeatedNumbers(values));
  return {
    difficulty,
    ideaCount: best.size,
    lineCount: lines.length,
  };
}

/**
 * A value seen once weighs 2. A value seen k times weighs half of 1 + 1/k.
 * The factor is the sum of those weights. Four different numbers give 8.
 */
export function repeatFactor(values) {
  return repeatedNumbers(values);
}

function magnitude(value) {
  return log2OnePlusAbs(value.p);
  // return Math.abs(value.p);
}

function negativeFactor(value) {
  return 1;
  // return 2;
}

function fractionFactor(value) {
  return 2.5 * log2Abs(value.q);
  // return 1;
}

function valueCost(value) {
  if (!value || value.p === 0) return 0;
  let cost = magnitude(value);
  if (value.p < 0) cost *= negativeFactor(value);
  if (value.q !== 1) cost *= fractionFactor(value);
  return cost;
}

function combineOperands(first, second) {
  return first + second;
}

function lineCost(steps, goal) {
  return steps + goal;
}

function floorCost(cost) {
  return Math.max(1, cost);
  // return cost;
}

function combineIdeas(difficulties) {
  let reciprocal = 0;
  for (const difficulty of difficulties) reciprocal += 1 / difficulty;
  return reciprocal === 0 ? Infinity : 1 / reciprocal;
}

function repeatedNumbers(values) {
  const n = values.length;
  if (n === 0) return 1;
  const sorted = values.slice().sort((a, b) => a - b);
  let weight = 0;
  let count = 1;
  for (let i = 1; i <= n; i++) {
    if (i < n && sameNumber(sorted[i], sorted[i - 1])) count++;
    else {
      weight += count === 1 ? 2 : 0.5 * (1 + 1 / count);
      count = 1;
    }
  }
  return weight;
}

function finish(ideas, repeated) {
  return ideas * repeated;
}

function goalTerm(goal) {
  if (!Number.isFinite(goal)) return 0;
  const value = ratFromNumber(goal);
  if (!value) return 0;
  return parameters.goalFactor * valueCost(value);
}

function operandCost(item, op, left) {
  const weight = item.computed ? 1 : parameters.startingNumberFactor;
  const scale = sideFactor(op, left);
  return weight * scale * valueCost(item.value);
}

function sameNumber(left, right) {
  return Number.isFinite(left) && Number.isFinite(right) && Math.abs(left - right) <= UNIT_TOLERANCE;
}

export function formatDifficulty(difficulty, ideaCount) {
  if (!Number.isFinite(difficulty) || !(ideaCount > 0)) return "";
  const ideas = ideaCount === 1 ? "1 idea" : `${ideaCount} ideas`;
  return `Difficulty ${difficulty.toFixed(2)} from ${ideas}. Lower is easier.`;
}

function leafItem(number) {
  return { value: ratFromNumber(number), computed: false };
}

function log2OnePlusAbs(value) {
  return Math.log1p(Math.abs(value)) / LOG_2;
}

function log2Abs(value) {
  return Math.log(Math.abs(value)) / LOG_2;
}

function sideFactor(op, left) {
  return operationCost(op) * argumentWeight(op, left);
}

function operationCost(op) {
  if (op === ADD) return parameters.addCost;
  if (op === SUB) return parameters.subtractCost;
  if (op === MUL) return parameters.multiplyCost;
  if (op === DIV) return parameters.divideCost;
  return 1;
}

function argumentWeight(op, left) {
  if (op === SUB) return left ? parameters.subtractLeft : parameters.subtractRight;
  if (op === DIV) return left ? parameters.divideLeft : parameters.divideRight;
  return 1;
}

function apply(op, left, right) {
  if (op === ADD) return left.add(right);
  if (op === SUB) return left.subtract(right);
  if (op === MUL) return left.multiply(right);
  if (op === DIV) return left.divide(right);
  throw new Error(`unknown op ${op}`);
}

function rat(p, q) {
  if (!Number.isSafeInteger(p) || !Number.isSafeInteger(q)) return null;
  if (q < 0) {
    p = -p;
    q = -q;
  }
  if (q === 0) return null;
  const divisor = gcd(Math.abs(p), q);
  return makeRat(p / divisor, q / divisor);
}

function makeRat(p, q) {
  return {
    p,
    q,
    add(other) {
      return rat(p * other.q + other.p * q, q * other.q);
    },
    subtract(other) {
      return rat(p * other.q - other.p * q, q * other.q);
    },
    multiply(other) {
      return rat(p * other.p, q * other.q);
    },
    divide(other) {
      if (other.p === 0) return null;
      return rat(p * other.q, q * other.p);
    },
  };
}

function ratFromNumber(number) {
  if (!Number.isFinite(number)) return null;
  const rounded = Math.round(number);
  if (Math.abs(number - rounded) <= UNIT_TOLERANCE && Math.abs(rounded) < 1e15) {
    return rat(rounded, 1);
  }
  return continuedFraction(number);
}

function continuedFraction(number) {
  const sign = number < 0 ? -1 : 1;
  let rest = Math.abs(number);
  let n0 = 0;
  let d0 = 1;
  let n1 = 1;
  let d1 = 0;
  for (let i = 0; i < 40; i++) {
    const whole = Math.floor(rest + 1e-12);
    const nextN = n1 * whole + n0;
    const nextD = d1 * whole + d0;
    if (!Number.isSafeInteger(nextN) || !Number.isSafeInteger(nextD)) break;
    n0 = n1;
    d0 = d1;
    n1 = nextN;
    d1 = nextD;
    if (d1 !== 0 && Math.abs(n1 / d1 - Math.abs(number)) <= UNIT_TOLERANCE) break;
    const frac = rest - whole;
    if (frac < 1e-12) break;
    rest = 1 / frac;
  }
  return rat(sign * n1, d1 === 0 ? 1 : d1);
}

function gcd(a, b) {
  let x = a;
  let y = b;
  while (y !== 0) {
    const next = x % y;
    x = y;
    y = next;
  }
  return x;
}
