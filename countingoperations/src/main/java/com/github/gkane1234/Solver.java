package com.github.gkane1234;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Streams ComSearch expressions and reports those that hit the goal.
 * Supports cooperative cancellation so a UI can stop without wiping results.
 *
 * <p>Uses eval-during-generate with parallel root partition jobs: numeric values
 * are computed while combining, and {@link Expression} objects are built only for hits.
 * Hits are reported as soon as they are found — you do not wait for the full
 * enumeration to finish.
 */
public class Solver {
    public static final int ROUNDING = 9;
    private static final double TOLERANCE = 1e-5;

    /**
     * Above this, root-partition parallelism is forced off. Large n has huge partition
     * counts; even a bounded pool adds memory pressure, and serial streaming is safer.
     */
    public static final int PARALLEL_MAX_N = 10;

    private final int numValues;
    private final int parallelism;
    private final ComSearchGenerator generator;
    private final Object callbackLock = new Object();

    public Solver(int numValues) {
        this(numValues, defaultParallelism(numValues));
    }

    public Solver(int numValues, int parallelism) {
        if (numValues < 1) {
            throw new IllegalArgumentException("numValues must be >= 1");
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1");
        }
        this.numValues = numValues;
        // Ignore requested parallelism for large n — see PARALLEL_MAX_N.
        this.parallelism = numValues > PARALLEL_MAX_N ? 1 : parallelism;
        this.generator = new ComSearchGenerator(numValues);
    }

    private static int defaultParallelism(int numValues) {
        if (numValues > PARALLEL_MAX_N) {
            return 1;
        }
        // Root partition jobs are coarse and uneven; past ~4 workers, GC/contention
        // usually outweighs extra parallelism on current heap-heavy combine path.
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    }

    public int getNumValues() {
        return numValues;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void requestStop() {
        generator.cancel();
    }

    /** Running totals from a streaming search. */
    public static final class SearchStats {
        public final long found;
        public final long checked;

        public SearchStats(long found, long checked) {
            this.found = found;
            this.checked = checked;
        }

        /** Percent of checked expressions that matched the goal. */
        public double hitPercent() {
            if (checked == 0) {
                return 0.0;
            }
            return 100.0 * found / checked;
        }
    }

    /**
     * Enumerate ComSearch expressions in order, invoke {@code onFound} for each match
     * as soon as it is discovered. Stops early if {@code maxSolutions} is reached or
     * {@link #requestStop()} is called — remaining expressions are simply not visited.
     */
    public SearchStats findSolutionsStreaming(double[] values, double goal, int maxSolutions,
                                              Consumer<EvaluatedExpression> onFound,
                                              Consumer<SearchStats> onProgress) {
        if (values.length != numValues) {
            throw new IllegalArgumentException(
                    "expected " + numValues + " values, got " + values.length);
        }
        AtomicLong found = new AtomicLong();
        AtomicLong checked = new AtomicLong();
        final double roundFactor = Math.pow(10, ROUNDING);
        // Progress less often for large n so UI/callback overhead stays small.
        final long progressEvery = numValues >= 9 ? 100_000L : 2_000L;

        generator.search(values, (rawValue, materialize) -> {
            if (found.get() >= maxSolutions || generator.isCancelled()) {
                generator.cancel();
                return;
            }
            long c = checked.incrementAndGet();
            double value = round(rawValue, roundFactor);
            if (equal(value, goal)) {
                // Materialize synchronously while generator scratch state is valid.
                Expression expression = materialize.get();
                EvaluatedExpression hit = new EvaluatedExpression(expression, values, value);
                synchronized (callbackLock) {
                    if (found.get() >= maxSolutions) {
                        generator.cancel();
                        return;
                    }
                    long f = found.incrementAndGet();
                    onFound.accept(hit);
                    if (onProgress != null) {
                        onProgress.accept(new SearchStats(f, checked.get()));
                    }
                    if (f >= maxSolutions) {
                        generator.cancel();
                    }
                }
            } else if (onProgress != null && c % progressEvery == 0) {
                synchronized (callbackLock) {
                    onProgress.accept(new SearchStats(found.get(), checked.get()));
                }
            }
        }, parallelism);

        SearchStats finalStats = new SearchStats(found.get(), checked.get());
        if (onProgress != null) {
            synchronized (callbackLock) {
                onProgress.accept(finalStats);
            }
        }
        return finalStats;
    }

    public SearchStats findSolutionsStreaming(double[] values, double goal, int maxSolutions,
                                              Consumer<EvaluatedExpression> onFound) {
        return findSolutionsStreaming(values, goal, maxSolutions, onFound, null);
    }

    public ExpressionList getExpressionList() {
        return generator.toExpressionList();
    }

    public static boolean equal(double a, double b) {
        return Math.abs(a - b) <= TOLERANCE;
    }

    private static double round(double value, double factor) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return Math.round(value * factor) / factor;
    }
}
