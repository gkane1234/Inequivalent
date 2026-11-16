import { marked, Renderer } from "./vendor/marked.esm.js";

const TARGET_N = 8;

const els = {
  searchTrack: document.getElementById("searchTrack"),
  searchFill: document.getElementById("searchFill"),
  searchCount: document.getElementById("searchCount"),
  searchLabel: document.getElementById("searchLabel"),
  modeSpecific: document.getElementById("modeSpecific"),
  modeFind: document.getElementById("modeFind"),
  nButtons: document.getElementById("nButtons"),
  goal: document.getElementById("goal"),
  maxSolutionsCap: document.getElementById("maxSolutionsCap"),
  valueFields: document.getElementById("valueFields"),
  specificPanel: document.getElementById("specificPanel"),
  findPanel: document.getElementById("findPanel"),
  runBtn: document.getElementById("runBtn"),
  stopBtn: document.getElementById("stopBtn"),
  randomBtn: document.getElementById("randomBtn"),
  results: document.getElementById("results"),
  solveMeta: document.getElementById("solveMeta"),
  minValue: document.getElementById("minValue"),
  maxValue: document.getElementById("maxValue"),
  minSolutions: document.getElementById("minSolutions"),
  maxSolutions: document.getElementById("maxSolutions"),
  development: document.getElementById("development"),
};

let mode = "specific";
let selectedN = 4;
let requestId = 0;
let activeId = 0;
let closedId = 0;
let shown = [];
const pending = new Map();
let worker = null;
let stopping = false;

function stoppedError() {
  const err = new Error("Stopped.");
  err.stopped = true;
  return err;
}

function setBusy(busy) {
  els.runBtn.disabled = busy;
  els.randomBtn.disabled = busy;
  els.stopBtn.disabled = !busy;
}

function renderNButtons() {
  els.nButtons.innerHTML = "";
  for (let n = 2; n <= TARGET_N; n++) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = String(n);
    if (n === selectedN) btn.classList.add("active");
    btn.addEventListener("click", () => {
      selectedN = n;
      renderNButtons();
      renderValueFields();
    });
    els.nButtons.appendChild(btn);
  }
}

function renderValueFields() {
  const previous = [...els.valueFields.querySelectorAll("input")].map((el) => el.value);
  const defaults = ["3", "3", "7", "7", "1", "2", "5", "8"];
  els.valueFields.innerHTML = "";
  for (let i = 0; i < selectedN; i++) {
    const label = document.createElement("label");
    label.className = "field";
    label.textContent = `v${i + 1}`;
    const input = document.createElement("input");
    input.type = "text";
    input.inputMode = "decimal";
    input.autocomplete = "off";
    input.value = previous[i] ?? defaults[i] ?? String(i + 1);
    label.appendChild(input);
    els.valueFields.appendChild(label);
  }
}

function parseMaxSolutionsCap() {
  const n = Number(els.maxSolutionsCap.value);
  if (!Number.isFinite(n) || n < 1) return 50;
  return Math.floor(n);
}

function formatShownValue(value) {
  if (!Number.isFinite(value)) return String(value);
  if (value === Math.round(value)) return String(Math.round(value));
  return String(value);
}

function showChecked(checked, total) {
  const safeTotal = total > 0 ? total : 0;
  const pct = safeTotal > 0 ? Math.min(100, (checked / safeTotal) * 100) : 0;
  els.searchFill.style.width = `${pct}%`;
  els.searchTrack.setAttribute("aria-valuenow", String(checked));
  els.searchTrack.setAttribute("aria-valuemax", String(safeTotal || 0));
  els.searchCount.textContent =
    safeTotal > 0
      ? `${checked.toLocaleString()} / ${safeTotal.toLocaleString()} checked`
      : `${checked.toLocaleString()} checked`;
}

function resetChecked() {
  showChecked(0, 0);
  els.searchLabel.textContent = "Checking…";
}

function solutionLines(solutions) {
  return solutions.map((s, i) => `${i + 1}. ${s.expression} = ${formatShownValue(s.value)}`);
}

function renderShown() {
  els.results.textContent = shown.length ? solutionLines(shown).join("\n") : "";
}

function resetShown() {
  shown = [];
  renderShown();
}

function addShown(solution) {
  shown.push({ expression: solution.expression, value: solution.value });
  renderShown();
  if (closedId && !activeId) {
    els.solveMeta.textContent = `Stopped. Showing ${shown.length} solution(s) found so far.`;
  }
}

function replaceShown(solutions) {
  shown = solutions.map((solution) => ({ expression: solution.expression, value: solution.value }));
  renderShown();
}

function bindWorker(target) {
  target.onmessage = (event) => {
    const msg = event.data;
    if (msg.id === closedId) {
      if (msg.type === "solution") addShown(msg);
      return;
    }
    if (msg.id !== activeId) return;
    if (msg.type === "progress") {
      showChecked(msg.checked, msg.total);
      if (msg.label) els.searchLabel.textContent = msg.label;
      return;
    }
    if (msg.type === "attempt") {
      resetShown();
      if (msg.label) els.searchLabel.textContent = msg.label;
      return;
    }
    if (msg.type === "solution") {
      addShown(msg);
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

  target.onerror = (err) => {
    if (target !== worker || stopping) return;
    const message = err.message || "The solver stopped unexpectedly";
    els.searchLabel.textContent = message;
    for (const [, entry] of pending) entry.reject(new Error(message));
    pending.clear();
    setBusy(false);
  };
}

function freshWorker() {
  worker = new Worker(new URL("./comsearch-worker.js", import.meta.url), { type: "module" });
  bindWorker(worker);
}

function stopSearch() {
  if (!worker || els.stopBtn.disabled) return;
  stopping = true;
  const id = activeId;
  closedId = id;
  activeId = 0;
  worker.terminate();
  freshWorker();
  const entry = pending.get(id);
  if (entry) {
    pending.delete(id);
    entry.reject(stoppedError());
  }
}

function callWorker(action, payload) {
  const id = ++requestId;
  activeId = id;
  closedId = 0;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    worker.postMessage({ id, action, payload });
  });
}

function finishSpecific(result, values, goal, maxSolutions, prefix) {
  replaceShown(result.solutions);
  showChecked(result.checked, result.total);
  els.searchLabel.textContent =
    result.solutions.length >= maxSolutions ? "Stopped at the max solution count." : "Search finished.";
  if (!result.solutions.length) {
    els.results.textContent = `No solutions listed for [${values.join(", ")}] → ${goal}`;
  }
  const lead = prefix ? `${prefix}: ` : "";
  els.solveMeta.textContent =
    result.solutions.length >= maxSolutions
      ? `${lead}Found ${result.solutions.length} solution(s) (stopped at max ${maxSolutions}).`
      : `${lead}Found ${result.solutions.length} solution(s).`;
}

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

els.stopBtn.addEventListener("click", () => stopSearch());

els.runBtn.addEventListener("click", async () => {
  const numValues = selectedN;
  const goal = Number(els.goal.value);
  const maxSolutions = parseMaxSolutionsCap();
  stopping = false;
  setBusy(true);
  resetShown();
  els.solveMeta.textContent = "Working…";
  resetChecked();

  try {
    if (mode === "specific") {
      const values = [...els.valueFields.querySelectorAll("input")].map((el) => Number(el.value));
      const result = await callWorker("solve", { numValues, values, goal, maxSolutions });
      finishSpecific(result, values, goal, maxSolutions);
    } else {
      const boardMax = Number(els.maxSolutions.value);
      const result = await callWorker("findValues", {
        numValues,
        goal,
        valueRange: [Number(els.minValue.value), Number(els.maxValue.value)],
        solutionRange: [Number(els.minSolutions.value), boardMax],
        maxAttempts: 1000,
        displayCap: maxSolutions,
      });
      replaceShown(result.solutions);
      showChecked(result.checked, result.total);
      els.searchLabel.textContent = `Found a board in ${result.attempts} attempt(s).`;
      const lines = [`Values: [${result.values.join(", ")}]  (${result.attempts} attempts)`, ...solutionLines(shown)];
      els.results.textContent = lines.join("\n");
      const count = result.solutionCount;
      els.solveMeta.textContent =
        count > shown.length
          ? `Found ${count} solution(s) for this board; showing ${shown.length}.`
          : `Found ${count} solution(s) for this board.`;
    }
  } catch (err) {
    if (err.stopped) {
      els.searchLabel.textContent = "Stopped.";
      els.solveMeta.textContent = shown.length
        ? `Stopped. Showing ${shown.length} solution(s) found so far.`
        : "Stopped before a solution was found.";
      return;
    }
    els.results.textContent = "";
    els.solveMeta.textContent = err.message;
    els.searchLabel.textContent = err.message;
  } finally {
    setBusy(false);
  }
});

els.randomBtn.addEventListener("click", async () => {
  mode = "specific";
  els.modeSpecific.classList.add("active");
  els.modeFind.classList.remove("active");
  els.specificPanel.classList.remove("hidden");
  els.findPanel.classList.add("hidden");

  stopping = false;
  setBusy(true);
  resetShown();
  els.solveMeta.textContent = "Picking a random example…";
  resetChecked();

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
    finishSpecific(solved, result.values, result.goal, maxSolutions, "Random example");
  } catch (err) {
    if (err.stopped) {
      els.searchLabel.textContent = "Stopped.";
      els.solveMeta.textContent = shown.length
        ? `Stopped. Showing ${shown.length} solution(s) found so far.`
        : "Stopped before a solution was found.";
      return;
    }
    els.results.textContent = "";
    els.solveMeta.textContent = err.message;
    els.searchLabel.textContent = err.message;
  } finally {
    setBusy(false);
  }
});

function renderDevelopment(markdown) {
  const renderer = new Renderer();
  const renderLink = renderer.link;
  renderer.link = function link(token) {
    return renderLink.call(this, token).replace("<a ", '<a target="_blank" rel="noopener noreferrer" ');
  };
  marked.use({ renderer, gfm: true });
  const html = marked
    .parse(markdown)
    .replace(/\$\$([\s\S]+?)\$\$/g, (_, tex) => `<span class="md-math">${tex}</span>`);
  els.development.innerHTML = html;
}

async function loadDevelopment() {
  try {
    const response = await fetch(new URL("./development.md", import.meta.url));
    if (!response.ok) throw new Error(`status ${response.status}`);
    renderDevelopment(await response.text());
  } catch {
    els.development.textContent = "The development notes could not be loaded.";
  }
}

freshWorker();
renderNButtons();
renderValueFields();
loadDevelopment();
