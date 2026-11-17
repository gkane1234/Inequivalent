/**
 * Group solved lines that differ only by where a 0 or a 1 is parked.
 * Same rules as Java IdentityDedupe. The lines shown to the player stay
 * as they are; this key is for counting ideas.
 */

const ADD = 0;
const SUB = 1;
const MUL = 2;
const DIV = 3;
const UNIT_TOLERANCE = 1e-9;

export function identitySignature(expression, values) {
  const root = evaluate(expression, values);
  const core = root.core == null ? canon(root.tree, values) : canon(root.core, values);
  if (root.stripped.length === 0) return core;
  const dropped = root.stripped.map((node) => canon(node, values)).sort();
  return [core, ...dropped].join(" || ");
}

function evaluate(expression, values) {
  const stack = [];
  let valuesPointer = 0;
  let operationsPointer = 0;
  for (const isNumber of expression.order) {
    if (isNumber) {
      const varIndex = expression.valueOrder[valuesPointer++];
      stack.push(leaf(varIndex, values[varIndex]));
    } else {
      const right = stack.pop();
      const left = stack.pop();
      stack.push(combine(expression.operations[operationsPointer++], left, right));
    }
  }
  if (stack.length !== 1) throw new Error("Invalid expression");
  return stack[0];
}

function leaf(varIndex, value) {
  const tree = { leaf: true, varIndex };
  return {
    value,
    tree,
    core: isUnit(value) ? null : tree,
    stripped: [],
  };
}

function combine(op, left, right) {
  if (isIdentityOperand(op, right.value, false)) return strip(left, right);
  if (isIdentityOperand(op, left.value, true)) return strip(right, left);
  return bake(op, left, right);
}

function strip(survivor, identity) {
  return {
    value: survivor.value,
    core: survivor.core,
    tree: survivor.tree,
    stripped: survivor.stripped.concat([identity.tree], identity.stripped),
  };
}

function bake(op, left, right) {
  const core = { leaf: false, op, left: material(left), right: material(right) };
  const value = apply(op, left.value, right.value);
  const stripped = left.stripped.concat(right.stripped);
  if (isUnit(value)) {
    return { value, tree: core, core: null, stripped };
  }
  return { value, tree: core, core, stripped };
}

function material(part) {
  return part.core == null ? part.tree : part.core;
}

function isIdentityOperand(op, value, onLeft) {
  if (onLeft) return (op === MUL && isOne(value)) || (op === ADD && isZero(value));
  return (
    (op === MUL && isOne(value)) ||
    (op === DIV && isOne(value)) ||
    ((op === ADD || op === SUB) && isZero(value))
  );
}

function isUnit(value) {
  return isZero(value) || isOne(value);
}

function isZero(value) {
  return Number.isFinite(value) && Math.abs(value) <= UNIT_TOLERANCE;
}

function isOne(value) {
  return Number.isFinite(value) && Math.abs(value - 1) <= UNIT_TOLERANCE;
}

function apply(op, a, b) {
  if (op === ADD) return a + b;
  if (op === SUB) return a - b;
  if (op === MUL) return a * b;
  if (op === DIV) return b === 0 ? NaN : a / b;
  throw new Error(`unknown op ${op}`);
}

function canon(node, values) {
  if (node.leaf) return formatValue(values[node.varIndex]);
  if (node.op === ADD || node.op === MUL) {
    const parts = [];
    flatten(node, node.op, parts);
    const keys = parts.map((part) => canon(part, values)).sort();
    return `(${keys.join(node.op === ADD ? "+" : "*")})`;
  }
  return `(${canon(node.left, values)}${symbol(node.op)}${canon(node.right, values)})`;
}

function flatten(node, op, out) {
  if (!node.leaf && node.op === op) {
    flatten(node.left, op, out);
    flatten(node.right, op, out);
  } else {
    out.push(node);
  }
}

function symbol(op) {
  if (op === ADD) return "+";
  if (op === SUB) return "-";
  if (op === MUL) return "*";
  return "/";
}

function formatValue(value) {
  if (!Number.isFinite(value)) return "nan";
  const rounded = Math.round(value);
  if (Math.abs(value - rounded) <= UNIT_TOLERANCE && Math.abs(rounded) < 1e15) {
    return String(rounded);
  }
  return String(value);
}
