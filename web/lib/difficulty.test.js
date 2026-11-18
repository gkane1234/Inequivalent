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

test("four different cards multiply by 4, and repeats weigh 1 + 1/k", () => {
  assert.equal(repeatFactor([9, 8, 7, 1]), 4);
  assert.ok(Math.abs(repeatFactor([6, 6, 6, 6]) - 5 / 4) < 1e-12);
  assert.ok(Math.abs(repeatFactor([8, 3, 3, 3]) - (1 + 4 / 3)) < 1e-12);
  assert.ok(Math.abs(repeatFactor([7, 3, 7, 3]) - (3 / 2 + 3 / 2)) < 1e-12);
});

test("single steps follow the examples", () => {
  const step = (values, op) => solutionCost(rpn([0, 1], [op], [true, true, false]), values);
  assert.ok(step([0, 7], DIV) < step([2, 1], DIV));
  assert.ok(step([2, 1], DIV) < step([6, 1], ADD));
  assert.ok(step([8, 3], MUL) < step([53, 27], SUB));
  assert.ok(step([53, 27], SUB) < step([7, 3], DIV));
  assert.ok(step([7, 3], DIV) < step([17, 19], MUL));
  assert.ok(Math.abs(step([6, 1], ADD) - 2.3592494356) < 1e-6);
  assert.ok(Math.abs(step([7, 3], DIV) - 5.5191194439) < 1e-6);
});

test("fraction paths match the sketch", () => {
  const fraction = solutionCost(
    rpn([1, 3, 0, 2], [DIV, SUB, MUL], [true, true, true, false, false, true, false]),
    [5, 5, 5, 1]
  );
  const sixOver = solutionCost(
    rpn([3, 0, 1, 2], [DIV, SUB, DIV], [true, true, true, true, false, false, false]),
    [1, 3, 4, 6]
  );
  const eightOver = solutionCost(
    rpn([3, 1, 2, 0], [DIV, SUB, DIV], [true, true, true, true, false, false, false]),
    [3, 3, 8, 8]
  );
  assert.ok(Math.abs(fraction - 26.606064339) < 1e-6);
  assert.ok(Math.abs(sixOver - 21.4089386377) < 1e-6);
  assert.ok(Math.abs(eightOver - 20.5146373744) < 1e-6);
});

test("a zero-cost line floors at 1, and a repeated line counts once", () => {
  const zero = rpn([0, 1], [ADD], [true, true, false]);
  const zeros = [0, 0];
  assert.equal(solutionCost(zero, zeros), 0);
  assert.equal(rate([zero], zeros).difficulty, 1.5);
  const product = rpn([0, 1], [MUL], [true, true, false]);
  assert.equal(rate([zero, product], zeros).difficulty, 0.75);

  const six = rpn([0, 1], [ADD], [true, true, false]);
  const rated = rate([six, six], [6, 1]);
  assert.equal(rated.ideaCount, 1);
  assert.equal(rated.lineCount, 2);
  assert.ok(Math.abs(rated.difficulty - solutionCost(six, [6, 1]) * 2) < 1e-9);
});

test("solved deals keep the spare-1 collapse and the agreed order", () => {
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
  assert.ok(Math.abs(fraction.difficulty - (26.606064339 * 7) / 3) < 0.05);
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
