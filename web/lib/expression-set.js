import { Expression } from "./expression.js";
import { expectedCount } from "./counter.js";

const THRESHOLD = 2;
const FLOAT32 = new Float32Array(1);

function toFloat32(x) {
  FLOAT32[0] = x;
  return FLOAT32[0];
}

/**
 * Inequivalent expression set with truncator filter (Java ExpressionSet).
 */
export class ExpressionSet {
  /**
   * @param {number} numValues
   * @param {number} rounding
   * @param {number} numTruncators
   * @param {() => number} [rng] random in [0,1)
   */
  constructor(numValues, rounding, numTruncators, rng = Math.random) {
    this.numValues = numValues;
    this.rounding = rounding;
    this.numTruncators = numTruncators;
    this.expressions = [];
    this.seen = Array.from({ length: numTruncators }, () => new Set());
    this.truncators = Array.from({ length: numTruncators }, () => {
      const t = new Array(numValues);
      for (let j = 0; j < numValues; j++) {
        t[j] = 2 * rng() * 10 - 10;
      }
      return t;
    });
  }

  get numExpressions() {
    return this.expressions.length;
  }

  get(i) {
    return this.expressions[i];
  }

  forceAdd(expression) {
    this.expressions.push(expression);
  }

  add(expression) {
    // Match Java: TFloatHashSet.add returns true when the float was newly inserted.
    let uniqueTruncators = 0;
    for (let i = 0; i < this.numTruncators; i++) {
      const value = expression.evaluateWithValues(this.truncators[i], this.rounding);
      if (!Number.isNaN(value)) {
        const key = toFloat32(value);
        if (!this.seen[i].has(key)) {
          this.seen[i].add(key);
          uniqueTruncators++;
        }
      }
    }
    if (uniqueTruncators >= THRESHOLD) {
      this.expressions.push(expression);
      return true;
    }
    return false;
  }

  cleanup() {
    this.seen = null;
    this.truncators = null;
    this.numTruncators = 0;
  }

  changeValueOrder(valueOrder) {
    const newExpressions = new Array(this.expressions.length);
    for (let i = 0; i < this.expressions.length; i++) {
      newExpressions[i] = this.expressions[i].changeValueOrder(valueOrder);
    }
    return new PlainExpressionList(newExpressions, this.numValues);
  }
}

/** Remapped list without truncators (Java ExpressionList.changeValueOrder result). */
export class PlainExpressionList {
  constructor(expressions, numValues) {
    this.expressions = expressions;
    this.numValues = numValues;
  }

  get numExpressions() {
    return this.expressions.length;
  }

  get(i) {
    return this.expressions[i];
  }

  changeValueOrder(valueOrder) {
    const newExpressions = new Array(this.expressions.length);
    for (let i = 0; i < this.expressions.length; i++) {
      newExpressions[i] = this.expressions[i].changeValueOrder(valueOrder);
    }
    return new PlainExpressionList(newExpressions, this.numValues);
  }
}

export function emptyCapacityHint(numValues) {
  return expectedCount(numValues);
}
