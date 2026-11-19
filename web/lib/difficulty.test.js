import assert from "node:assert/strict";
import test from "node:test";
import { solutionCost, rate, repeatFactor } from "./difficulty.js";
import { solveComSearch } from "./comsearch.js";

const ADD = 0;
const SUB = 1;
const MUL = 2;
const DIV = 3;

function rpn(vars, ops, order) {
  return { valueOrder: vars, operations: ops, order };
}

test("singletons weigh 2 and repeats weigh half of 1 + 1/k", () => {
  assert.equal(repeatFactor([9, 8, 7, 1]), 8);
  assert.ok(Math.abs(repeatFactor([6, 6, 6, 6]) - 5 / 8) < 1e-12);
  assert.ok(Math.abs(repeatFactor([8, 3, 3, 3]) - 8 / 3) < 1e-12);
  assert.ok(Math.abs(repeatFactor([7, 3, 7, 3]) - 3 / 2) < 1e-12);
});

test("one step charges the two operands", () => {
  const step = (values, op) => solutionCost(rpn([0, 1], [op], [true, true, false]), values);
  const log2 = (value) => Math.log(value) / Math.log(2);
  assert.ok(Math.abs(step([8, 3], MUL) - 0.5 * (log2(9) + log2(4))) < 1e-9);
  assert.equal(step([0, 0], ADD), 0);
});

test("a zero-cost line floors at 1, and a repeated line counts once", () => {
  const zero = rpn([0, 1], [ADD], [true, true, false]);
  const zeros = [0, 0];
  assert.equal(solutionCost(zero, zeros), 0);
  assert.equal(rate([zero], zeros, 0).difficulty, 0.75);
  const product = rpn([0, 1], [MUL], [true, true, false]);
  assert.equal(rate([zero, product], zeros, 0).difficulty, 0.375);

  const six = rpn([0, 1], [ADD], [true, true, false]);
  const rated = rate([six, six], [6, 1], 0);
  assert.equal(rated.ideaCount, 1);
  assert.equal(rated.lineCount, 2);
  assert.ok(Math.abs(rated.difficulty - Math.max(1, solutionCost(six, [6, 1])) * 4) < 1e-9);
});

test("solved numbers keep the spare-1 collapse and the agreed order", () => {
  const spare = solveComSearch([9, 8, 7, 1], 24, Infinity, undefined, undefined, undefined, {
    dedupeRepeated: true,
  });
  const sixes = solveComSearch([6, 6, 6, 6], 24, Infinity, undefined, undefined, undefined, {
    dedupeRepeated: true,
  });
  const ordinary = solveComSearch([2, 4, 7, 10], 24, Infinity, undefined, undefined, undefined, {
    dedupeRepeated: true,
  });
  const fraction = solveComSearch([5, 5, 5, 1], 24, Infinity, undefined, undefined, undefined, {
    dedupeRepeated: true,
  });
  assert.equal(spare.ideaCount, 2);
  assert.equal(spare.solutions.length, 15);
  assert.equal(sixes.ideaCount, 2);
  assert.equal(ordinary.ideaCount, ordinary.solutions.length);
  assert.equal(fraction.ideaCount, 1);
  assert.ok(sixes.difficulty < spare.difficulty);
  assert.ok(sixes.difficulty < ordinary.difficulty);
  assert.ok(sixes.difficulty < fraction.difficulty);
  const triple = solveComSearch([3, 3, 3, 8], 24, Infinity, undefined, undefined, undefined, {
    dedupeRepeated: true,
  });
  const pairs = solveComSearch([7, 7, 3, 3], 24, Infinity, undefined, undefined, undefined, {
    dedupeRepeated: true,
  });
  assert.ok(triple.difficulty < pairs.difficulty);
  assert.equal(
    solveComSearch([9, 8, 7, 1], 24, 2, undefined, undefined, undefined, { dedupeRepeated: true }).difficulty,
    null
  );
});
