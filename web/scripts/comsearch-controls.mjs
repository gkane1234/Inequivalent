import assert from "node:assert/strict";
import { expectedCount } from "../lib/counter.js";
import { solveComSearch } from "../lib/comsearch.js";

assert.equal(expectedCount(8), 1150212810);

const found = [];
const result = solveComSearch(
  [3, 3, 7, 7],
  24,
  50,
  () => {},
  (solution) => found.push(solution),
  () => found.length >= 1
);

assert.ok(found.length >= 1);
assert.equal(result.solutions.length, found.length);
assert.ok(result.checked < expectedCount(4));
assert.match(found[0].expression, /3/);

const uncapped = solveComSearch([3, 3, 7, 7], 24, null);
assert.equal(uncapped.checked, expectedCount(4));
assert.ok(uncapped.solutions.length >= 4);

const one = solveComSearch([3, 3, 7, 7], 24, 1);
assert.equal(one.solutions.length, 1);
assert.ok(one.checked < expectedCount(4));

const off = solveComSearch([3, 3, 7, 7], 24, null);
const on = solveComSearch([3, 3, 7, 7], 24, null, null, null, null, { dedupeRepeated: true });
assert.ok(off.solutions.some((s) => s.expression === "(3+3/7)*7"));
assert.ok(off.solutions.some((s) => s.expression === "(3/7+3)*7"));
assert.equal(off.checked, on.checked);
assert.equal(on.solutions.filter((s) => s.expression === "(3+3/7)*7").length, 1);
assert.equal(on.solutions.some((s) => s.expression === "(3/7+3)*7"), false);
assert.ok(on.solutions.length < off.solutions.length);
assert.equal(new Set(on.solutions.map((s) => s.expression)).size, on.solutions.length);

const fiftyOff = solveComSearch([3, 3, 7, 7], 50, null);
const fiftyOn = solveComSearch([3, 3, 7, 7], 50, null, null, null, null, { dedupeRepeated: true });
assert.ok(fiftyOff.solutions.filter((s) => s.expression === "3/3+7*7").length > 1);
assert.equal(fiftyOn.solutions.filter((s) => s.expression === "3/3+7*7").length, 1);

const distinctOff = solveComSearch([1, 2, 3, 4], 10, null);
const distinctOn = solveComSearch([1, 2, 3, 4], 10, null, null, null, null, { dedupeRepeated: true });
assert.equal(distinctOff.solutions.length, distinctOn.solutions.length);
assert.equal(distinctOff.checked, distinctOn.checked);

console.log(
  `stopped after ${result.checked} checks, first hit ${found[0].expression}; dedupe ${off.solutions.length} -> ${on.solutions.length}`
);
