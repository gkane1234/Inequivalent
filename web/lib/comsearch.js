import { expectedCount } from "./counter.js";
import { rate } from "./difficulty.js";

/**
 * JavaScript port of ComSearchGenerator.search (serial).
 * Walks inequivalent expressions, evaluates the supplied numbers while
 * combining, and builds a display string only when the caller asks for a hit.
 */

const OP_ADD = 0;
const OP_SUB = 1;
const OP_MUL = 2;
const OP_DIV = 3;
const SYMBOL = ["+", "-", "*", "/"];

const ROUNDING_FACTOR = 1e9;
const TOLERANCE = 1e-5;

function apply(op, a, b) {
  if (op === OP_ADD) return a + b;
  if (op === OP_SUB) return a - b;
  if (op === OP_MUL) return a * b;
  if (op === OP_DIV) return b !== 0 ? a / b : NaN;
  throw new Error(`unknown op ${op}`);
}

function leaf(varIndex, value) {
  return {
    leaf: true,
    var: varIndex,
    value,
    minLeaf: varIndex,
    hasSub: false,
  };
}

function combine(left, right, op) {
  return {
    leaf: false,
    op,
    left,
    right,
    value: apply(op, left.value, right.value),
    minLeaf: Math.min(left.minLeaf, right.minLeaf),
    hasSub: op === OP_SUB || left.hasSub || right.hasSub,
  };
}

function bitSet(mask, bit) {
  return Math.floor(mask / 2 ** bit) % 2 !== 0;
}

function forEachUnorderedPartition(leaves, consumer, isCancelled) {
  if (leaves.length < 2) return;
  const n = leaves.length;
  const blocks = [];
  const tryNext = new Array(n).fill(0);
  const placedIn = new Array(n);
  let i = 0;

  while (i >= 0) {
    if (isCancelled()) return;

    if (i === n) {
      if (blocks.length >= 2) consumer(copyBlocks(blocks));
      i--;
      if (i >= 0) {
        undo(blocks, i, placedIn);
        tryNext[i]++;
      }
      continue;
    }

    const options = blocks.length + 1;
    if (tryNext[i] >= options) {
      tryNext[i] = 0;
      i--;
      if (i >= 0) {
        undo(blocks, i, placedIn);
        tryNext[i]++;
      }
      continue;
    }

    const id = leaves[i];
    const choice = tryNext[i];
    if (choice < blocks.length) {
      blocks[choice].push(id);
      placedIn[i] = choice;
    } else {
      blocks.push([id]);
      placedIn[i] = -1;
    }

    i++;
    if (i < n) tryNext[i] = 0;
  }
}

function undo(blocks, i, placedIn) {
  if (placedIn[i] >= 0) {
    blocks[placedIn[i]].pop();
  } else {
    blocks.pop();
  }
}

function copyBlocks(blocks) {
  return blocks.map((block) => block.slice());
}

function indexOfMinLeaf(children) {
  let minIndex = 0;
  let min = children[0].minLeaf;
  for (let b = 1; b < children.length; b++) {
    if (children[b].minLeaf < min) {
      min = children[b].minLeaf;
      minIndex = b;
    }
  }
  return minIndex;
}

function anyHasSub(children) {
  for (const child of children) {
    if (child.hasSub) return true;
  }
  return false;
}

function foldValue(parts, len, op) {
  let acc = parts[0].value;
  for (let i = 1; i < len; i++) acc = apply(op, acc, parts[i].value);
  return acc;
}

function foldForward(parts, len, op) {
  let acc = parts[0];
  for (let i = 1; i < len; i++) acc = combine(acc, parts[i], op);
  return acc;
}

function flattenCommutative(node, op, out) {
  if (!node.leaf && node.op === op) {
    flattenCommutative(node.left, op, out);
    flattenCommutative(node.right, op, out);
  } else {
    out.push(node);
  }
}

/** Flatten + and * chains and sort those parts by display text. Does not mutate shared nodes. */
function reorderCommutative(node, values) {
  if (node.leaf) return node;
  const left = reorderCommutative(node.left, values);
  const right = reorderCommutative(node.right, values);
  if (node.op !== OP_ADD && node.op !== OP_MUL) {
    if (left === node.left && right === node.right) return node;
    return combine(left, right, node.op);
  }
  const parts = [];
  flattenCommutative(left, node.op, parts);
  flattenCommutative(right, node.op, parts);
  parts.sort((a, b) => {
    const ka = formatTree(a, values);
    const kb = formatTree(b, values);
    if (ka < kb) return -1;
    if (ka > kb) return 1;
    return 0;
  });
  return foldForward(parts, parts.length, node.op);
}

function sortByMinLeaf(arr, len) {
  for (let i = 1; i < len; i++) {
    const key = arr[i];
    const keyMin = key.minLeaf;
    let j = i - 1;
    while (j >= 0 && arr[j].minLeaf > keyMin) {
      arr[j + 1] = arr[j];
      j--;
    }
    arr[j + 1] = key;
  }
}

function canAbsorbNegation(node) {
  if (node.leaf) return false;
  if (node.op === OP_SUB) return true;
  return canAbsorbNegation(node.left) || canAbsorbNegation(node.right);
}

function negateNode(node) {
  if (node.leaf) throw new Error("Cannot negate a bare leaf");
  if (node.op === OP_SUB) return combine(node.right, node.left, OP_SUB);
  if (node.op === OP_ADD) {
    if (canAbsorbNegation(node.right)) return combine(negateNode(node.right), node.left, OP_SUB);
    if (canAbsorbNegation(node.left)) return combine(negateNode(node.left), node.right, OP_SUB);
    throw new Error("Cannot negate sum without a subtraction inside");
  }
  if (canAbsorbNegation(node.left)) return combine(negateNode(node.left), node.right, node.op);
  if (canAbsorbNegation(node.right)) return combine(node.left, negateNode(node.right), node.op);
  throw new Error("Cannot negate expression");
}

function precOf(node) {
  if (node.leaf) return 0;
  return node.op === OP_ADD || node.op === OP_SUB ? 1 : 2;
}

function needsParens(child, parentPrec, isLeft) {
  const childPrec = precOf(child);
  if (childPrec === 0) return false;
  if (childPrec < parentPrec) return true;
  if (childPrec > parentPrec) return false;
  return !isLeft;
}

function formatLeaf(next) {
  if (next === Math.round(next)) return String(Math.round(next));
  return next.toFixed(3);
}

function formatTree(node, values) {
  if (node.leaf) return formatLeaf(values[node.var]);
  const prec = precOf(node);
  const left = needsParens(node.left, prec, true)
    ? `(${formatTree(node.left, values)})`
    : formatTree(node.left, values);
  const right = needsParens(node.right, prec, false)
    ? `(${formatTree(node.right, values)})`
    : formatTree(node.right, values);
  return left + SYMBOL[node.op] + right;
}

function splitAdditive(children, minIndex, freeMask) {
  const g = children.length;
  const pos = new Array(g);
  const neg = new Array(g);
  let posN = 0;
  let negN = 0;
  pos[posN++] = children[minIndex];
  let bit = 0;
  for (let b = 0; b < g; b++) {
    if (b === minIndex) continue;
    if (bitSet(freeMask, bit)) pos[posN++] = children[b];
    else neg[negN++] = children[b];
    bit++;
  }
  sortByMinLeaf(pos, posN);
  sortByMinLeaf(neg, negN);
  return { pos, posN, neg, negN };
}

function buildAdditive(children, minIndex, freeMask) {
  const { pos, posN, neg, negN } = splitAdditive(children, minIndex, freeMask);
  const left = foldForward(pos, posN, OP_ADD);
  if (negN === 0) return left;
  return combine(left, foldForward(neg, negN, OP_ADD), OP_SUB);
}

function splitMultiplicative(children, mask) {
  const g = children.length;
  const numer = new Array(g);
  const denom = new Array(g);
  let numerN = 0;
  let denomN = 0;
  for (let b = 0; b < g; b++) {
    if (bitSet(mask, b)) numer[numerN++] = children[b];
    else denom[denomN++] = children[b];
  }
  sortByMinLeaf(numer, numerN);
  sortByMinLeaf(denom, denomN);
  return { numer, numerN, denom, denomN };
}

function buildMultiplicative(children, mask) {
  const { numer, numerN, denom, denomN } = splitMultiplicative(children, mask);
  const left = foldForward(numer, numerN, OP_MUL);
  if (denomN === 0) return left;
  return combine(left, foldForward(denom, denomN, OP_MUL), OP_DIV);
}

function round9(value) {
  if (!Number.isFinite(value)) return value;
  return Math.round(value * ROUNDING_FACTOR) / ROUNDING_FACTOR;
}

function valuesEqual(a, b) {
  return Math.abs(a - b) <= TOLERANCE;
}

/**
 * @param {number[]} values
 * @param {number} goal
 * @param {number} maxSolutions
 * @param {(p: {checked: number, total: number, found: number, done?: boolean}) => void} [onProgress]
 * @param {(solution: {expression: string, value: number}) => void} [onSolution]
 * @param {() => boolean} [shouldStop] checked between steps so a caller can interrupt
 * @param {{ dedupeRepeated?: boolean }} [options] when dedupeRepeated is set, reorder sums and products by text and keep one copy of each line
 */
export function solveComSearch(values, goal, maxSolutions, onProgress, onSolution, shouldStop, options) {
  const n = values.length;
  const total = expectedCount(n);
  const dedupe = Boolean(options && options.dedupeRepeated);
  const seen = dedupe ? new Set() : null;
  const numericLimit = Number(maxSolutions);
  const limit = Number.isFinite(numericLimit) && numericLimit >= 1 ? Math.floor(numericLimit) : Infinity;
  const solutions = [];
  let checked = 0;
  let cancelled = false;
  let aborted = false;
  const reportEvery = n >= 7 ? 20000 : n >= 6 ? 5000 : 1000;

  const leafNodes = values.map((value, i) => leaf(i, value));
  const isCancelled = () => cancelled || Boolean(shouldStop && shouldStop());

  function report(done = false) {
    if (!onProgress) return;
    if (!done && checked % reportEvery !== 0) return;
    onProgress({ checked, total, found: solutions.length, done });
  }

  function consider(raw, materialize) {
    if (isCancelled()) {
      aborted = true;
      cancelled = true;
      return;
    }
    if (solutions.length >= limit) {
      cancelled = true;
      aborted = true;
      return;
    }
    checked++;
    report(false);
    const value = round9(raw);
    if (valuesEqual(value, goal)) {
      const tree = materialize();
      const kept = dedupe ? reorderCommutative(tree, values) : tree;
      const expression = formatTree(kept, values);
      if (seen) {
        if (seen.has(expression)) return;
        seen.add(expression);
      }
      const solution = { expression, value, program: toProgram(kept) };
      solutions.push(solution);
      if (onSolution) onSolution(solution);
      if (solutions.length >= limit) {
        cancelled = true;
        aborted = true;
      }
    }
  }

  function sink(raw, hasSub, materialize, materializeOpposite) {
    if (isCancelled()) return;
    consider(raw, materialize);
    if (!cancelled && hasSub) consider(-raw, materializeOpposite);
  }

  function leafV(varIndex) {
    return leafNodes[varIndex];
  }

  function combineAdditiveToSink(children) {
    if (isCancelled()) return;
    const g = children.length;
    const minIndex = indexOfMinLeaf(children);
    const childHasSub = anyHasSub(children);
    const freeLimit = 2 ** (g - 1);
    const snapshot = children.slice();
    for (let freeMask = 0; freeMask < freeLimit; freeMask++) {
      if (isCancelled()) return;
      const { pos, posN, neg, negN } = splitAdditive(snapshot, minIndex, freeMask);
      let value = foldValue(pos, posN, OP_ADD);
      if (negN > 0) value -= foldValue(neg, negN, OP_ADD);
      const hasSub = childHasSub || negN > 0;
      sink(
        value,
        hasSub,
        () => buildAdditive(snapshot, minIndex, freeMask),
        () => negateNode(buildAdditive(snapshot, minIndex, freeMask))
      );
    }
  }

  function combineMultiplicativeToSink(children) {
    if (isCancelled()) return;
    const g = children.length;
    const childHasSub = anyHasSub(children);
    const maskLimit = 2 ** g;
    const snapshot = children.slice();
    for (let mask = 1; mask < maskLimit; mask++) {
      if (isCancelled()) return;
      const { numer, numerN, denom, denomN } = splitMultiplicative(snapshot, mask);
      let value = foldValue(numer, numerN, OP_MUL);
      if (denomN > 0) {
        const d = foldValue(denom, denomN, OP_MUL);
        value = d !== 0 ? value / d : NaN;
      }
      sink(
        value,
        childHasSub,
        () => buildMultiplicative(snapshot, mask),
        () => negateNode(buildMultiplicative(snapshot, mask))
      );
    }
  }

  function combineAdditive(children, out) {
    if (isCancelled()) return;
    const g = children.length;
    const minIndex = indexOfMinLeaf(children);
    const freeLimit = 2 ** (g - 1);
    for (let freeMask = 0; freeMask < freeLimit; freeMask++) {
      if (isCancelled()) return;
      const { pos, posN, neg, negN } = splitAdditive(children, minIndex, freeMask);
      const left = foldForward(pos, posN, OP_ADD);
      if (negN === 0) out(left);
      else out(combine(left, foldForward(neg, negN, OP_ADD), OP_SUB));
    }
  }

  function combineMultiplicative(children, out) {
    if (isCancelled()) return;
    const g = children.length;
    const maskLimit = 2 ** g;
    for (let mask = 1; mask < maskLimit; mask++) {
      if (isCancelled()) return;
      const { numer, numerN, denom, denomN } = splitMultiplicative(children, mask);
      const left = foldForward(numer, numerN, OP_MUL);
      if (denomN === 0) out(left);
      else out(combine(left, foldForward(denom, denomN, OP_MUL), OP_DIV));
    }
  }

  function chooseChildren(index, partition, additive, chosen, onComplete) {
    if (isCancelled()) return;
    if (index === partition.length) {
      onComplete();
      return;
    }
    const block = partition[index];
    const onChild = (child) => {
      if (isCancelled()) return;
      chosen[index] = child;
      chooseChildren(index + 1, partition, additive, chosen, onComplete);
    };
    if (additive) genMul(block, onChild);
    else genAdd(block, onChild);
  }

  function runRootJob(additive, partition, out) {
    if (isCancelled()) return;
    const chosen = new Array(partition.length);
    chooseChildren(0, partition, additive, chosen, () => {
      if (additive) combineAdditive(chosen, out);
      else combineMultiplicative(chosen, out);
    });
  }

  function runRootJobSearch(additive, partition) {
    if (isCancelled()) return;
    const chosen = new Array(partition.length);
    chooseChildren(0, partition, additive, chosen, () => {
      if (additive) combineAdditiveToSink(chosen);
      else combineMultiplicativeToSink(chosen);
    });
  }

  function genAdd(leaves, out) {
    if (isCancelled()) return;
    if (leaves.length === 1) {
      out(leafV(leaves[0]));
      return;
    }
    forEachUnorderedPartition(leaves, (partition) => runRootJob(true, partition, out), isCancelled);
  }

  function genMul(leaves, out) {
    if (isCancelled()) return;
    if (leaves.length === 1) {
      out(leafV(leaves[0]));
      return;
    }
    forEachUnorderedPartition(leaves, (partition) => runRootJob(false, partition, out), isCancelled);
  }

  if (n === 1) {
    consider(leafNodes[0].value, () => leafNodes[0]);
  } else {
    const leaves = values.map((_, i) => i);
    forEachUnorderedPartition(leaves, (partition) => runRootJobSearch(true, partition), isCancelled);
    if (!cancelled) {
      forEachUnorderedPartition(leaves, (partition) => runRootJobSearch(false, partition), isCancelled);
    }
  }

  report(true);
  const stoppedEarly = aborted || cancelled || Boolean(shouldStop && shouldStop());
  const rating = !stoppedEarly && solutions.length > 0 ? rate(solutions.map((solution) => solution.program), values) : null;
  return {
    solutions,
    checked,
    total,
    difficulty: rating ? rating.difficulty : null,
    ideaCount: rating ? rating.ideaCount : 0,
  };
}

function toProgram(node) {
  const valueOrder = [];
  const operations = [];
  const order = [];
  function write(current) {
    if (current.leaf) {
      valueOrder.push(current.var);
      order.push(true);
      return;
    }
    write(current.left);
    write(current.right);
    operations.push(current.op);
    order.push(false);
  }
  write(node);
  return { valueOrder, operations, order };
}
