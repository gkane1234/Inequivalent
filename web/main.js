import { initExplainDemo } from "./explain.js";

const TARGET_N = 6;
const readyLevels = new Set();

const els = {
  progressFill: document.getElementById("progressFill"),
  progressPct: document.getElementById("progressPct"),
  statusLine: document.getElementById("statusLine"),
  levelPills: document.getElementById("levelPills"),
  regenBtn: document.getElementById("regenBtn"),
  modeSpecific: document.getElementById("modeSpecific"),
  modeFind: document.getElementById("modeFind"),
  nButtons: document.getElementById("nButtons"),
  goal: document.getElementById("goal"),
  maxSolutionsCap: document.getElementById("maxSolutionsCap"),
  valueFields: document.getElementById("valueFields"),
  specificPanel: document.getElementById("specificPanel"),
  findPanel: document.getElementById("findPanel"),
  runBtn: document.getElementById("runBtn"),
  randomBtn: document.getElementById("randomBtn"),
  results: document.getElementById("results"),
  solveMeta: document.getElementById("solveMeta"),
  minValue: document.getElementById("minValue"),
  maxValue: document.getElementById("maxValue"),
  minSolutions: document.getElementById("minSolutions"),
  maxSolutions: document.getElementById("maxSolutions"),
};

let mode = "specific";
let selectedN = 4;
let requestId = 0;
const pending = new Map();

const worker = new Worker(new URL("./worker.js", import.meta.url), { type: "module" });

function setProgress(fraction, message) {
  const pct = Math.max(0, Math.min(100, Math.round(fraction * 100)));
  els.progressFill.style.width = `${pct}%`;
  els.progressPct.textContent = `${pct}%`;
  if (message) els.statusLine.textContent = message;
}

function renderPills() {
  els.levelPills.innerHTML = "";
  for (let n = 1; n <= TARGET_N; n++) {
    const pill = document.createElement("span");
    pill.className = "pill" + (readyLevels.has(n) ? " ready" : "");
    pill.textContent = readyLevels.has(n) ? `n=${n} ready` : `n=${n}…`;
    els.levelPills.appendChild(pill);
  }
}

function renderNButtons() {
  els.nButtons.innerHTML = "";
  for (let n = 2; n <= TARGET_N; n++) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = String(n);
    btn.dataset.n = String(n);
    if (n === selectedN) btn.classList.add("active");
    btn.disabled = !readyLevels.has(n);
    btn.title = readyLevels.has(n) ? `Use ${n} values` : `n=${n} not ready yet`;
    btn.addEventListener("click", () => {
      if (!readyLevels.has(n)) return;
      selectedN = n;
      renderNButtons();
      renderValueFields();
      updateRunEnabled();
    });
    els.nButtons.appendChild(btn);
  }
}

function updateRunEnabled() {
  const ready = readyLevels.has(selectedN);
  els.runBtn.disabled = !ready;
  if (els.randomBtn) els.randomBtn.disabled = !ready;
}

function renderValueFields() {
  const n = selectedN;
  const previous = [...els.valueFields.querySelectorAll("input")].map((el) => el.value);
  const defaults = ["3", "3", "7", "7", "1", "2"];
  els.valueFields.innerHTML = "";
  for (let i = 0; i < n; i++) {
    const label = document.createElement("label");
    label.className = "field";
    label.textContent = `v${i + 1}`;
    const input = document.createElement("input");
    input.type = "text";
    input.inputMode = "decimal";
    input.autocomplete = "off";
    input.value = previous[i] ?? defaults[i] ?? String(i + 1);
    input.dataset.index = String(i);
    label.appendChild(input);
    els.valueFields.appendChild(label);
  }
  updateRunEnabled();
}

function parseMaxSolutionsCap() {
  const n = Number(els.maxSolutionsCap.value);
  if (!Number.isFinite(n) || n < 1) return 50;
  return Math.floor(n);
}

function callWorker(action, payload) {
  const id = ++requestId;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    worker.postMessage({ id, action, payload });
  });
}

worker.onmessage = (event) => {
  const msg = event.data;

  if (msg.type === "status" || msg.type === "level-start" || msg.type === "group" || msg.type === "growth") {
    if (typeof msg.fraction === "number") setProgress(msg.fraction, msg.message);
    else if (msg.message) els.statusLine.textContent = msg.message;
    return;
  }

  if (msg.type === "level") {
    readyLevels.add(msg.n);
    renderPills();
    renderNButtons();
    updateRunEnabled();
    setProgress(msg.fraction, msg.message);
    return;
  }

  if (msg.type === "ready") {
    for (let n = 1; n <= msg.maxN; n++) readyLevels.add(n);
    renderPills();
    renderNButtons();
    updateRunEnabled();
    setProgress(1, msg.fromCache ? "Loaded from cache — ready." : "Generation complete — ready.");
    return;
  }

  if (msg.type === "solve-progress" || msg.type === "find-attempt") {
    if (msg.message) els.solveMeta.textContent = msg.message;
    else if (msg.type === "solve-progress") {
      els.solveMeta.textContent = `Scanning… ${msg.evaluated.toLocaleString()} / ${msg.total.toLocaleString()} (found ${msg.found})`;
    }
    return;
  }

  if (msg.type === "result") {
    const entry = pending.get(msg.id);
    if (!entry) return;
    pending.delete(msg.id);
    if (msg.ok) entry.resolve(msg);
    else entry.reject(new Error(msg.error || "Worker error"));
  }
};

worker.onerror = (err) => {
  els.statusLine.textContent = `Worker error: ${err.message}`;
  console.error(err);
};

els.modeSpecific.addEventListener("click", () => {
  mode = "specific";
  els.modeSpecific.classList.add("active");
  els.modeFind.classList.remove("active");
  els.specificPanel.classList.remove("hidden");
  els.findPanel.classList.add("hidden");
});

els.modeFind.addEventListener("click", () => {
  mode = "find";
  els.modeFind.classList.add("active");
  els.modeSpecific.classList.remove("active");
  els.findPanel.classList.remove("hidden");
  els.specificPanel.classList.add("hidden");
});

els.runBtn.addEventListener("click", async () => {
  const numValues = selectedN;
  const goal = Number(els.goal.value);
  const maxSolutions = parseMaxSolutionsCap();
  els.runBtn.disabled = true;
  if (els.randomBtn) els.randomBtn.disabled = true;
  els.results.textContent = "";
  els.solveMeta.textContent = "Working…";

  try {
    if (mode === "specific") {
      const inputs = [...els.valueFields.querySelectorAll("input")];
      const values = inputs.map((el) => Number(el.value));
      const result = await callWorker("solve", {
        numValues,
        values,
        goal,
        maxSolutions,
      });
      const count = result.solutions.length;
      const lines = result.solutions.map((s, i) => `${i + 1}. ${s.expression} = ${s.value}`);
      els.results.textContent =
        lines.length > 0
          ? lines.join("\n")
          : `No solutions listed for [${values.join(", ")}] → ${goal}`;
      els.solveMeta.textContent =
        count >= maxSolutions
          ? `Found ${count} solution(s) (stopped at max ${maxSolutions}).`
          : `Found ${count} solution(s).`;
    } else {
      const boardMax = Number(els.maxSolutions.value);
      const result = await callWorker("findValues", {
        numValues,
        goal,
        valueRange: [Number(els.minValue.value), Number(els.maxValue.value)],
        solutionRange: [Number(els.minSolutions.value), boardMax],
        maxAttempts: 1000,
      });
      const count = result.solutions.length;
      // Cap displayed solutions by the shared max solutions field
      const shown = result.solutions.slice(0, maxSolutions);
      const lines = [
        `Values: [${result.values.join(", ")}]  (${result.attempts} attempts)`,
        ...shown.map((s, i) => `${i + 1}. ${s.expression} = ${s.value}`),
      ];
      els.results.textContent = lines.join("\n");
      els.solveMeta.textContent =
        count > maxSolutions
          ? `Found ${count} solution(s) for this board; showing ${maxSolutions}.`
          : `Found ${count} solution(s) for this board.`;
    }
  } catch (err) {
    els.results.textContent = "";
    els.solveMeta.textContent = err.message;
  } finally {
    updateRunEnabled();
  }
});

els.randomBtn.addEventListener("click", async () => {
  // Always fill Specific Values for the selected n
  mode = "specific";
  els.modeSpecific.classList.add("active");
  els.modeFind.classList.remove("active");
  els.specificPanel.classList.remove("hidden");
  els.findPanel.classList.add("hidden");

  els.runBtn.disabled = true;
  els.randomBtn.disabled = true;
  els.results.textContent = "";
  els.solveMeta.textContent = "Picking a random example…";

  try {
    const result = await callWorker("randomExample", { numValues: selectedN });
    renderValueFields();
    const inputs = [...els.valueFields.querySelectorAll("input")];
    result.values.forEach((v, i) => {
      if (inputs[i]) inputs[i].value = String(v);
    });
    els.goal.value = String(result.goal);

    const maxSolutions = parseMaxSolutionsCap();
    els.solveMeta.textContent = "Solving random example…";
    const solved = await callWorker("solve", {
      numValues: selectedN,
      values: result.values,
      goal: result.goal,
      maxSolutions,
    });
    const count = solved.solutions.length;
    const lines = solved.solutions.map((s, i) => `${i + 1}. ${s.expression} = ${s.value}`);
    els.results.textContent =
      lines.length > 0
        ? lines.join("\n")
        : `No solutions listed for [${result.values.join(", ")}] → ${result.goal}`;
    els.solveMeta.textContent =
      count >= maxSolutions
        ? `Random example: found ${count} solution(s) (stopped at max ${maxSolutions}).`
        : `Random example: found ${count} solution(s).`;
  } catch (err) {
    els.results.textContent = "";
    els.solveMeta.textContent = err.message;
  } finally {
    updateRunEnabled();
  }
});

els.regenBtn.addEventListener("click", async () => {
  readyLevels.clear();
  renderPills();
  renderNButtons();
  updateRunEnabled();
  setProgress(0, "Regenerating…");
  try {
    await callWorker("init", { force: true });
  } catch (err) {
    els.statusLine.textContent = err.message;
  }
});

renderPills();
renderNButtons();
renderValueFields();
initExplainDemo();
setProgress(0, "Initializing worker…");
callWorker("init", { force: false }).catch((err) => {
  els.statusLine.textContent = err.message;
});
