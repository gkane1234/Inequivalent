import { Expression, displayExpression } from "./expression.js";

const ADD = 0;
const MUL = 2;

/**
 * Reorder sums and products by the display text of their parts, then format.
 * Subtraction and division keep operand order. Same idea as ComSearch dedupe,
 * so repeated numbers collapse to one line on the expression-list solver too.
 */
export function canonicalDisplay(expression, values) {
  return displayExpression(canonicalize(expression, values), values);
}

export function canonicalize(expression, values) {
  return toExpression(reorder(parse(expression), values));
}

function parse(expression) {
  const stack = [];
  let valuesPointer = 0;
  let operationsPointer = 0;
  for (const isNumber of expression.order) {
    if (isNumber) {
      stack.push({ leaf: true, varIndex: expression.valueOrder[valuesPointer++] });
    } else {
      const right = stack.pop();
      const left = stack.pop();
      stack.push({
        leaf: false,
        op: expression.operations[operationsPointer++],
        left,
        right,
      });
    }
  }
  if (stack.length !== 1) throw new Error("Invalid expression");
  return stack[0];
}

function reorder(node, values) {
  if (node.leaf) return node;
  const left = reorder(node.left, values);
  const right = reorder(node.right, values);
  if (node.op !== ADD && node.op !== MUL) {
    if (left === node.left && right === node.right) return node;
    return { leaf: false, op: node.op, left, right };
  }
  const parts = [];
  flatten(left, node.op, parts);
  flatten(right, node.op, parts);
  parts.sort((a, b) => {
    const ka = displayExpression(toExpression(a), values);
    const kb = displayExpression(toExpression(b), values);
    if (ka < kb) return -1;
    if (ka > kb) return 1;
    return 0;
  });
  let folded = parts[0];
  for (let i = 1; i < parts.length; i++) {
    folded = { leaf: false, op: node.op, left: folded, right: parts[i] };
  }
  return folded;
}

function flatten(node, op, out) {
  if (!node.leaf && node.op === op) {
    flatten(node.left, op, out);
    flatten(node.right, op, out);
  } else {
    out.push(node);
  }
}

function toExpression(node) {
  const valueOrder = [];
  const operations = [];
  const order = [];
  function write(current) {
    if (current.leaf) {
      valueOrder.push(current.varIndex);
      order.push(true);
      return;
    }
    write(current.left);
    write(current.right);
    operations.push(current.op);
    order.push(false);
  }
  write(node);
  return new Expression(valueOrder, operations, order);
}
