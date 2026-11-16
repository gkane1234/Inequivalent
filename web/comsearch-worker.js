import { solveComSearch } from "./lib/comsearch.js";
import { makeRandomExample } from "./lib/solver.js";

function post(msg) {
  self.postMessage(msg);
}

self.onmessage = (event) => {
  const { id, action, payload } = event.data;
  try {
    if (action === "solve") {
      const { numValues, values, goal, maxSolutions = 50, dedupeRepeated = false } = payload;
      if (!Array.isArray(values) || values.length !== numValues) {
        throw new Error(`expected ${numValues} values`);
      }
      const result = solveComSearch(
        values,
        goal,
        maxSolutions,
        (p) => {
          post({
            type: "progress",
            id,
            checked: p.checked,
            total: p.total,
            found: p.found,
            label: p.done ? "" : `Found ${p.found}`,
          });
        },
        (solution) => {
          post({ type: "solution", id, expression: solution.expression, value: solution.value });
        },
        undefined,
        { dedupeRepeated }
      );
      post({ type: "result", id, ok: true, ...result, values, goal });
      return;
    }

    if (action === "findValues") {
      const {
        numValues,
        goal,
        valueRange,
        solutionRange,
        maxAttempts = 1000,
        displayCap = 50,
        dedupeRepeated = false,
      } = payload;
      const [vmin, vmax] = valueRange;
      const [smin, smax] = solutionRange;
      if (!(vmax > vmin)) throw new Error("Max value must be greater than min value");
      if (!(smax >= smin)) throw new Error("Max solutions must be at least the minimum");

      for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        const values = Array.from({ length: numValues }, () => Math.floor(Math.random() * (vmax - vmin)) + vmin);
        post({
          type: "attempt",
          id,
          label: `Attempt ${attempt}/${maxAttempts}: [${values.join(", ")}]`,
        });
        const result = solveComSearch(
          values,
          goal,
          smax + 1,
          (p) => {
            post({
              type: "progress",
              id,
              checked: p.checked,
              total: p.total,
              found: p.found,
              label: `Attempt ${attempt}/${maxAttempts}: [${values.join(", ")}]`,
            });
          },
          (solution) => {
            post({ type: "solution", id, expression: solution.expression, value: solution.value });
          },
          undefined,
          { dedupeRepeated }
        );
        if (result.solutions.length >= smin && result.solutions.length <= smax) {
          post({
            type: "result",
            id,
            ok: true,
            values,
            goal,
            attempts: attempt,
            solutions: Number.isFinite(displayCap) ? result.solutions.slice(0, displayCap) : result.solutions,
            solutionCount: result.solutions.length,
            checked: result.checked,
            total: result.total,
          });
          return;
        }
      }
      throw new Error(`No board found within ${maxAttempts} attempts`);
    }

    if (action === "randomExample") {
      const result = makeRandomExample(payload.numValues);
      post({ type: "result", id, ok: true, ...result });
      return;
    }

    throw new Error(`Unknown action: ${action}`);
  } catch (err) {
    post({ type: "result", id, ok: false, error: err.message || String(err) });
  }
};
