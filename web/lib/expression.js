import { OPERATIONS, NUM_OPERATION_ORDERINGS, opName } from "./operations.js";

/**
 * RPN expression: valueOrder (variable indices), operations (opcodes),
 * order (true = push value, false = apply next op).
 */
export class Expression {
  constructor(valueOrder, operations, order) {
    this.valueOrder = valueOrder;
    this.operations = operations;
    this.order = order;
  }

  changeValueOrder(valueOrder) {
    const newValues = new Array(valueOrder.length);
    for (let i = 0; i < valueOrder.length; i++) {
      newValues[i] = valueOrder[this.valueOrder[i]];
    }
    return new Expression(newValues, this.operations, this.order);
  }

  evaluateWithValues(values, rounding) {
    const remapped = new Array(this.valueOrder.length);
    for (let i = 0; i < this.valueOrder.length; i++) {
      remapped[i] = values[this.valueOrder[i]];
    }
    return this.#evaluateRpn(remapped, rounding);
  }

  #evaluateRpn(values, rounding) {
    const stack = new Array(this.order.length);
    let sp = 0;
    let vp = 0;
    let op = 0;

    for (let i = 0; i < this.order.length; i++) {
      if (this.order[i]) {
        stack[sp++] = values[vp++];
      } else {
        const b = stack[--sp];
        const a = stack[--sp];
        const result = OPERATIONS[this.operations[op++]].apply(a, b);
        if (Number.isNaN(result)) return result;
        stack[sp++] = result;
      }
    }
    const nonRounded = stack[--sp];
    const factor = 10 ** rounding;
    return Math.round(nonRounded * factor) / factor;
  }

  /** Parenthetical form with variable indices (debug). */
  toString() {
    return displayExpression(this, null);
  }

  static createCombinedExpressions(expression1, expression2) {
    const out = new Array(NUM_OPERATION_ORDERINGS);
    let index = 0;
    for (let opCode = 0; opCode < OPERATIONS.length; opCode++) {
      if (!OPERATIONS[opCode].commutative) {
        out[index++] = combineExpressions(expression1, expression2, opCode);
        out[index++] = combineExpressions(expression2, expression1, opCode);
      } else {
        out[index++] = combineExpressions(expression1, expression2, opCode);
      }
    }
    return out;
  }
}

function combine(arr1, arr2) {
  return arr1.concat(arr2);
}

function combineWithExtraSpot(arr1, arr2) {
  const newArr = arr1.concat(arr2);
  newArr.push(undefined);
  return newArr;
}

function combineExpressions(expr1, expr2, opCode) {
  const newValueOrder = combine(expr1.valueOrder, expr2.valueOrder);
  const newOperations = combineWithExtraSpot(expr1.operations, expr2.operations);
  newOperations[newOperations.length - 1] = opCode;
  const newOrder = combineWithExtraSpot(expr1.order, expr2.order);
  newOrder[newOrder.length - 1] = false;
  return new Expression(newValueOrder, newOperations, newOrder);
}

/**
 * Display with concrete numbers (like Java EvaluatedExpression.display).
 * @param {Expression} expression
 * @param {number[]|null} values if null, shows variable indices
 */
export function displayExpression(expression, values) {
  const stack = [];
  let vp = 0;
  let op = 0;

  for (const isNumber of expression.order) {
    if (isNumber) {
      if (values == null) {
        stack.push(String(expression.valueOrder[vp++]));
      } else {
        stack.push(formatLeafNumber(values[expression.valueOrder[vp++]]));
      }
    } else {
      const b = stack.pop();
      const a = stack.pop();
      stack.push(`(${a}${opName(expression.operations[op++])}${b})`);
    }
  }
  return tidyAdjacentSigns(stack[stack.length - 1]);
}

function formatLeafNumber(next) {
  if (next === Math.round(next)) return String(Math.round(next));
  return next.toFixed(3);
}

/** Turn +-7 into -7 and --45 into +45 (and same for ++ / -+). */
function tidyAdjacentSigns(s) {
  let out = s;
  let prev;
  do {
    prev = out;
    out = out.replaceAll("+-", "-").replaceAll("--", "+").replaceAll("++", "+").replaceAll("-+", "-");
  } while (out !== prev);
  return out;
}
