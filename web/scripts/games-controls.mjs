import assert from "node:assert/strict";
import { Expression } from "../lib/expression.js";
import { canonicalDisplay } from "../lib/canonical.js";
import { findAllSolutions } from "../lib/solver.js";
import { solveComSearch } from "../lib/comsearch.js";
import {
  bandForCount,
  countLabel,
  dealCountdown,
  find24Puzzle,
  findCountdownPuzzle,
  matchesBand,
} from "../lib/games.js";

assert.equal(bandForCount(0), null);
assert.equal(bandForCount(1), "hard");
assert.equal(bandForCount(2), "hard");
assert.equal(bandForCount(2.5), "medium");
assert.equal(bandForCount(3), "medium");
assert.equal(bandForCount(7), "medium");
assert.equal(bandForCount(7.5), "medium");
assert.equal(bandForCount(8), "easy");
assert.equal(countLabel(8, "easy"), "8");
assert.equal(countLabel(9, "easy"), "at least 9");
assert.equal(countLabel(4, "medium"), "4");

for (let i = 0; i < 200; i++) {
  const dealt = dealCountdown();
  assert.equal(dealt.values.length, 6);
  assert.ok(dealt.largeCount >= 0 && dealt.largeCount <= 4);
  const counts = new Map();
  for (const value of dealt.values) counts.set(value, (counts.get(value) ?? 0) + 1);
  for (const [value, count] of counts) {
    if (value >= 25) assert.equal(count, 1);
    else assert.ok(count <= 2);
  }
  assert.equal([...counts.keys()].filter((value) => value >= 25).length, dealt.largeCount);
}
const fourLarge = dealCountdown(4);
assert.equal(fourLarge.largeCount, 4);
assert.equal(fourLarge.values.filter((value) => value >= 25).length, 4);

const values = [3, 3, 7, 7];
const sumFirst = new Expression([0, 1, 2, 3], [3, 0, 2], [true, true, true, false, false, true, false]);
const divFirst = new Expression([0, 2, 1, 3], [3, 0, 2], [true, true, false, true, false, true, false]);
assert.equal(canonicalDisplay(sumFirst, values), canonicalDisplay(divFirst, values));

const list = {
  numExpressions: 2,
  get(index) {
    return index === 0 ? sumFirst : divFirst;
  },
};
const raw = findAllSolutions(list, values, 24, 10);
const deduped = findAllSolutions(list, values, 24, 10, null, { dedupeRepeated: true });
assert.equal(raw.length, 2);
assert.equal(deduped.length, 1);
assert.equal(deduped[0].value, 24);

function solve(board, goal, maxSolutions) {
  return Promise.resolve(
    solveComSearch(board, goal, maxSolutions, null, null, null, { dedupeRepeated: true }).solutions
  );
}

const repeated = await solve([3, 3, 7, 7], 24, 20);
assert.equal(repeated.length, 1);
assert.equal(bandForCount(repeated.length), "hard");
const repeatedRaw = solveComSearch([3, 3, 7, 7], 24, 20).solutions.length;
assert.ok(repeatedRaw > repeated.length);

for (const band of ["easy", "medium", "hard"]) {
  const puzzle = await find24Puzzle(solve, band);
  assert.equal(puzzle.values.length, 4);
  assert.equal(puzzle.goal, 24);
  assert.ok(matchesBand(puzzle.solutions.length, band), `${band} got ${puzzle.solutions.length}`);
  assert.ok(puzzle.solutions.every((solution) => solution.value === 24));
  console.log(`${band}: ${puzzle.values.join(", ")} → ${puzzle.countText}`);
}

const countdown = await findCountdownPuzzle(solve);
assert.equal(countdown.values.length, 6);
assert.ok(countdown.goal >= 100 && countdown.goal <= 999);
assert.equal(countdown.answer.value, countdown.goal);
console.log(
  `countdown: ${countdown.values.join(", ")} → ${countdown.goal} = ${countdown.answer.expression}`
);
