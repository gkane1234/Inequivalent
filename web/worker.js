import { generateThroughN } from "./lib/expression-dynamic.js";
import { findAllSolutions, findSolvableValues, makeRandomExample } from "./lib/solver.js";
import {
  saveList,
  loadRecord,
  unpackListAsync,
  clearCache,
  listCachedNs,
} from "./lib/cache.js";
import { expectedCount } from "./lib/counter.js";

const TARGET_N = 6;

/** @type {Array<import('./lib/expression-set.js').PlainExpressionList|import('./lib/expression-set.js').ExpressionSet|null>} */
let lists = new Array(TARGET_N + 1).fill(null);

const inbox = [];
let initActive = false;
let chain = Promise.resolve();

function post(msg) {
  self.postMessage(msg);
}

function isSearch(action) {
  return action === "solve" || action === "findValues" || action === "randomExample";
}

function canRunNow(msg) {
  const n = msg.payload?.numValues;
  return isSearch(msg.action) && Number.isInteger(n) && lists[n] != null;
}

function enqueue(task) {
  chain = chain
    .then(task)
    .catch((err) => {
      post({ type: "status", message: err?.message || String(err) });
    });
}

async function drainInbox() {
  if (inbox.length === 0) return;
  const batch = inbox.splice(0, inbox.length);
  const later = [];
  for (const msg of batch) {
    if (canRunNow(msg)) await handleSearch(msg);
    else later.push(msg);
  }
  if (later.length) inbox.unshift(...later);
}

/**
 * Generation and cache loads run on this same worker. A solve posted while n=6
 * is still building used to sit unread until that job finished. Yield so the
 * browser can deliver the message, then run it if that size is already ready.
 */
function breathe() {
  return new Promise((resolve) => {
    setTimeout(() => {
      drainInbox().then(resolve, (err) => {
        post({ type: "status", message: err?.message || String(err) });
        resolve();
      });
    }, 0);
  });
}

self.onmessage = (event) => {
  const msg = event.data;
  if (initActive) {
    inbox.push(msg);
    return;
  }
  if (msg.action === "init") initActive = true;
  enqueue(() => dispatch(msg));
};

async function dispatch(msg) {
  if (msg.action === "init") initActive = true;
  const { id, action, payload } = msg;
  try {
    if (action === "init") {
      await ensureGenerated(Boolean(payload?.force));
      post({ type: "result", id, ok: true });
      return;
    }
    if (action === "clearCache") {
      await clearCache();
      lists = new Array(TARGET_N + 1).fill(null);
      post({ type: "result", id, ok: true });
      return;
    }
    await handleSearch(msg);
  } catch (err) {
    post({ type: "result", id, ok: false, error: err.message || String(err) });
  } finally {
    if (action === "init") {
      initActive = false;
      const rest = inbox.splice(0, inbox.length);
      for (const queued of rest) enqueue(() => dispatch(queued));
    }
  }
}

async function handleSearch(msg) {
  const { id, action, payload } = msg;
  try {
    if (action === "solve") {
      const { numValues, values, goal, maxSolutions = 200 } = payload;
      const list = lists[numValues];
      if (!list) throw new Error(`Expressions for n=${numValues} not ready yet`);
      const solutions = findAllSolutions(list, values, goal, maxSolutions, (p) => post({ ...p, id }));
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

    throw new Error(`Unknown action: ${action}`);
  } catch (err) {
    post({ type: "result", id, ok: false, error: err.message || String(err) });
  }
}

async function afterLevel(n, list) {
  await breathe();
  try {
    await saveList(list);
  } catch (err) {
    post({
      type: "status",
      message: `Could not cache n=${n} (${err.message || err}). Solving still works.`,
    });
  }
  await breathe();
}

async function ensureGenerated(force = false) {
  if (force) {
    await clearCache();
    lists = new Array(TARGET_N + 1).fill(null);
    await breathe();
  }

  const cached = new Set(await listCachedNs());
  let full = !force;
  for (let n = 1; n <= TARGET_N && full; n++) {
    if (!cached.has(n)) full = false;
  }

  if (full) {
    post({ type: "status", message: "Loading cached expression sets…" });
    for (let n = 1; n <= TARGET_N; n++) {
      const record = await loadRecord(n);
      if (!record) throw new Error(`Cache missing n=${n}`);
      lists[n] = await unpackListAsync(record, breathe);
      post({
        type: "level",
        n,
        count: lists[n].numExpressions,
        expected: expectedCount(n),
        fraction: n / TARGET_N,
        cached: true,
        message: `Loaded n=${n} from cache (${lists[n].numExpressions.toLocaleString()} expressions)`,
      });
      await breathe();
    }
    post({ type: "ready", maxN: TARGET_N, fromCache: true });
    return;
  }

  if (!force) {
    await clearCache();
    lists = new Array(TARGET_N + 1).fill(null);
  }

  await generateThroughN({
    targetN: TARGET_N,
    rounding: 9,
    numTruncators: 20,
    onProgress: (p) => post(p),
    onPause: breathe,
    onLevelComplete: (n, list) => {
      lists[n] = list;
      return afterLevel(n, list);
    },
  });

  post({ type: "ready", maxN: TARGET_N, fromCache: false });
}
