package com.github.gkane1234;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Streams ComSearch expressions and reports those that hit the goal.
 * Supports cooperative cancellation so a UI can stop without wiping results.
 */
public class Solver {
    public static final int ROUNDING = 9;
    private static final double TOLERANCE = 1e-5;

    private final int numValues;
    private final ComSearchGenerator generator;

    public Solver(int numValues) {
        if (numValues < 1) {
            throw new IllegalArgumentException("numValues must be >= 1");
        }
        this.numValues = numValues;
        this.generator = new ComSearchGenerator(numValues);
    }

    public int getNumValues() {
        return numValues;
    }

    public void requestStop() {
        generator.cancel();
    }

    /** Running totals from a streaming search. */
    public static final class SearchStats {
        public final int found;
        public final int checked;

        public SearchStats(int found, int checked) {
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
     * Enumerate all ComSearch expressions, invoke {@code onFound} for each match.
     * {@code onProgress} is called periodically and on each hit with live counts.
     *
     * <p>Uses a fast RPN evaluator with reused buffers and caches subexpression
     * values by identity (memoized generator shares Expression instances).
     */
    public SearchStats findSolutionsStreaming(double[] values, double goal, int maxSolutions,
                                              Consumer<EvaluatedExpression> onFound,
                                              Consumer<SearchStats> onProgress) {
        if (values.length != numValues) {
            throw new IllegalArgumentException(
                    "expected " + numValues + " values, got " + values.length);
        }
        int[] found = {0};
        int[] checked = {0};
        double[] remapScratch = new double[numValues];
        double[] stackScratch = new double[Math.max(8, numValues * 4)];
        Map<Expression, Double> valueCache = new IdentityHashMap<>();

        generator.generate(expression -> {
            if (found[0] >= maxSolutions) {
                generator.cancel();
                return;
            }
            checked[0]++;
            double value = evalCached(expression, values, valueCache, remapScratch, stackScratch);
            if (equal(value, goal)) {
                onFound.accept(new EvaluatedExpression(expression, values, value));
                found[0]++;
                if (onProgress != null) {
                    onProgress.accept(new SearchStats(found[0], checked[0]));
                }
                if (found[0] >= maxSolutions) {
                    generator.cancel();
                }
            } else if (onProgress != null && checked[0] % 2000 == 0) {
                onProgress.accept(new SearchStats(found[0], checked[0]));
            }
        });
        SearchStats finalStats = new SearchStats(found[0], checked[0]);
        if (onProgress != null) {
            onProgress.accept(finalStats);
        }
        return finalStats;
    }

    private static double evalCached(Expression expression, double[] values,
                                     Map<Expression, Double> cache,
                                     double[] remapScratch, double[] stackScratch) {
        Double cached = cache.get(expression);
        if (cached != null) {
            return cached;
        }
        double value = expression.evaluateWithValues(values, ROUNDING, remapScratch, stackScratch);
        // Cache only modest-size DAGs; unbounded IdentityHashMap can grow with every root expr.
        if (expression.valueOrder.length <= 4) {
            cache.put(expression, value);
        }
        return value;
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
}
