import { formatDifficulty } from "./lib/difficulty.js";
import { find24Puzzle, findCountdownPuzzle } from "./lib/games.js";

const BANDS = [
  ["easy", "Easy"],
  ["medium", "Medium"],
  ["hard", "Hard"],
];

/**
 * Draw the 24 game and the Countdown numbers game under the solver.
 * `engine.solve` is the only solver call, so either page can host this.
 *
 * @param {{
 *   engineName: string,
 *   isReady: (n: number) => boolean,
 *   subscribe: (cb: () => void) => void,
 *   solve: (values: number[], goal: number, maxSolutions: number) => Promise<{expression: string, value: number}[]>,
 * }} engine
 */
export function mountGames(engine) {
  const host = document.getElementById("games");
  if (!host) return;
  host.innerHTML = `
    <h2 id="games-heading">Games</h2>
    <p class="hint" id="gamesEngine"></p>

    <div class="game-block">
      <h3>24 game</h3>
      <p>
        Four numbers from 1 to 9. The goal is 24, and each number is used once.
        The easy, medium, and hard buttons follow the number of solutions after deduping
        repeated numbers: easy has more than 7.5, medium has between 2.5 and 7.5, and hard
        has fewer than 2.5. The numbers also show an arithmetic difficulty. Lower is easier.
        Lines that only move a 0 or a 1 count as one idea, and repeated numbers make it lower.
      </p>
      <div class="modes" id="game24Bands" role="group" aria-label="24 game difficulty"></div>
      <div class="actions">
        <button type="button" class="primary" id="game24Deal">New puzzle</button>
        <button type="button" class="ghost" id="game24Reveal" disabled>Reveal answer</button>
      </div>
      <div class="meta" id="game24Status" aria-live="polite"></div>
      <div class="number-chips" id="game24Numbers"></div>
      <div class="results game-answer hidden" id="game24Answer"></div>
    </div>

    <div class="game-block">
      <h3>Countdown numbers</h3>
      <p>
        The board has 24 cards. The top row is 25, 50, 75 and 100. The other three rows
        hold six, seven and seven cards: two each of the integers from 1 to 10.
        A contestant picks six cards, by a pattern, by how many to take from each row,
        or by how many large numbers and small numbers. This deals six legal cards and a
        three-digit target those six can make. Each number is used once. The status includes
        the same arithmetic difficulty as the 24 game.
      </p>
      <div class="actions">
        <button type="button" class="primary" id="countdownDeal">New puzzle</button>
        <button type="button" class="ghost" id="countdownReveal" disabled>Reveal answer</button>
      </div>
      <div class="meta" id="countdownStatus" aria-live="polite"></div>
      <div class="number-chips" id="countdownNumbers"></div>
      <div class="results game-answer hidden" id="countdownAnswer"></div>
    </div>
  `;

  const els = {
    engine: host.querySelector("#gamesEngine"),
    bands: host.querySelector("#game24Bands"),
    deal24: host.querySelector("#game24Deal"),
    reveal24: host.querySelector("#game24Reveal"),
    status24: host.querySelector("#game24Status"),
    numbers24: host.querySelector("#game24Numbers"),
    answer24: host.querySelector("#game24Answer"),
    dealCount: host.querySelector("#countdownDeal"),
    revealCount: host.querySelector("#countdownReveal"),
    statusCount: host.querySelector("#countdownStatus"),
    numbersCount: host.querySelector("#countdownNumbers"),
    answerCount: host.querySelector("#countdownAnswer"),
  };

  els.engine.textContent = `Both games use this page's ${engine.engineName} solver. Deduping is on, so the 24-game bands count distinct lines.`;

  let band = "medium";
  let busy = false;
  let puzzle24 = null;
  let puzzleCount = null;

  for (const [id, label] of BANDS) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = label;
    button.dataset.band = id;
    if (id === band) button.classList.add("active");
    button.addEventListener("click", () => {
      if (busy) return;
      band = id;
      for (const other of els.bands.querySelectorAll("button")) {
        other.classList.toggle("active", other === button);
      }
    });
    els.bands.appendChild(button);
  }

  function paintNumbers(container, values, goalLabel) {
    container.replaceChildren();
    for (const value of values) {
      const chip = document.createElement("span");
      chip.textContent = String(value);
      container.appendChild(chip);
    }
    if (goalLabel) {
      const goal = document.createElement("span");
      goal.className = "goal-chip";
      goal.textContent = goalLabel;
      container.appendChild(goal);
    }
  }

  function refresh() {
    const ready24 = engine.isReady(4);
    const readyCount = engine.isReady(6);
    els.deal24.disabled = busy || !ready24;
    els.dealCount.disabled = busy || !readyCount;
    els.reveal24.disabled = busy || !puzzle24;
    els.revealCount.disabled = busy || !puzzleCount;
    for (const button of els.bands.querySelectorAll("button")) button.disabled = busy;
    if (!puzzle24 && !busy) {
      els.status24.textContent = ready24
        ? "Choose a difficulty, then deal a puzzle."
        : "Waiting until 4-number expressions are ready.";
    }
    if (!puzzleCount && !busy) {
      els.statusCount.textContent = readyCount
        ? "Deal six cards and a three-digit target."
        : "Waiting until 6-number expressions are ready.";
    }
  }

  els.deal24.addEventListener("click", async () => {
    busy = true;
    puzzle24 = null;
    els.answer24.classList.add("hidden");
    els.answer24.textContent = "";
    els.numbers24.replaceChildren();
    refresh();
    try {
      puzzle24 = await find24Puzzle(engine.solve, band, {
        onAttempt({ attempt, values }) {
          els.status24.textContent = `Looking for a ${band} board… try ${attempt}: ${values.join(", ")}`;
        },
      });
      paintNumbers(els.numbers24, puzzle24.values, "24");
      els.status24.textContent = withDifficulty(
        `${labelOf(puzzle24.band)} board. ${solutionPhrase(puzzle24.countText)}.`,
        puzzle24
      );
    } catch (err) {
      els.status24.textContent = err.message;
    } finally {
      busy = false;
      refresh();
    }
  });

  els.reveal24.addEventListener("click", () => {
    if (!puzzle24) return;
    const lines = puzzle24.solutions.map((solution, i) => `${i + 1}. ${solution.expression} = ${formatValue(solution.value)}`);
    els.answer24.textContent = lines.join("\n");
    els.answer24.classList.remove("hidden");
  });

  els.dealCount.addEventListener("click", async () => {
    busy = true;
    puzzleCount = null;
    els.answerCount.classList.add("hidden");
    els.answerCount.textContent = "";
    els.numbersCount.replaceChildren();
    refresh();
    try {
      puzzleCount = await findCountdownPuzzle(engine.solve, {
        onAttempt({ attempt, values, goal }) {
          els.statusCount.textContent = `Looking for a solvable target… try ${attempt}: ${values.join(", ")} → ${goal}`;
        },
      });
      paintNumbers(els.numbersCount, puzzleCount.values, String(puzzleCount.goal));
      const small = 6 - puzzleCount.largeCount;
      els.statusCount.textContent = withDifficulty(
        `${puzzleCount.largeCount} large and ${small} small. Target ${puzzleCount.goal}.`,
        puzzleCount
      );
    } catch (err) {
      els.statusCount.textContent = err.message;
    } finally {
      busy = false;
      refresh();
    }
  });

  els.revealCount.addEventListener("click", () => {
    if (!puzzleCount) return;
    const { answer } = puzzleCount;
    els.answerCount.textContent = `${answer.expression} = ${formatValue(answer.value)}`;
    els.answerCount.classList.remove("hidden");
  });

  engine.subscribe(refresh);
  refresh();
}

function withDifficulty(sentence, puzzle) {
  const extra = formatDifficulty(puzzle.difficulty, puzzle.ideaCount);
  return extra ? `${sentence} ${extra}` : sentence;
}

function labelOf(band) {
  return BANDS.find(([id]) => id === band)?.[1] ?? band;
}

function solutionPhrase(countText) {
  const atLeast = countText.startsWith("at least ");
  const count = Number(atLeast ? countText.slice("at least ".length) : countText);
  const word = count === 1 && !atLeast ? "solution" : "solutions";
  return `${countText} distinct ${word}`;
}

function formatValue(value) {
  if (Number.isInteger(value)) return String(value);
  return String(value);
}
