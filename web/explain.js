import { displayExpression } from "./lib/expression.js";
import { generateThroughN } from "./lib/expression-dynamic.js";

const TOLERANCE = 1e-5;
const ROUNDING = 9;

/** Build the inequivalent set for n=3 once (68 expressions). */
const LIST_N3 = generateThroughN({ targetN: 3 }).at(-1);
const TOTAL = LIST_N3.numExpressions;

function valuesEqual(x, y) {
  return Math.abs(x - y) <= TOLERANCE;
}

function formatValue(v) {
  if (Number.isNaN(v)) return "NaN";
  if (!Number.isFinite(v)) return String(v);
  if (Math.abs(v - Math.round(v)) < 1e-9) return String(Math.round(v));
  const s = v.toPrecision(8);
  return String(Number(s));
}

function formatDelta(delta) {
  if (!Number.isFinite(delta)) return "—";
  if (Math.abs(delta) < TOLERANCE) return "0";
  if (Math.abs(delta - Math.round(delta)) < 1e-9) return String(Math.abs(Math.round(delta)));
  return Math.abs(delta).toPrecision(4);
}

/**
 * Wire the n=3 interactive demo: list expressions ordered by closeness to the goal.
 */
export function initExplainDemo() {
  const inputA = document.getElementById("demoA");
  const inputB = document.getElementById("demoB");
  const inputC = document.getElementById("demoC");
  const inputGoal = document.getElementById("demoGoal");
  const list = document.getElementById("demoList");
  const meta = document.getElementById("demoMeta");

  if (!inputA || !inputB || !inputC || !inputGoal || !list || !meta) return;

  function render() {
    const a = Number(inputA.value);
    const b = Number(inputB.value);
    const c = Number(inputC.value);
    const goal = Number(inputGoal.value);
    const values = [a, b, c];
    const goalOk = Number.isFinite(goal);

    const rows = [];
    const seenText = new Set();
    for (let i = 0; i < TOTAL; i++) {
      const expr = LIST_N3.get(i);
      const value = expr.evaluateWithValues(values, ROUNDING);
      const text = displayExpression(expr, values);
      if (seenText.has(text)) continue;
      seenText.add(text);
      const finite = Number.isFinite(value) && !Number.isNaN(value);
      const delta = finite && goalOk ? value - goal : Number.POSITIVE_INFINITY;
      const hit = finite && goalOk && valuesEqual(value, goal);
      rows.push({
        value,
        text,
        delta,
        absDelta: Math.abs(delta),
        hit,
        finite,
      });
    }

    rows.sort((x, y) => {
      if (x.absDelta !== y.absDelta) return x.absDelta - y.absDelta;
      if (x.finite !== y.finite) return x.finite ? -1 : 1;
      return x.text.localeCompare(y.text);
    });

    const matches = rows.filter((r) => r.hit).length;
    list.innerHTML = "";
    for (const row of rows) {
      const li = document.createElement("li");
      li.className = "demo-item" + (row.hit ? " match" : "");
      const deltaLabel = row.finite && goalOk ? `|Δ| ${formatDelta(row.delta)}` : "|Δ| —";
      li.innerHTML =
        `<span class="demo-expr">${row.text}</span>` +
        `<span class="demo-side">` +
        `<span class="demo-delta">${deltaLabel}</span>` +
        `<span class="demo-val">= ${formatValue(row.value)}</span>` +
        `</span>`;
      list.appendChild(li);
    }

    meta.textContent = `Matches: ${matches} of ${TOTAL} (sorted by distance to goal)`;
  }

  for (const el of [inputA, inputB, inputC, inputGoal]) {
    el.addEventListener("input", render);
  }
  render();
}
