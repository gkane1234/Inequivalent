package com.github.gkane1234;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Counts goal solutions for every nondecreasing set of integers in a range.
 * A set is kept when its solution count falls in the requested window, then
 * ordered by that count or by {@link Difficulty}.
 *
 * <p>The score is the parallel sum of the solved ideas. Lower is easier.
 * A set with no solved line has no score. Dedupe changes which lines are rated.
 *
 * <p>Different sets run at the same time. Each set is one thread: the expressions
 * inside a set are not split, because a pool per set is slower than the walk.
 * {@code live} holds the solvers currently inside a set so a caller can stop them.
 */
public final class RangeSurvey {
    public enum Sort {
        SOLUTION_COUNT,
        DIFFICULTY
    }

    public static final class Row {
        private final int[] values;
        private final int solutions;
        private final double difficulty;

        public Row(int[] values, int solutions, double difficulty) {
            this.values = values.clone();
            this.solutions = solutions;
            this.difficulty = difficulty;
        }

        public int solutions() {
            return solutions;
        }

        public double difficulty() {
            return difficulty;
        }

        /** Blank when the deal has no solved line. */
        public String difficultyText() {
            if (Double.isNaN(difficulty) || Double.isInfinite(difficulty)) {
                return "";
            }
            return String.format(Locale.US, "%.2f", difficulty);
        }

        public String valuesText() {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    text.append(' ');
                }
                text.append(values[i]);
            }
            return text.toString();
        }
    }

    private RangeSurvey() {}

    /** Number of nondecreasing {@code count}-tuples with values in a span of this size. */
    public static long multisetCount(int count, int span) {
        if (count < 0 || span < 0) {
            return 0;
        }
        if (count == 0) {
            return 1;
        }
        if (span == 0) {
            return 0;
        }
        long top = (long) span + count - 1L;
        if (top > Integer.MAX_VALUE) {
            return -1L;
        }
        return combinations((int) top, count);
    }

    /**
     * Fills {@code into} with one row per fully counted nondecreasing set, in that
     * same order. A set whose search was cancelled is left out.
     * Returns how many sets were fully counted.
     */
    public static int search(Solver solver, int min, int max, double goal, boolean dedupeRepeated,
                              BooleanSupplier stop, List<Row> into, IntConsumer onProgress) {
        return search(solver, min, max, goal, dedupeRepeated, stop, null, into, onProgress);
    }

    /**
     * Same search. Solvers that are inside a set are added to {@code live} until that set finishes.
     */
    public static int search(Solver solver, int min, int max, double goal, boolean dedupeRepeated,
                              BooleanSupplier stop, List<Solver> live, List<Row> into, IntConsumer onProgress) {
        if (min > max) {
            throw new IllegalArgumentException("Range min must be ≤ max.");
        }
        int count = solver.getNumValues();
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        final List<int[]> sets = multisets(count, min, max);
        final Row[] slots = new Row[sets.size()];
        final AtomicInteger completed = new AtomicInteger();
        final AtomicReference<RuntimeException> failure = new AtomicReference<RuntimeException>();
        final ThreadLocal<Solver> localSolver = new ThreadLocal<Solver>() {
            @Override
            protected Solver initialValue() {
                return new Solver(count, 1);
            }
        };
        int workers = Math.max(1, Math.min(sets.size(), Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            for (int i = 0; i < sets.size(); i++) {
                final int index = i;
                final int[] values = sets.get(i);
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (failure.get() != null || stopped(stop)) {
                            return;
                        }
                        Solver current = localSolver.get();
                        track(live, current, true);
                        try {
                            if (stopped(stop)) {
                                return;
                            }
                            double[] numbers = toDoubles(values);
                            List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
                            Solver.SearchStats stats = current.findSolutionsStreaming(
                                    numbers, goal, Integer.MAX_VALUE, hits::add, null, dedupeRepeated);
                            if (current.isCancelled()) {
                                return;
                            }
                            int solutions = stats.found > Integer.MAX_VALUE
                                    ? Integer.MAX_VALUE
                                    : (int) stats.found;
                            slots[index] = new Row(values, solutions,
                                    Difficulty.rate(hits, numbers).difficulty);
                            int done = completed.incrementAndGet();
                            if (onProgress != null) {
                                onProgress.accept(done);
                            }
                        } catch (RuntimeException ex) {
                            failure.compareAndSet(null, ex);
                            current.requestStop();
                        } finally {
                            track(live, current, false);
                        }
                    }
                });
            }
            pool.shutdown();
            waitFor(pool, stop, live);
        } finally {
            cancel(live);
            pool.shutdown();
            boolean interrupted = Thread.interrupted();
            while (!pool.isTerminated()) {
                try {
                    pool.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
                cancel(live);
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            for (Row row : slots) {
                if (row != null) {
                    into.add(row);
                }
            }
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return completed.get();
    }

    private static void waitFor(ExecutorService pool, BooleanSupplier stop, List<Solver> live) {
        try {
            while (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                if (stopped(stop) || Thread.currentThread().isInterrupted()) {
                    cancel(live);
                    return;
                }
            }
        } catch (InterruptedException ex) {
            cancel(live);
            Thread.currentThread().interrupt();
        }
    }

    private static boolean stopped(BooleanSupplier stop) {
        return stop != null && stop.getAsBoolean();
    }

    private static void cancel(List<Solver> live) {
        if (live == null) {
            return;
        }
        synchronized (live) {
            for (Solver solver : live) {
                solver.requestStop();
            }
        }
    }

    private static void track(List<Solver> live, Solver solver, boolean add) {
        if (live == null) {
            return;
        }
        synchronized (live) {
            if (add) {
                live.add(solver);
            } else {
                live.remove(solver);
            }
        }
    }

    private static List<int[]> multisets(int count, int min, int max) {
        List<int[]> sets = new ArrayList<int[]>();
        int[] current = new int[count];
        for (int i = 0; i < count; i++) {
            current[i] = min;
        }
        while (true) {
            sets.add(current.clone());
            int index = count - 1;
            while (index >= 0 && current[index] == max) {
                index--;
            }
            if (index < 0) {
                return sets;
            }
            int next = current[index] + 1;
            for (int j = index; j < count; j++) {
                current[j] = next;
            }
        }
    }

    public static int countSolutions(Solver solver, double[] values, double goal, boolean dedupeRepeated) {
        Solver.SearchStats stats = solver.findSolutionsStreaming(
                values, goal, Integer.MAX_VALUE, hit -> { }, null, dedupeRepeated);
        if (stats.found > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) stats.found;
    }

    /**
     * Keeps rows whose solution count is inside {@code [minSolutions, maxSolutions]}.
     * A null maximum keeps every count at or above the minimum.
     * Solution count puts the largest counts first. Difficulty puts the higher scores first.
     */
    public static List<Row> select(List<Row> rows, int minSolutions, Integer maxSolutions, Sort sort) {
        if (minSolutions < 0) {
            throw new IllegalArgumentException("Solution-count min must be ≥ 0.");
        }
        if (maxSolutions != null && maxSolutions < minSolutions) {
            throw new IllegalArgumentException("Solution-count min must be ≤ max.");
        }
        List<Row> kept = new ArrayList<Row>();
        for (Row row : rows) {
            if (row.solutions < minSolutions) {
                continue;
            }
            if (maxSolutions != null && row.solutions > maxSolutions) {
                continue;
            }
            kept.add(row);
        }
        if (sort == Sort.DIFFICULTY) {
            kept.sort((a, b) -> {
                int byDifficulty = compareDifficulty(a, b);
                if (byDifficulty != 0) {
                    return byDifficulty;
                }
                int byCount = Integer.compare(b.solutions, a.solutions);
                if (byCount != 0) {
                    return byCount;
                }
                return a.valuesText().compareTo(b.valuesText());
            });
        } else {
            kept.sort((a, b) -> {
                int byCount = Integer.compare(b.solutions, a.solutions);
                if (byCount != 0) {
                    return byCount;
                }
                return a.valuesText().compareTo(b.valuesText());
            });
        }
        return kept;
    }

    /** Higher scores first. A missing score sorts last. */
    private static int compareDifficulty(Row a, Row b) {
        boolean aMissing = !Double.isFinite(a.difficulty);
        boolean bMissing = !Double.isFinite(b.difficulty);
        if (aMissing && bMissing) {
            return 0;
        }
        if (aMissing) {
            return 1;
        }
        if (bMissing) {
            return -1;
        }
        return Double.compare(b.difficulty, a.difficulty);
    }

    private static double[] toDoubles(int[] values) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i];
        }
        return out;
    }

    static long combinations(int n, int k) {
        if (k < 0 || n < 0 || k > n) {
            return 0L;
        }
        k = Math.min(k, n - k);
        long value = 1L;
        for (int i = 1; i <= k; i++) {
            if (value > Long.MAX_VALUE / (n - i + 1L)) {
                return -1L;
            }
            value = value * (n - i + 1L) / i;
        }
        return value;
    }
}
