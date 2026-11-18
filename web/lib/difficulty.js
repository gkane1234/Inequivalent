/**
 * Difficulty of one solved line, and of a deal.
 * Same rules as Java Difficulty. A line costs the sum of its steps, and at
 * least 1. Lines that share an identity key are one idea and the idea keeps
 * its cheapest line. The deal is the parallel sum of those ideas, then
 * scaled by how often the dealt numbers repeat.
 */

import { identitySignature } from "./identity-dedupe.js";

const ADD = 0;
const SUB = 1;
const MUL = 2;
const DIV = 3;

export const LAMBDA = 2.5;
export const FAMILIAR_WEIGHT = 0.5;
export const MIN_SOLUTION_DIFFICULTY = 1;

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
      const result = apply(expression.operations[operationsPointer++], left.value, right.value);
      if (!result) return Infinity;
      total +=
        numberCost(left.value, !left.computed) +
        numberCost(right.value, !right.computed) +
        numberCost(result, true);
      stack.push({ value: result, computed: true });
    }
  }
  if (stack.length !== 1) throw new Error("Invalid expression");
  return total;
}

export function solutionDifficulty(expression, values) {
  return Math.max(MIN_SOLUTION_DIFFICULTY, solutionCost(expression, values));
}

/**
 * @returns {{ difficulty: number, ideaCount: number, lineCount: number }}
 * difficulty is NaN when there are no lines.
 */
export function rate(lines, values) {
  const best = new Map();
  for (const line of lines) {
    const key = identitySignature(line, values);
    const difficulty = solutionDifficulty(line, values);
    const previous = best.get(key);
    if (previous == null || difficulty < previous) best.set(key, difficulty);
  }
  if (best.size === 0) return { difficulty: NaN, ideaCount: 0, lineCount: 0 };
  let reciprocal = 0;
  for (const difficulty of best.values()) reciprocal += 1 / difficulty;
  let difficulty = reciprocal === 0 ? Infinity : 1 / reciprocal;
  if (Number.isFinite(difficulty)) difficulty *= repeatFactor(values);
  return {
    difficulty,
    ideaCount: best.size,
    lineCount: lines.length,
  };
}

/**
 * A value seen once weighs 1. A value seen k times weighs 1 + 1/k.
 * Divide by the number of cards so an all-different hand stays at 1.
 */
export function repeatFactor(values) {
  const n = values.length;
  if (n === 0) return 1;
  const sorted = values.slice().sort((a, b) => a - b);
  let weight = 0;
  let count = 1;
  for (let i = 1; i <= n; i++) {
    if (i < n && sameCard(sorted[i], sorted[i - 1])) count++;
    else {
      weight += count === 1 ? 1 : 1 + 1 / count;
      count = 1;
    }
  }
  return weight / n;
}

function sameCard(left, right) {
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

function numberCost(value, familiar) {
  if (value.p === 0n) return 0;
  const weight = familiar ? FAMILIAR_WEIGHT : 1;
  const size = weight * log1pAbs(value.p);
  const fraction = value.q === 1n ? 0 : LAMBDA * logAbs(value.q);
  return size + fraction;
}

function log1pAbs(value) {
  const magnitude = value < 0n ? -value : value;
  if (magnitude < 2n ** 52n) return Math.log(1 + Number(magnitude));
  return logAbs(magnitude);
}

function logAbs(value) {
  const magnitude = value < 0n ? -value : value;
  if (magnitude === 0n) return -Infinity;
  const bits = magnitude.toString(2).length;
  if (bits <= 52) return Math.log(Number(magnitude));
  const shift = bits - 52;
  const mantissa = Number(magnitude >> BigInt(shift));
  return Math.log(mantissa) + shift * LOG_2;
}

function apply(op, left, right) {
  if (op === ADD) return left.add(right);
  if (op === SUB) return left.subtract(right);
  if (op === MUL) return left.multiply(right);
  if (op === DIV) return left.divide(right);
  throw new Error(`unknown op ${op}`);
}

function rat(p, q) {
  if (q < 0n) {
    p = -p;
    q = -q;
  }
  if (q === 0n) return null;
  const divisor = gcd(p < 0n ? -p : p, q);
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
      if (other.p === 0n) return null;
      return rat(p * other.q, q * other.p);
    },
  };
}

function ratFromNumber(number) {
  if (!Number.isFinite(number)) return null;
  const rounded = Math.round(number);
  if (Math.abs(number - rounded) <= UNIT_TOLERANCE && Math.abs(rounded) < 1e15) {
    return rat(BigInt(rounded), 1n);
  }
  return continuedFraction(number);
}

function continuedFraction(number) {
  const sign = number < 0 ? -1n : 1n;
  let rest = Math.abs(number);
  let n0 = 0n;
  let d0 = 1n;
  let n1 = 1n;
  let d1 = 0n;
  for (let i = 0; i < 40; i++) {
    const whole = BigInt(Math.floor(rest + 1e-12));
    const nextN = n1 * whole + n0;
    const nextD = d1 * whole + d0;
    n0 = n1;
    d0 = d1;
    n1 = nextN;
    d1 = nextD;
    if (d1 !== 0n && Math.abs(Number(n1) / Number(d1) - Math.abs(number)) <= UNIT_TOLERANCE) break;
    const frac = rest - Number(whole);
    if (frac < 1e-12) break;
    rest = 1 / frac;
  }
  return rat(sign * n1, d1 === 0n ? 1n : d1);
}

function gcd(a, b) {
  let x = a;
  let y = b;
  while (y !== 0n) {
    const next = x % y;
    x = y;
    y = next;
  }
  return x;
}
