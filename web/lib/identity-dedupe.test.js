import assert from "node:assert/strict";
import test from "node:test";
import { identitySignature } from "./identity-dedupe.js";

const ADD = 0;
const SUB = 1;
const MUL = 2;
const DIV = 3;

function rpn(vars, ops, order) {
  return { valueOrder: vars, operations: ops, order };
}

test("parking a 1 is one idea, adding it is another", () => {
  const values = [8, 9, 1, 7];
  const parked = identitySignature(
    rpn([0, 1, 2, 3], [ADD, DIV, ADD], [true, true, false, true, false, true, false]),
    values
  );
  const multiplied = identitySignature(
    rpn([2, 0, 1, 3], [ADD, MUL, ADD], [true, true, true, false, false, true, false]),
    values
  );
  const dividedEarly = identitySignature(
    rpn([3, 0, 1, 2], [ADD, ADD, DIV], [true, true, false, true, false, true, false]),
    values
  );
  const used = identitySignature(
    rpn([2, 1, 3, 0], [ADD, SUB, MUL], [true, true, false, true, false, true, false]),
    values
  );
  assert.equal(parked, "(7+8+9) || 1");
  assert.equal(multiplied, parked);
  assert.equal(dividedEarly, parked);
  assert.notEqual(used, parked);
});

test("a spared 0 and a spared 1 are one multiset", () => {
  const values = [5, 1, 0];
  const timesThenAdd = identitySignature(
    rpn([0, 1, 2], [MUL, ADD], [true, true, false, true, false]),
    values
  );
  const addThenTimes = identitySignature(
    rpn([0, 2, 1], [ADD, MUL], [true, true, false, true, false]),
    values
  );
  const oneThenFive = identitySignature(
    rpn([1, 2, 0], [ADD, MUL], [true, true, false, true, false]),
    values
  );
  assert.equal(timesThenAdd, "5 || 0 || 1");
  assert.equal(addThenTimes, timesThenAdd);
  assert.equal(oneThenFive, timesThenAdd);
});

test("two 1s are not the same payload as 1-1", () => {
  const values = [8, 3, 1, 1];
  const both = identitySignature(
    rpn([0, 1, 2, 3], [MUL, MUL, MUL], [true, true, false, true, false, true, false]),
    values
  );
  const split = identitySignature(
    rpn([0, 2, 1, 3], [MUL, MUL, MUL], [true, true, false, true, true, false, false]),
    values
  );
  const cancelled = identitySignature(
    rpn([0, 1, 2, 3], [MUL, SUB, ADD], [true, true, false, true, true, false, false]),
    values
  );
  assert.equal(both, "(3*8) || 1 || 1");
  assert.equal(split, both);
  assert.equal(cancelled, "(3*8) || (1-1)");
});

test("3/3 stays a different payload from 3-3", () => {
  const values = [8, 3, 3, 3];
  const quotient = identitySignature(
    rpn([0, 1, 2, 3], [MUL, DIV, MUL], [true, true, false, true, true, false, false]),
    values
  );
  const moved = identitySignature(
    rpn([0, 2, 3, 1], [DIV, MUL, MUL], [true, true, true, false, false, true, false]),
    values
  );
  const difference = identitySignature(
    rpn([0, 1, 2, 3], [MUL, SUB, ADD], [true, true, false, true, true, false, false]),
    values
  );
  const across = identitySignature(
    rpn([1, 2, 0, 3], [MUL, MUL, DIV], [true, true, false, true, false, true, false]),
    values
  );
  assert.equal(quotient, "(3*8) || (3/3)");
  assert.equal(moved, quotient);
  assert.notEqual(difference, quotient);
  assert.notEqual(across, quotient);
});
