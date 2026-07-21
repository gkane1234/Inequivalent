import { generateThroughN } from "./lib/expression-dynamic.js";
import { findAllSolutions, findSolvableValues, makeRandomExample } from "./lib/solver.js";
import { saveList, loadList, clearCache, hasFullCache } from "./lib/cache.js";
import { expectedCount } from "./lib/counter.js";

const TARGET_N = 6;

/** @type {Array<import('./lib/expression-set.js').PlainExpressionList|import('./lib/expression-set.js').ExpressionSet|null>} */
let lists = new Array(TARGET_N + 1).fill(null);

function post(msg) {
  self.postMessage(msg);
}

async function ensureGenerated(force = false) {
  if (!force && (await hasFullCache(TARGET_N))) {
    post({ type: "status", message: "Loading cached expression sets…" });
    for (let n = 1; n <= TARGET_N; n++) {
      lists[n] = await loadList(n);
      post({
        type: "level",
        n,
        count: lists[n].numExpressions,
        expected: expectedCount(n),
        fraction: n / TARGET_N,
        cached: true,
        message: `Loaded n=${n} from cache (${lists[n].numExpressions.toLocaleString()} expressions)`,
      });
    }
    post({ type: "ready", maxN: TARGET_N, fromCache: true });
    return;
  }

  if (force) {
    await clearCache();
    lists = new Array(TARGET_N + 1).fill(null);
  }

  generateThroughN({
    targetN: TARGET_N,
    rounding: 9,
    numTruncators: 20,
    onProgress: (p) => post(p),
    onLevelComplete: (n, list) => {
      lists[n] = list;
    },
  });

  for (let n = 1; n <= TARGET_N; n++) {
    if (lists[n]) await saveList(lists[n]);
  }

  post({ type: "ready", maxN: TARGET_N, fromCache: false });
}

self.onmessage = async (event) => {
  const { id, action, payload } = event.data;
  try {
    if (action === "init") {
      await ensureGenerated(Boolean(payload?.force));
      post({ type: "result", id, ok: true });
      return;
    }

    if (action === "solve") {
      const { numValues, values, goal, maxSolutions = 200 } = payload;
      const list = lists[numValues];
      if (!list) throw new Error(`Expressions for n=${numValues} not ready yet`);
      const solutions = findAllSolutions(list, values, goal, maxSolutions, (p) =>
        post({ ...p, id })
      );
      post({ type: "result", id, ok: true, solutions, values, goal });
      return;
    }

    if (action === "findValues") {
      const { numValues, goal, valueRange, solutionRange, maxAttempts = 1000 } = payload;
      const list = lists[numValues];
      if (!list) throw new Error(`Expressions for n=${numValues} not ready yet`);
      const result = findSolvableValues(
        list,
        numValues,
        goal,
        valueRange,
        solutionRange,
        maxAttempts,
        (p) => post({ ...p, id })
      );
      post({ type: "result", id, ok: true, ...result, goal });
      return;
    }

    if (action === "randomExample") {
      const { numValues } = payload;
      if (!lists[numValues]) throw new Error(`Expressions for n=${numValues} not ready yet`);
      const result = makeRandomExample(numValues);
      post({ type: "result", id, ok: true, ...result });
      return;
    }

    if (action === "clearCache") {
      await clearCache();
      lists = new Array(TARGET_N + 1).fill(null);
      post({ type: "result", id, ok: true });
      return;
    }

    throw new Error(`Unknown action: ${action}`);
  } catch (err) {
    post({ type: "result", id, ok: false, error: err.message || String(err) });
  }
};
