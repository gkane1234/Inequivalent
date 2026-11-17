/**
 * 24 game and Countdown numbers deals.
 * The solver is injected, so the same deals run on ComSearch or the expression list.
 */

export const GAME24_GOAL = 24;
export const COUNTDOWN_LARGE = Object.freeze([25, 50, 75, 100]);

/** How many deduped solutions to fetch before the band is known. */
export function capForBand(band) {
  if (band === "easy") return 9;
  if (band === "medium") return 8;
  if (band === "hard") return 3;
  throw new Error(`Unknown difficulty ${band}`);
}

/**
 * Easy is more than 7.5 distinct solutions, medium is from 2.5 through 7.5,
 * and hard is fewer than 2.5. Zero solutions is not a puzzle.
 */
export function bandForCount(count) {
  if (!(count > 0)) return null;
  if (count > 7.5) return "easy";
  if (count < 2.5) return "hard";
  return "medium";
}

export function matchesBand(count, band) {
  return bandForCount(count) === band;
}

export function countLabel(count, band) {
  const cap = capForBand(band);
  if (band === "easy" && count >= cap) return `at least ${count}`;
  return String(count);
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
 * @param {(values: number[], goal: number, maxSolutions: number) => Promise<{expression: string, value: number}[]>} solve
 */
export async function find24Puzzle(solve, band, options = {}) {
  const cap = capForBand(band);
  const maxAttempts = options.maxAttempts ?? 400;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const values = deal24(options.random);
    options.onAttempt?.({ attempt, maxAttempts, values, goal: GAME24_GOAL });
    const solutions = await solve(values, GAME24_GOAL, cap);
    if (matchesBand(solutions.length, band)) {
      const full = await solve(values, GAME24_GOAL, Number.POSITIVE_INFINITY);
      return {
        values,
        goal: GAME24_GOAL,
        solutions,
        band,
        countText: countLabel(solutions.length, band),
        attempts: attempt,
        difficulty: full.difficulty,
        ideaCount: full.ideaCount,
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
