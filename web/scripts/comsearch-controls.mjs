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
console.log(`stopped after ${result.checked} checks, first hit ${found[0].expression}`);
