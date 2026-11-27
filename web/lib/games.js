/**
 * 24 game and Countdown numbers deals.
 * The solver is injected, so the same deals run on ComSearch or the expression list.
 *
 * 24-game bands use arithmetic difficulty cutoffs from the Java 1–13 sweep
 * (DifficultyStrata). Recompute there when the difficulty equation changes, then
 * paste the numbers into DIFFICULTY_BOUNDS.
 */

export const GAME24_GOAL = 24;
export const COUNTDOWN_LARGE = Object.freeze([25, 50, 75, 100]);

/**
 * Numerical difficulty cutoffs from
 * `mvn -f countingoperations/pom.xml exec:java -Dexec.mainClass=com.github.gkane1234.DifficultyStrata`
 * with percentiles 60 / 85 / 95 over nondecreasing 4-tuples in [1, 13] → 24.
 * easy ≤ easyMax, medium ≤ mediumMax, hard ≤ hardMax, hardest above hardMax.
 */
export const DIFFICULTY_BOUNDS = Object.freeze({
  easyMax: 27.413335,
  mediumMax: 48.099017,
  hardMax: 61.534586,
});

export const BANDS = Object.freeze(["easy", "medium", "hard", "hardest"]);

/**
 * @param {number} difficulty
 * @returns {"easy"|"medium"|"hard"|"hardest"|null}
 */
export function bandForDifficulty(difficulty, bounds = DIFFICULTY_BOUNDS) {
  if (!Number.isFinite(difficulty) || !(difficulty > 0)) return null;
  if (difficulty <= bounds.easyMax) return "easy";
  if (difficulty <= bounds.mediumMax) return "medium";
  if (difficulty <= bounds.hardMax) return "hard";
  return "hardest";
}

export function matchesBand(difficulty, band, bounds = DIFFICULTY_BOUNDS) {
  return bandForDifficulty(difficulty, bounds) === band;
}

export function deal24(random = Math.random) {
  const values = [];
  for (let i = 0; i < 4; i++) values.push(1 + Math.floor(random() * 9));
  return values;
}

/**
 * Six cards from the Countdown board.
 * Large cards are 25, 50, 75, 100, each once.
 * Small cards are two copies of each integer from 1 to 10.
 * @param {number} [largeCount] 0 through 4; omitted means a random count
 */
export function dealCountdown(largeCount, random = Math.random) {
  const count = largeCount == null ? Math.floor(random() * 5) : largeCount;
  if (!Number.isInteger(count) || count < 0 || count > 4) {
    throw new Error("largeCount must be an integer from 0 to 4");
  }
  const large = shuffle(COUNTDOWN_LARGE, random).slice(0, count);
  const smallPool = [];
  for (let n = 1; n <= 10; n++) smallPool.push(n, n);
  const small = shuffle(smallPool, random).slice(0, 6 - count);
  return { values: shuffle([...large, ...small], random), largeCount: count };
}

export function randomThreeDigit(random = Math.random) {
  return 100 + Math.floor(random() * 900);
}

/**
 * @param {(values: number[], goal: number, maxSolutions: number) => Promise<{expression: string, value: number, difficulty?: number, ideaCount?: number}[]>} solve
 */
export async function find24Puzzle(solve, band, options = {}) {
  if (!BANDS.includes(band)) throw new Error(`Unknown difficulty ${band}`);
  const bounds = options.bounds ?? DIFFICULTY_BOUNDS;
  const maxAttempts = options.maxAttempts ?? 800;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const values = deal24(options.random);
    options.onAttempt?.({ attempt, maxAttempts, values, goal: GAME24_GOAL });
    const solutions = await solve(values, GAME24_GOAL, Number.POSITIVE_INFINITY);
    const difficulty = solutions.difficulty;
    if (matchesBand(difficulty, band, bounds)) {
      return {
        values,
        goal: GAME24_GOAL,
        solutions,
        band,
        countText: String(solutions.length),
        attempts: attempt,
        difficulty,
        ideaCount: solutions.ideaCount,
      };
    }
  }
  throw new Error(`No ${band} 24-game puzzle found in ${maxAttempts} deals`);
}

/**
 * @param {(values: number[], goal: number, maxSolutions: number) => Promise<{expression: string, value: number}[]>} solve
 */
export async function findCountdownPuzzle(solve, options = {}) {
  const maxAttempts = options.maxAttempts ?? 12;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const dealt = dealCountdown(options.largeCount, options.random);
    const goal = randomThreeDigit(options.random);
    options.onAttempt?.({ attempt, maxAttempts, values: dealt.values, goal });
    const solutions = await solve(dealt.values, goal, 1);
    if (solutions.length > 0) {
      const full = await solve(dealt.values, goal, Number.POSITIVE_INFINITY);
      return {
        values: dealt.values,
        largeCount: dealt.largeCount,
        goal,
        answer: full[0] ?? solutions[0],
        attempts: attempt,
        difficulty: full.difficulty,
        ideaCount: full.ideaCount,
      };
    }
  }
  throw new Error(`No countdown puzzle found in ${maxAttempts} deals`);
}

function shuffle(items, random) {
  const copy = items.slice();
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    const tmp = copy[i];
    copy[i] = copy[j];
    copy[j] = tmp;
  }
  return copy;
}
