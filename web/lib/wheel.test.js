import assert from "node:assert/strict";
import test from "node:test";
import { WHEEL_BANDS, wheelSvg } from "./wheel.js";

const CENTER = 200;
const CIRCLE_R = 170;
const DOT_R = 7.5;

test("each band paints the same dot cluster in all four corners", () => {
  for (const [band, spec] of Object.entries(WHEEL_BANDS)) {
    const svg = wheelSvg([3, 1, 4, 2], band, `card${band}`);
    const dots = [...svg.matchAll(/<circle class="wheel-dot" cx="([^"]+)" cy="([^"]+)" r="([^"]+)" fill="([^"]+)"/g)];
    assert.equal(dots.length, spec.dots * 4, band);
    for (const dot of dots) {
      assert.equal(dot[3], String(DOT_R));
      assert.equal(dot[4], spec.color);
      const cx = Number(dot[1]);
      const cy = Number(dot[2]);
      const nearest = Math.hypot(cx - CENTER, cy - CENTER) - DOT_R;
      assert.ok(nearest > CIRCLE_R, `${band} dot overlaps the gold circle`);
    }
  }
});

test("the four numbers stay upright, top then clockwise", () => {
  const svg = wheelSvg([8, 6, 1, 4], "medium");
  const nums = [...svg.matchAll(/<text class="wheel-num" x="([^"]+)" y="([^"]+)">([^<]+)<\/text>/g)];
  assert.deepEqual(
    nums.map((num) => num[3]),
    ["8", "6", "1", "4"]
  );
  assert.ok(nums.every((num) => !num[0].includes("transform")));
  const [top, right, bottom, left] = nums.map((num) => [Number(num[1]), Number(num[2])]);
  assert.ok(top[1] < CENTER && top[0] === CENTER);
  assert.ok(right[0] > CENTER && right[1] === CENTER);
  assert.ok(bottom[1] > CENTER && bottom[0] === CENTER);
  assert.ok(left[0] < CENTER && left[1] === CENTER);
  assert.match(svg, /Medium 24 card\. Numbers 8, 6, 1, 4\./);
});

test("a wheel rejects a bad band, a short hand, and a bad id", () => {
  assert.throws(() => wheelSvg([1, 2, 3, 4], "brutal"), /Unknown difficulty/);
  assert.throws(() => wheelSvg([1, 2, 3], "easy"), /four numbers/);
  assert.throws(() => wheelSvg([1, 2, 3, 4], "easy", "bad id"), /Invalid wheel id/);
});
