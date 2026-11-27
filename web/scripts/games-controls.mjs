import assert from "node:assert/strict";
import { Expression } from "../lib/expression.js";
import { canonicalDisplay } from "../lib/canonical.js";
import { findAllSolutions } from "../lib/solver.js";
import { solveComSearch } from "../lib/comsearch.js";
import {
  BANDS,
  DIFFICULTY_BOUNDS,
  bandForDifficulty,
  dealCountdown,
  find24Puzzle,
  findCountdownPuzzle,
  matchesBand,
} from "../lib/games.js";

assert.equal(bandForDifficulty(0), null);
assert.equal(bandForDifficulty(Number.NaN), null);
assert.equal(bandForDifficulty(DIFFICULTY_BOUNDS.easyMax), "easy");
assert.equal(bandForDifficulty(DIFFICULTY_BOUNDS.easyMax + 1e-9), "medium");
assert.equal(bandForDifficulty(DIFFICULTY_BOUNDS.mediumMax), "medium");
assert.equal(bandForDifficulty(DIFFICULTY_BOUNDS.mediumMax + 1e-9), "hard");
assert.equal(bandForDifficulty(DIFFICULTY_BOUNDS.hardMax), "hard");
assert.equal(bandForDifficulty(DIFFICULTY_BOUNDS.hardMax + 1e-9), "hardest");
assert.deepEqual([...BANDS], ["easy", "medium", "hard", "hardest"]);

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
    (() => {
      const result = solveComSearch(board, goal, maxSolutions, null, null, null, { dedupeRepeated: true });
      const solutions = result.solutions;
      solutions.difficulty = result.difficulty;
      solutions.ideaCount = result.ideaCount;
      return solutions;
    })()
  );
}

const repeated = await solve([3, 3, 7, 7], 24, 20);
assert.equal(repeated.length, 1);
assert.ok(Number.isFinite(repeated.difficulty));
assert.equal(bandForDifficulty(repeated.difficulty), "hardest");
const repeatedRaw = solveComSearch([3, 3, 7, 7], 24, 20).solutions.length;
assert.ok(repeatedRaw > repeated.length);

for (const band of BANDS) {
  const puzzle = await find24Puzzle(solve, band);
  assert.equal(puzzle.values.length, 4);
  assert.equal(puzzle.goal, 24);
  assert.ok(matchesBand(puzzle.difficulty, band), `${band} got difficulty ${puzzle.difficulty}`);
  assert.ok(puzzle.solutions.every((solution) => solution.value === 24));
  console.log(`${band}: ${puzzle.values.join(", ")} → difficulty ${puzzle.difficulty.toFixed(2)} (${puzzle.countText} lines)`);
}

const countdown = await findCountdownPuzzle(solve);
assert.equal(countdown.values.length, 6);
assert.ok(countdown.goal >= 100 && countdown.goal <= 999);
assert.equal(countdown.answer.value, countdown.goal);
console.log(
  `countdown: ${countdown.values.join(", ")} → ${countdown.goal} = ${countdown.answer.expression}`
);
